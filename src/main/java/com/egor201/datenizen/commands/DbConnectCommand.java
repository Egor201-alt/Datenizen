package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.utilities.text.StringHolder;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.Settings;
import com.egor201.datenizen.database.DatabaseManager;
import com.egor201.datenizen.events.DbConnectedEvent;
import com.egor201.datenizen.events.DbErrorEvent;

import java.util.HashMap;
import java.util.Map;

public class DbConnectCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_connect
    // @Syntax db_connect [id:<id>] [driver:<driver>] [url:<url>] (user:<user>) (pass:<pass>) (read_only:<true/false>) (init_sql:<sql>) (params:<map>) (pool_max:<n>) (pool_min:<n>) (connection_timeout:<ms>)
    // @Required 3
    // @Maximum 11
    // @Short Connects to a database using HikariCP connection pooling.
    // @Group Datenizen
    //
    // @Description
    // Connects to a database asynchronously and stores the connection pool under the specified ID.
    // Fires 'db connected' on success, or 'db error' on failure.
    //
    // The 'driver' argument accepts short aliases (recommended) or full class names:
    //   sqlite      -> org.sqlite.JDBC
    //   mysql       -> com.mysql.cj.jdbc.Driver
    //   mariadb     -> org.mariadb.jdbc.Driver
    //   postgresql  -> org.postgresql.Driver
    //   postgres    -> org.postgresql.Driver
    //
    // 'params' passes extra JDBC/driver properties (for example useSSL=false).
    // 'pool_max', 'pool_min' and 'connection_timeout' override the config defaults.
    //
    // @Usage
    // - db_connect id:local driver:sqlite url:plugins/Datenizen/local.db
    // - db_connect id:main driver:mysql url:localhost:3306/mydb user:root pass:1234 pool_max:20
    // -->
    public DbConnectCommand() {
        super("db_connect",
              "db_connect [id:<id>] [driver:<driver>] [url:<url>] (user:<user>) (pass:<pass>) (read_only:<true/false>) (init_sql:<sql>) (params:<map>) (pool_max:<n>) (pool_min:<n>) (connection_timeout:<ms>)",
              3, 11,
              "Must specify id, driver, and url!",
              ArgSpec.element("id"),
              ArgSpec.element("driver"),
              ArgSpec.element("url"),
              ArgSpec.optionalElement("user"),
              ArgSpec.optionalElement("pass"),
              ArgSpec.optionalElement("read_only"),
              ArgSpec.optionalElement("init_sql"),
              ArgSpec.optionalMap("params"),
              ArgSpec.optionalElement("pool_max"),
              ArgSpec.optionalElement("pool_min"),
              ArgSpec.optionalElement("connection_timeout"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id = se.getElement("id").asString();
        String driverInput = se.getElement("driver").asString();
        String urlInput = se.getElement("url").asString();
        ElementTag userTag = se.getElement("user");
        ElementTag passTag = se.getElement("pass");
        ElementTag readOnlyTag = se.getElement("read_only");
        ElementTag initSqlTag = se.getElement("init_sql");
        MapTag paramsTag = se.getObjectTag("params");
        ElementTag poolMaxTag = se.getElement("pool_max");
        ElementTag poolMinTag = se.getElement("pool_min");
        ElementTag connectionTimeoutTag = se.getElement("connection_timeout");

        String user = userTag != null ? userTag.asString() : "";
        String pass = passTag != null ? passTag.asString() : "";
        boolean readOnly = readOnlyTag != null && readOnlyTag.asBoolean();
        String initSql = initSqlTag != null ? initSqlTag.asString() : null;

        int poolMax = Settings.poolMax;
        int poolMin = Settings.poolMin;
        long connectionTimeoutMs = Settings.connectionTimeoutMs;
        try {
            if (poolMaxTag != null) poolMax = Integer.parseInt(poolMaxTag.asString());
            if (poolMinTag != null) poolMin = Integer.parseInt(poolMinTag.asString());
            if (connectionTimeoutTag != null) connectionTimeoutMs = Long.parseLong(connectionTimeoutTag.asString());
        } catch (NumberFormatException e) {
            DbCommandSupport.error(id, "pool_max, pool_min and connection_timeout must be numbers", "db_connect");
            return;
        }
        if (poolMax < 1 || poolMin < 0 || poolMin > poolMax || connectionTimeoutMs < 250) {
            DbCommandSupport.error(id, "Invalid pool configuration", "db_connect");
            return;
        }

        Map<String, String> jdbcParams = new HashMap<>();
        if (paramsTag != null) {
            for (StringHolder holder : paramsTag.map.keySet()) {
                ObjectTag value = paramsTag.map.get(holder);
                jdbcParams.put(holder.str, value != null ? value.toString() : "");
            }
        }

        String driver = DatabaseManager.resolveDriver(driverInput);
        if (driver == null) {
            DbErrorEvent.instance.fireFor(id,
                "Unknown driver alias '" + driverInput + "'. Use: sqlite, mysql, mariadb, postgresql",
                "db_connect");
            return;
        }

        String url = DatabaseManager.resolveUrl(driver, urlInput);
        final int finalPoolMax = poolMax;
        final int finalPoolMin = poolMin;
        final long finalConnectionTimeout = connectionTimeoutMs;

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            if (Datenizen.getInstance().getDatabaseManager().getDataSource(id) != null) {
                DbCommandSupport.error(id, "A connection with id '" + id + "' already exists", "db_connect");
                return;
            }
            boolean success = Datenizen.getInstance().getDatabaseManager().connect(
                id, driver, url, user, pass, readOnly, initSql, jdbcParams,
                finalPoolMax, finalPoolMin, finalConnectionTimeout
            );
            org.bukkit.Bukkit.getScheduler().runTask(Datenizen.getInstance(), () -> {
                if (success) {
                    DbConnectedEvent.instance.fireFor(id);
                } else {
                    DbErrorEvent.instance.fireFor(id, "Failed to connect to '" + id + "'. Check server log for details.", "db_connect");
                }
            });
        });
    }
}
