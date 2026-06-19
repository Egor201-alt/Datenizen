package com.egor201.datenizen.tags;

import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.tags.TagManager;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbErrorEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.regex.Pattern;

public class DatenizenTags {

    private static final Pattern SAFE_NAME = Pattern.compile("^[a-zA-Z0-9_]+$");

    private static ListTag getResultSetAsList(String id, String sql, ListTag args) {
        try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (args != null) {
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                ListTag result = new ListTag();
                while (rs.next()) {
                    MapTag row = new MapTag();
                    for (int i = 1; i <= cols; i++) {
                        Object val = rs.getObject(i);
                        row.putObject(meta.getColumnName(i), new ElementTag(val == null ? "null" : val.toString()));
                    }
                    result.addObject(row);
                }
                return result;
            }
        } catch (Exception e) {
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                DbErrorEvent.instance.fireFor(id, e.getMessage(), e instanceof java.sql.SQLException ? ((java.sql.SQLException)e).getSQLState() : null, sql));
            return null;
        }
    }

    public static void register() {

        // <--[tag]
        // @Attribute <db_query[<id>].sql[<query>].args[<list>]>
        // @Returns ListTag
        // @Group Datenizen
        // @Description Returns query results as a ListTag of MapTags.
        // -->
        TagManager.registerTagHandler(ListTag.class, "db_query", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (attribute.startsWith("sql") && attribute.hasParam()) {
                String sql = attribute.getParam();
                attribute.fulfill(1);
                ListTag args = attribute.startsWith("args") && attribute.hasParam() ? attribute.contextAsType(1, ListTag.class) : null;
                if (args != null) attribute.fulfill(1);
                return getResultSetAsList(id, sql, args);
            }
            return null;
        });

        // <--[tag]
        // @Attribute <db_query_first[<id>].sql[<query>].args[<list>]>
        // @Returns MapTag
        // @Group Datenizen
        // @Description Returns the first row of the query result as a MapTag, or null if no rows.
        // Much cleaner than db_query when you only need one row.
        //
        // @Usage
        // - define player <db_query_first[main].sql[SELECT * FROM players WHERE uuid=?].args[<player.uuid>]>
        // - narrate <[player].get[coins]>
        // -->
        TagManager.registerTagHandler(MapTag.class, "db_query_first", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (attribute.startsWith("sql") && attribute.hasParam()) {
                String sql = attribute.getParam();
                attribute.fulfill(1);
                ListTag args = attribute.startsWith("args") && attribute.hasParam() ? attribute.contextAsType(1, ListTag.class) : null;
                if (args != null) attribute.fulfill(1);
                try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (args != null) {
                        for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            ResultSetMetaData meta = rs.getMetaData();
                            int cols = meta.getColumnCount();
                            MapTag row = new MapTag();
                            for (int i = 1; i <= cols; i++) {
                                Object val = rs.getObject(i);
                                row.putObject(meta.getColumnName(i), new ElementTag(val == null ? "null" : val.toString()));
                            }
                            return row;
                        }
                    }
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                        DbErrorEvent.instance.fireFor(id, e.getMessage(), e instanceof java.sql.SQLException ? ((java.sql.SQLException)e).getSQLState() : null, sql));
                }
            }
            return null;
        });

        // <--[tag]
        // @Attribute <db_convert_map[<id>].sql[<query>].args[<list>]>
        // @Returns MapTag
        // @Group Datenizen
        // @Description Returns a MapTag where the first column is the key and the second is the value.
        // -->
        TagManager.registerTagHandler(MapTag.class, "db_convert_map", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (attribute.startsWith("sql") && attribute.hasParam()) {
                String sql = attribute.getParam();
                attribute.fulfill(1);
                ListTag args = attribute.startsWith("args") && attribute.hasParam() ? attribute.contextAsType(1, ListTag.class) : null;
                if (args != null) attribute.fulfill(1);
                try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (args != null) {
                        for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        MapTag map = new MapTag();
                        while (rs.next()) {
                            Object key = rs.getObject(1);
                            Object val = rs.getObject(2);
                            if (key != null) map.putObject(key.toString(), new ElementTag(val == null ? "null" : val.toString()));
                        }
                        return map;
                    }
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                        DbErrorEvent.instance.fireFor(id, e.getMessage(), e instanceof java.sql.SQLException ? ((java.sql.SQLException)e).getSQLState() : null, sql));
                }
            }
            return null;
        });

        // <--[tag]
        // @Attribute <db_query_as_json[<id>].sql[<query>].args[<list>]>
        // @Returns ElementTag
        // @Group Datenizen
        // @Description Returns the query result as a JSON array string.
        // -->
        TagManager.registerTagHandler(ElementTag.class, "db_query_as_json", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (attribute.startsWith("sql") && attribute.hasParam()) {
                String sql = attribute.getParam();
                attribute.fulfill(1);
                ListTag args = attribute.startsWith("args") && attribute.hasParam() ? attribute.contextAsType(1, ListTag.class) : null;
                if (args != null) attribute.fulfill(1);
                try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (args != null) {
                        for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int cols = meta.getColumnCount();
                        JsonArray arr = new JsonArray();
                        while (rs.next()) {
                            JsonObject obj = new JsonObject();
                            for (int i = 1; i <= cols; i++) {
                                String colName = meta.getColumnName(i);
                                String val = rs.getString(i);
                                if (val == null) {
                                    obj.add(colName, JsonNull.INSTANCE);
                                } else {
                                    obj.addProperty(colName, val);
                                }
                            }
                            arr.add(obj);
                        }
                        return new ElementTag(arr.toString());
                    }
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                        DbErrorEvent.instance.fireFor(id, e.getMessage(), e instanceof java.sql.SQLException ? ((java.sql.SQLException)e).getSQLState() : null, sql));
                }
            }
            return null;
        });

        // <--[tag]
        // @Attribute <db_last_id[<tx_id>]>
        // @Returns ElementTag
        // @Group Datenizen
        // @Description Returns the last inserted row ID. MUST pass a transaction ID to guarantee the same connection.
        // -->
        TagManager.registerTagHandler(ElementTag.class, "db_last_id", attribute -> {
            if (!attribute.hasParam()) return null;
            String txId = attribute.getParam();
            attribute.fulfill(1);
            Connection conn = Datenizen.getInstance().getDatabaseManager().getTransactionConnection(txId);
            if (conn == null) return null;
            String dbId = Datenizen.getInstance().getDatabaseManager().getTxDbId(txId);
            String type = Datenizen.getInstance().getDatabaseManager().getDatabaseType(dbId);
            String sql = type.equals("sqlite") ? "SELECT last_insert_rowid()"
                       : type.equals("postgresql") ? "SELECT LASTVAL()"
                       : "SELECT LAST_INSERT_ID()";
            try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new ElementTag(rs.getString(1));
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(dbId, e.getMessage(), e instanceof java.sql.SQLException ? ((java.sql.SQLException)e).getSQLState() : null, sql));
            }
            return null;
        });

        // <--[tag]
        // @Attribute <db_exists_table[<id>].table[<name>]>
        // @Returns ElementTag(Boolean)
        // @Group Datenizen
        // @Description Returns true if the specified table exists in the database.
        // -->
        TagManager.registerTagHandler(ElementTag.class, "db_exists_table", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (attribute.startsWith("table") && attribute.hasParam()) {
                String table = attribute.getParam();
                attribute.fulfill(1);
                try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id)) {
                    DatabaseMetaData meta = conn.getMetaData();
                    try (ResultSet rs = meta.getTables(null, null, table, new String[]{"TABLE"})) {
                        return new ElementTag(rs.next());
                    }
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                        DbErrorEvent.instance.fireFor(id, e.getMessage(), null, "CHECK TABLE EXISTS"));
                }
            }
            return new ElementTag(false);
        });

        // <--[tag]
        // @Attribute <db_value[<id>].sql[<query>].args[<list>]>
        // @Returns ElementTag
        // @Group Datenizen
        // @Description Returns the first column of the first row of the query result.
        // -->
        TagManager.registerTagHandler(ElementTag.class, "db_value", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (attribute.startsWith("sql") && attribute.hasParam()) {
                String sql = attribute.getParam();
                attribute.fulfill(1);
                ListTag args = attribute.startsWith("args") && attribute.hasParam() ? attribute.contextAsType(1, ListTag.class) : null;
                if (args != null) attribute.fulfill(1);
                try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (args != null) {
                        for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            Object val = rs.getObject(1);
                            return new ElementTag(val == null ? "null" : val.toString());
                        }
                    }
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                        DbErrorEvent.instance.fireFor(id, e.getMessage(), e instanceof java.sql.SQLException ? ((java.sql.SQLException)e).getSQLState() : null, sql));
                }
            }
            return null;
        });

        // <--[tag]
        // @Attribute <db_exists[<id>].sql[<query>].args[<list>]>
        // @Returns ElementTag(Boolean)
        // @Group Datenizen
        // @Description Returns true if the query returns at least one row.
        // -->
        TagManager.registerTagHandler(ElementTag.class, "db_exists", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (attribute.startsWith("sql") && attribute.hasParam()) {
                String sql = attribute.getParam();
                attribute.fulfill(1);
                ListTag args = attribute.startsWith("args") && attribute.hasParam() ? attribute.contextAsType(1, ListTag.class) : null;
                if (args != null) attribute.fulfill(1);
                try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (args != null) {
                        for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        return new ElementTag(rs.next());
                    }
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                        DbErrorEvent.instance.fireFor(id, e.getMessage(), e instanceof java.sql.SQLException ? ((java.sql.SQLException)e).getSQLState() : null, sql));
                }
            }
            return new ElementTag(false);
        });

        // <--[tag]
        // @Attribute <db_columns[<id>].table[<name>]>
        // @Returns ListTag
        // @Group Datenizen
        // @Description Returns column names for a given table.
        // -->
        TagManager.registerTagHandler(ListTag.class, "db_columns", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (attribute.startsWith("table") && attribute.hasParam()) {
                String table = attribute.getParam();
                attribute.fulfill(1);
                try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id)) {
                    DatabaseMetaData meta = conn.getMetaData();
                    try (ResultSet rs = meta.getColumns(null, null, table, null)) {
                        ListTag list = new ListTag();
                        while (rs.next()) list.addObject(new ElementTag(rs.getString("COLUMN_NAME")));
                        return list;
                    }
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                        DbErrorEvent.instance.fireFor(id, e.getMessage(), null, "GET COLUMNS"));
                }
            }
            return null;
        });

        // <--[tag]
        // @Attribute <db_tables[<id>]>
        // @Returns ListTag
        // @Group Datenizen
        // @Description Returns all table names in the database.
        //
        // @Usage
        // - narrate "Tables: <db_tables[main]>"
        // -->
        TagManager.registerTagHandler(ListTag.class, "db_tables", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id)) {
                DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                    ListTag list = new ListTag();
                    while (rs.next()) list.addObject(new ElementTag(rs.getString("TABLE_NAME")));
                    return list;
                }
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, e.getMessage(), null, "GET TABLES"));
            }
            return null;
        });

        // <--[tag]
        // @Attribute <db_count[<id>].table[<name>]>
        // @Returns ElementTag
        // @Group Datenizen
        // @Description Returns total row count for a table.
        // Table name must be alphanumeric/underscores only.
        // For filtered counts use db_value with a parameterized SELECT COUNT(*) query.
        // -->
        TagManager.registerTagHandler(ElementTag.class, "db_count", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (attribute.startsWith("table") && attribute.hasParam()) {
                String table = attribute.getParam();
                attribute.fulfill(1);
                if (!SAFE_NAME.matcher(table).matches()) {
                    Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                        DbErrorEvent.instance.fireFor(id, "Invalid table name: " + table, null, "db_count"));
                    return null;
                }
                String sql = "SELECT COUNT(*) FROM " + table;
                try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                     PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return new ElementTag(rs.getInt(1));
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                        DbErrorEvent.instance.fireFor(id, e.getMessage(), e instanceof java.sql.SQLException ? ((java.sql.SQLException)e).getSQLState() : null, sql));
                }
            }
            return null;
        });

        // <--[tag]
        // @Attribute <db_info[<id>]>
        // @Returns MapTag
        // @Group Datenizen
        // @Description Returns HikariCP pool statistics.
        // Keys: active_connections, idle_connections, total_connections, threads_awaiting.
        // -->
        TagManager.registerTagHandler(MapTag.class, "db_info", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            HikariDataSource ds = Datenizen.getInstance().getDatabaseManager().getDataSource(id);
            if (ds != null) {
                HikariPoolMXBean mx = ds.getHikariPoolMXBean();
                if (mx != null) {
                    MapTag map = new MapTag();
                    map.putObject("active_connections", new ElementTag(mx.getActiveConnections()));
                    map.putObject("idle_connections",   new ElementTag(mx.getIdleConnections()));
                    map.putObject("total_connections",  new ElementTag(mx.getTotalConnections()));
                    map.putObject("threads_awaiting",   new ElementTag(mx.getThreadsAwaitingConnection()));
                    return map;
                }
            }
            return null;
        });

        // <--[tag]
        // @Attribute <db_connected[<id>]>
        // @Returns ElementTag(Boolean)
        // @Group Datenizen
        // @Description Returns true if the database ID is registered and its pool is open.
        //
        // @Usage
        // - if <db_connected[main]>:
        //   - narrate "Database is online."
        // -->
        TagManager.registerTagHandler(ElementTag.class, "db_connected", attribute -> {
            if (!attribute.hasParam()) return new ElementTag(false);
            String id = attribute.getParam();
            attribute.fulfill(1);
            HikariDataSource ds = Datenizen.getInstance().getDatabaseManager().getDataSource(id);
            return new ElementTag(ds != null && !ds.isClosed());
        });

        // <--[tag]
        // @Attribute <db_ping[<id>]>
        // @Returns ElementTag(Boolean)
        // @Group Datenizen
        // @Description Actively tests the connection with isValid(1).
        // More reliable than db_connected for detecting stale or dropped connections.
        //
        // @Usage
        // - if !<db_ping[main]>:
        //   - db_reconnect id:main
        // -->
        TagManager.registerTagHandler(ElementTag.class, "db_ping", attribute -> {
            if (!attribute.hasParam()) return new ElementTag(false);
            String id = attribute.getParam();
            attribute.fulfill(1);
            HikariDataSource ds = Datenizen.getInstance().getDatabaseManager().getDataSource(id);
            if (ds == null || ds.isClosed()) return new ElementTag(false);
            try (Connection conn = ds.getConnection()) {
                return new ElementTag(conn.isValid(1));
            } catch (Exception e) {
                return new ElementTag(false);
            }
        });

        // <--[tag]
        // @Attribute <db_list>
        // @Returns ListTag
        // @Group Datenizen
        // @Description Returns a list of all active database connection IDs.
        //
        // @Usage
        // - narrate "Active DBs: <db_list>"
        // -->
        TagManager.registerTagHandler(ListTag.class, "db_list", attribute -> {
            ListTag list = new ListTag();
            for (String id : Datenizen.getInstance().getDatabaseManager().getActiveIds()) {
                list.addObject(new ElementTag(id));
            }
            return list;
        });

        // <--[tag]
        // @Attribute <db_cached_query[<id>].sql[<query>].ttl[<ticks>].args[<list>]>
        // @Returns ListTag
        // @Group Datenizen
        // @Description Returns query results cached for the given TTL in ticks.
        // The first call runs the query and caches the result; subsequent calls within the TTL
        // return the cached ListTag of MapTags without hitting the database.
        // Cache is automatically invalidated when the connection is disconnected.
        // -->
        TagManager.registerTagHandler(ListTag.class, "db_cached_query", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (!attribute.startsWith("sql") || !attribute.hasParam()) return null;
            String sql = attribute.getParam();
            attribute.fulfill(1);
            if (!attribute.startsWith("ttl") || !attribute.hasParam()) return null;
            long ttlTicks;
            try { ttlTicks = Long.parseLong(attribute.getParam()); } catch (NumberFormatException e) { return null; }
            attribute.fulfill(1);
            long ttlMs = ttlTicks * 50L;
            ListTag args = attribute.startsWith("args") && attribute.hasParam() ? attribute.contextAsType(1, ListTag.class) : null;
            if (args != null) attribute.fulfill(1);

            String cacheKey = id + ":" + sql + ":" + (args != null ? args.identify() : "");
            long now = System.currentTimeMillis();

            ListTag cached = Datenizen.getInstance().getDatabaseManager().getCached(cacheKey, now);
            if (cached != null) return cached;

            ListTag result = getResultSetAsList(id, sql, args);
            if (result != null) {
                Datenizen.getInstance().getDatabaseManager().putCache(cacheKey, result, now + ttlMs);
            }
            return result;
        });

        // <--[tag]
        // @Attribute <db_query_page[<id>].sql[<query>].page[<n>].size[<size>].args[<list>]>
        // @Returns ListTag
        // @Group Datenizen
        // @Description Returns a paginated slice of query results (page is 1-based, default size 20).
        // Appends LIMIT and OFFSET to the provided SQL query.
        // -->
        TagManager.registerTagHandler(ListTag.class, "db_query_page", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (!attribute.startsWith("sql") || !attribute.hasParam()) return null;
            String sql = attribute.getParam();
            attribute.fulfill(1);
            if (!attribute.startsWith("page") || !attribute.hasParam()) return null;
            int page;
            try { page = Integer.parseInt(attribute.getParam()); } catch (NumberFormatException e) { return null; }
            attribute.fulfill(1);
            int size = 20;
            if (attribute.startsWith("size") && attribute.hasParam()) {
                try { size = Integer.parseInt(attribute.getParam()); } catch (NumberFormatException e) { return null; }
                attribute.fulfill(1);
            }
            ListTag args = attribute.startsWith("args") && attribute.hasParam() ? attribute.contextAsType(1, ListTag.class) : null;
            if (args != null) attribute.fulfill(1);

            if (page < 1) page = 1;
            if (size < 1) size = 1;
            int offset = (page - 1) * size;
            String pagedSql = sql + " LIMIT " + size + " OFFSET " + offset;
            return getResultSetAsList(id, pagedSql, args);
        });

    }
}