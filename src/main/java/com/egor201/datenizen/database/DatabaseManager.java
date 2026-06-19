package com.egor201.datenizen.database;

import com.denizenscript.denizencore.objects.core.ListTag;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbConnectionLeakedEvent;
import com.egor201.datenizen.events.DbDisconnectedEvent;
import com.egor201.datenizen.events.DbNotifyEvent;
import com.egor201.datenizen.events.DbTransactionExpiredEvent;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseManager {

    private record ConnectionParams(String driver, String url, String user, String password) {}

    private final Map<String, HikariDataSource>  connectionPools       = new ConcurrentHashMap<>();
    private final Map<String, Connection>        activeTransactions    = new ConcurrentHashMap<>();
    private final Map<String, Long>              transactionStartTimes = new ConcurrentHashMap<>();
    private final Map<String, String>            transactionDbIds      = new ConcurrentHashMap<>();
    private final Map<String, ConnectionParams>  savedParams           = new ConcurrentHashMap<>();
    private final Map<String, String>            namedQueries          = new ConcurrentHashMap<>();
    // Object[] = { ListTag result, Long expiresAtMs }
    private final Map<String, Object[]>          queryCache            = new ConcurrentHashMap<>();
    // key: "id:channel"
    private final Map<String, Connection>        listenConnections     = new ConcurrentHashMap<>();
    private final Map<String, Integer>           listenTaskIds         = new ConcurrentHashMap<>();

    private static final Map<String, String> DRIVER_ALIASES = Map.of(
        "sqlite",     "org.sqlite.JDBC",
        "mysql",      "com.mysql.cj.jdbc.Driver",
        "mariadb",    "org.mariadb.jdbc.Driver",
        "postgresql", "org.postgresql.Driver",
        "postgres",   "org.postgresql.Driver"
    );

    private static final Set<String> ALLOWED_DRIVERS = new HashSet<>(DRIVER_ALIASES.values());

    public DatabaseManager() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(Datenizen.getInstance(), () -> {
            long now = System.currentTimeMillis();
            List<String> expired = new ArrayList<>();

            for (Map.Entry<String, Long> entry : transactionStartTimes.entrySet()) {
                if (now - entry.getValue() > 300_000) {
                    expired.add(entry.getKey());
                }
            }

            for (String txId : expired) {
                String dbId = transactionDbIds.get(txId);
                long elapsedSeconds = (now - transactionStartTimes.getOrDefault(txId, now)) / 1000;
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () -> {
                    DbConnectionLeakedEvent.instance.fireFor(txId, elapsedSeconds);
                    DbTransactionExpiredEvent.instance.fireFor(txId, dbId);
                });
                try { rollbackTransaction(txId); } catch (SQLException ignored) {}
            }
        }, 1200L, 1200L);
    }

    public static String resolveDriver(String input) {
        if (input == null) return null;
        return DRIVER_ALIASES.get(input.toLowerCase());
    }

    public static String resolveUrl(String driver, String rawUrl) {
        if (rawUrl == null) return null;
        if (rawUrl.startsWith("jdbc:")) return rawUrl;
        return switch (driver) {
            case "org.sqlite.JDBC"          -> "jdbc:sqlite:" + rawUrl;
            case "com.mysql.cj.jdbc.Driver" -> "jdbc:mysql://" + rawUrl;
            case "org.mariadb.jdbc.Driver"  -> "jdbc:mariadb://" + rawUrl;
            case "org.postgresql.Driver"    -> "jdbc:postgresql://" + rawUrl;
            default -> rawUrl;
        };
    }

    public boolean connect(String id, String driver, String url, String user, String password) {
        if (connectionPools.containsKey(id)) return false;

        if (!ALLOWED_DRIVERS.contains(driver)) {
            Bukkit.getLogger().severe("[Datenizen] Blocked connection attempt with unapproved driver: " + driver);
            return false;
        }

        HikariConfig config = new HikariConfig();
        config.setDriverClassName(driver);
        config.setJdbcUrl(url);
        if (user != null && !user.isEmpty())         config.setUsername(user);
        if (password != null && !password.isEmpty()) config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(600000);
        config.setPoolName("Datenizen-" + id);

        try {
            HikariDataSource ds = new HikariDataSource(config);
            connectionPools.put(id, ds);
            savedParams.put(id, new ConnectionParams(driver, url, user != null ? user : "", password != null ? password : ""));
            return true;
        } catch (Exception e) {
            Bukkit.getLogger().severe("[Datenizen] Failed to connect to database '" + id + "': " + e.getMessage());
            return false;
        }
    }

    public boolean reconnect(String id) {
        ConnectionParams params = savedParams.get(id);
        if (params == null) return false;

        HikariDataSource old = connectionPools.remove(id);
        if (old != null && !old.isClosed()) old.close();

        HikariConfig config = new HikariConfig();
        config.setDriverClassName(params.driver());
        config.setJdbcUrl(params.url());
        if (!params.user().isEmpty())     config.setUsername(params.user());
        if (!params.password().isEmpty()) config.setPassword(params.password());
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(600000);
        config.setPoolName("Datenizen-" + id);

        try {
            HikariDataSource ds = new HikariDataSource(config);
            connectionPools.put(id, ds);
            return true;
        } catch (Exception e) {
            Bukkit.getLogger().severe("[Datenizen] Failed to reconnect to database '" + id + "': " + e.getMessage());
            return false;
        }
    }

    public void registerQuery(String id, String name, String sql) {
        namedQueries.put(id + ":" + name, sql);
    }

    public String getNamedQuery(String id, String name) {
        return namedQueries.get(id + ":" + name);
    }

    public ListTag getCached(String key, long nowMs) {
        Object[] entry = queryCache.get(key);
        if (entry == null) return null;
        if (nowMs > (long) entry[1]) { queryCache.remove(key); return null; }
        return (ListTag) entry[0];
    }

    public void putCache(String key, ListTag result, long expiresAtMs) {
        queryCache.put(key, new Object[]{result, expiresAtMs});
    }

    public void invalidateCache(String id) {
        queryCache.keySet().removeIf(k -> k.startsWith(id + ":"));
    }

    public boolean disconnect(String id) {
        List<String> txToRollback = new ArrayList<>();
        for (Map.Entry<String, String> entry : transactionDbIds.entrySet()) {
            if (entry.getValue().equals(id)) txToRollback.add(entry.getKey());
        }
        for (String tx : txToRollback) {
            try { rollbackTransaction(tx); } catch (SQLException ignored) {}
        }

        invalidateCache(id);
        namedQueries.keySet().removeIf(k -> k.startsWith(id + ":"));
        savedParams.remove(id);
        HikariDataSource ds = connectionPools.remove(id);
        if (ds != null) {
            if (!ds.isClosed()) ds.close();
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                DbDisconnectedEvent.instance.fireFor(id)
            );
            return true;
        }
        return false;
    }

    public Connection getConnection(String id) throws SQLException {
        HikariDataSource ds = connectionPools.get(id);
        if (ds != null) return ds.getConnection();
        throw new SQLException("Database ID '" + id + "' not found");
    }

    public HikariDataSource getDataSource(String id) {
        return connectionPools.get(id);
    }

    public String getDatabaseType(String id) {
        HikariDataSource ds = connectionPools.get(id);
        if (ds == null) return "unknown";
        String url = ds.getJdbcUrl();
        if (url.contains("sqlite"))     return "sqlite";
        if (url.contains("postgresql")) return "postgresql";
        if (url.contains("mariadb"))    return "mariadb";
        return "mysql";
    }

    public Set<String> getActiveIds() {
        return Collections.unmodifiableSet(connectionPools.keySet());
    }

    public boolean startTransaction(String txId, String dbId) throws SQLException {
        if (activeTransactions.containsKey(txId)) return false;
        Connection conn = getConnection(dbId);
        try {
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            try { conn.close(); } catch (SQLException ignored) {}
            throw e;
        }
        activeTransactions.put(txId, conn);
        transactionStartTimes.put(txId, System.currentTimeMillis());
        transactionDbIds.put(txId, dbId);
        return true;
    }

    public boolean commitTransaction(String txId) throws SQLException {
        Connection conn = activeTransactions.remove(txId);
        transactionStartTimes.remove(txId);
        transactionDbIds.remove(txId);
        if (conn != null) {
            try {
                conn.commit();
                conn.setAutoCommit(true);
            } finally {
                conn.close();
            }
            return true;
        }
        return false;
    }

    public boolean rollbackTransaction(String txId) throws SQLException {
        Connection conn = activeTransactions.remove(txId);
        transactionStartTimes.remove(txId);
        transactionDbIds.remove(txId);
        if (conn != null) {
            try {
                conn.rollback();
                conn.setAutoCommit(true);
            } finally {
                conn.close();
            }
            return true;
        }
        return false;
    }

    public Connection getTransactionConnection(String txId) {
        return activeTransactions.get(txId);
    }

    public String getTxDbId(String txId) {
        return transactionDbIds.get(txId);
    }

    public void cleanPool(String id) {
        HikariDataSource ds = connectionPools.get(id);
        if (ds != null && ds.getHikariPoolMXBean() != null) {
            ds.getHikariPoolMXBean().softEvictConnections();
        }
    }

    public void analyze(String id) throws SQLException {
        String type = getDatabaseType(id);
        String sql = type.equals("sqlite") ? "VACUUM" : "ANALYZE";
        try (Connection conn = getConnection(id); Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    public void startListen(String id, String channel) throws Exception {
        String key = id + ":" + channel;
        if (listenConnections.containsKey(key)) return;

        Connection conn = getConnection(id);
        try (Statement st = conn.createStatement()) {
            st.execute("LISTEN " + channel);
        }
        listenConnections.put(key, conn);

        PGConnection pgConn = conn.unwrap(PGConnection.class);

        int taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(Datenizen.getInstance(), () -> {
            try (Statement st = conn.createStatement()) {
                st.execute("SELECT 1");
            } catch (Exception ignored) {}
            try {
                PGNotification[] notifications = pgConn.getNotifications(0);
                if (notifications == null) return;
                for (PGNotification n : notifications) {
                    String payload = n.getParameter();
                    Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                        DbNotifyEvent.instance.fireFor(id, n.getName(), payload != null ? payload : "")
                    );
                }
            } catch (Exception ignored) {}
        }, 20L, 20L).getTaskId();

        listenTaskIds.put(key, taskId);
    }

    public void stopListen(String id, String channel) throws Exception {
        String key = id + ":" + channel;
        Integer taskId = listenTaskIds.remove(key);
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
        Connection conn = listenConnections.remove(key);
        if (conn != null) {
            try (Statement st = conn.createStatement()) { st.execute("UNLISTEN " + channel); } catch (Exception ignored) {}
            try { conn.close(); } catch (Exception ignored) {}
        }
    }

    public void stopAllListeners() {
        for (Map.Entry<String, Integer> e : listenTaskIds.entrySet()) {
            Bukkit.getScheduler().cancelTask(e.getValue());
        }
        listenTaskIds.clear();
        for (Connection conn : listenConnections.values()) {
            try { conn.close(); } catch (Exception ignored) {}
        }
        listenConnections.clear();
    }

    public void closeAllConnections() {
        stopAllListeners();

        for (Connection conn : activeTransactions.values()) {
            try { conn.rollback(); }         catch (SQLException ignored) {}
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
            try { conn.close(); }            catch (SQLException ignored) {}
        }
        activeTransactions.clear();
        transactionStartTimes.clear();
        transactionDbIds.clear();
        savedParams.clear();
        namedQueries.clear();
        queryCache.clear();

        for (HikariDataSource ds : connectionPools.values()) {
            if (ds != null && !ds.isClosed()) ds.close();
        }
        connectionPools.clear();
    }
}