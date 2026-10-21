package com.egor201.datenizen.tags;

import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.tags.TagManager;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.Settings;
import com.egor201.datenizen.database.DatabaseManager;
import com.egor201.datenizen.database.DbStats;
import com.egor201.datenizen.database.ResultMapper;
import com.egor201.datenizen.events.DbErrorEvent;
import com.egor201.datenizen.util.Identifiers;
import com.egor201.datenizen.util.JsonUtils;
import com.egor201.datenizen.util.NamedParams;
import com.egor201.datenizen.util.SqlUtils;
import com.egor201.datenizen.util.Tables;
import com.google.gson.JsonArray;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.bukkit.Bukkit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class DatenizenTags {

    @FunctionalInterface
    private interface ResultReader<T> {
        T read(ResultSet rs) throws SQLException;
    }

    /**
     * Runs a parameterized query and converts its ResultSet using the given reader.
     * All db_* query tags share this single connection/bind/error path.
     */
    private static <T> T readQuery(String id, String sql, ListTag args, MapTag namedArgs, ResultReader<T> reader) {
        long start = System.nanoTime();
        DatabaseManager manager = Datenizen.getInstance().getDatabaseManager();
        try {
            NamedParams.Bound bound = NamedParams.bind(sql, namedArgs);
            try (Connection conn = manager.getConnection(id);
                 PreparedStatement ps = conn.prepareStatement(bound.sql())) {

                if (Settings.queryTimeoutSeconds() > 0) {
                    ps.setQueryTimeout(Settings.queryTimeoutSeconds());
                }
                if (Settings.fetchSize > 0) {
                    ps.setFetchSize(Settings.fetchSize);
                }
                if (!bound.values().isEmpty()) {
                    ResultMapper.bindValues(ps, bound.values());
                } else {
                    ResultMapper.bind(ps, args);
                }
                if (Settings.logQueries) {
                    Bukkit.getLogger().info("[Datenizen] [SQL] " + bound.sql());
                }

                try (ResultSet rs = ps.executeQuery()) {
                    T result = reader.read(rs);
                    long duration = (System.nanoTime() - start) / 1_000_000L;
                    manager.recordQuery(id, duration, true, bound.sql());
                    return result;
                }
            }
        } catch (Exception e) {
            long duration = (System.nanoTime() - start) / 1_000_000L;
            manager.recordQuery(id, duration, false, sql);
            manager.recordError(id, e, sql);
            reportError(id, e, sql);
            return null;
        }
    }

    private static <T> T readQuery(String id, String sql, ListTag args, ResultReader<T> reader) {
        return readQuery(id, sql, args, null, reader);
    }

    private static void reportError(String id, Exception e, String sql) {
        String state = e instanceof SQLException ? ((SQLException) e).getSQLState() : null;
        Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
            DbErrorEvent.instance.fireFor(id, e.getMessage(), state, sql));
    }

    /** Same as {@link #reportError}, but always reports a null SQL state (used by metadata calls). */
    private static void reportErrorNoState(String id, Exception e, String operation) {
        Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
            DbErrorEvent.instance.fireFor(id, e.getMessage(), null, operation));
    }

    private static ListTag getResultSetAsList(String id, String sql, ListTag args) {
        return readQuery(id, sql, args, ResultMapper::rows);
    }

    private static ListTag getResultSetAsList(String id, String sql, ListTag args, MapTag namedArgs) {
        return readQuery(id, sql, args, namedArgs, ResultMapper::rows);
    }

    private static MapTag namedArgs(com.denizenscript.denizencore.tags.Attribute attribute) {
        if (attribute.startsWith("named_args") && attribute.hasParam()) {
            MapTag result = attribute.contextAsType(1, MapTag.class);
            attribute.fulfill(1);
            return result;
        }
        return null;
    }

    public static void register() {

        // <--[tag]
        // @Attribute <db_query[<id>].sql[<query>].args[<list>].named_args[<map>]>
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
                MapTag named = namedArgs(attribute);
                return getResultSetAsList(id, sql, args, named);
            }
            return null;
        });

        // <--[tag]
        // @Attribute <db_query_keyed[<id>].sql[<query>].key[<column>].args[<list>].named_args[<map>]>
        // @Returns MapTag
        // @Group Datenizen
        // @Description Returns query results as a MapTag keyed by the given column.
        // -->
        TagManager.registerTagHandler(MapTag.class, "db_query_keyed", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (attribute.startsWith("sql") && attribute.hasParam()) {
                String sql = attribute.getParam();
                attribute.fulfill(1);
                ListTag args = attribute.startsWith("args") && attribute.hasParam() ? attribute.contextAsType(1, ListTag.class) : null;
                if (args != null) attribute.fulfill(1);
                MapTag named = namedArgs(attribute);
                if (attribute.startsWith("key") && attribute.hasParam()) {
                    String key = attribute.getParam();
                    attribute.fulfill(1);
                    return readQuery(id, sql, args, named, rs -> ResultMapper.keyedRows(rs, key));
                }
            }
            return null;
        });

        // <--[tag]
        // @Attribute <db_query_column[<id>].sql[<query>].args[<list>].named_args[<map>]>
        // @Returns ListTag
        // @Group Datenizen
        // @Description Returns the first column of every row as a ListTag.
        // -->
        TagManager.registerTagHandler(ListTag.class, "db_query_column", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (attribute.startsWith("sql") && attribute.hasParam()) {
                String sql = attribute.getParam();
                attribute.fulfill(1);
                ListTag args = attribute.startsWith("args") && attribute.hasParam() ? attribute.contextAsType(1, ListTag.class) : null;
                if (args != null) attribute.fulfill(1);
                MapTag named = namedArgs(attribute);
                return readQuery(id, sql, args, named, ResultMapper::column);
            }
            return null;
        });

        // <--[tag]
        // @Attribute <db_query_first[<id>].sql[<query>].args[<list>].named_args[<map>]>
        // @Returns MapTag
        // @Group Datenizen
        // @Description Returns the first row of the query result as a MapTag, or null if no rows.
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
                MapTag named = namedArgs(attribute);
                return readQuery(id, sql, args, named, ResultMapper::firstRow);
            }
            return null;
        });

        // <--[tag]
        // @Attribute <db_convert_map[<id>].sql[<query>].args[<list>].named_args[<map>]>
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
                MapTag named = namedArgs(attribute);
                return readQuery(id, sql, args, named, ResultMapper::keyValueMap);
            }
            return null;
        });

        // <--[tag]
        // @Attribute <db_query_as_json[<id>].sql[<query>].args[<list>].named_args[<map>]>
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
                MapTag named = namedArgs(attribute);
                JsonArray arr = readQuery(id, sql, args, named, ResultMapper::json);
                return arr == null ? null : new ElementTag(arr.toString());
            }
            return null;
        });

        // <--[tag]
        // @Attribute <db_select[<id>].table[<table>].columns[<list>].where[<sql>].args[<list>].named_args[<map>].order[<sql>].limit[<n>].offset[<n>]>
        // @Returns ListTag
        // @Group Datenizen
        // @Description Returns selected rows as a ListTag of MapTags.
        // -->
        TagManager.registerTagHandler(ListTag.class, "db_select", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (!attribute.startsWith("table") || !attribute.hasParam()) return null;
            String table = attribute.getParam();
            attribute.fulfill(1);
            if (!Identifiers.isValid(table)) return null;

            ListTag columns = attribute.startsWith("columns") && attribute.hasParam() ? attribute.contextAsType(1, ListTag.class) : null;
            if (columns != null) attribute.fulfill(1);
            if (columns != null) {
                for (String column : columns) {
                    if (!column.equals("*") && !Identifiers.isValid(column)) return null;
                }
            }

            String where = attribute.startsWith("where") && attribute.hasParam() ? attribute.getParam() : null;
            if (where != null) attribute.fulfill(1);
            ListTag args = attribute.startsWith("args") && attribute.hasParam() ? attribute.contextAsType(1, ListTag.class) : null;
            if (args != null) attribute.fulfill(1);
            MapTag named = namedArgs(attribute);

            String order = attribute.startsWith("order") && attribute.hasParam() ? attribute.getParam() : null;
            if (order != null) attribute.fulfill(1);
            if (order != null && !SqlUtils.isSafeOrder(order)) return null;

            Integer limit = null;
            Integer offset = null;
            try {
                if (attribute.startsWith("limit") && attribute.hasParam()) {
                    limit = Integer.parseInt(attribute.getParam());
                    attribute.fulfill(1);
                }
                if (attribute.startsWith("offset") && attribute.hasParam()) {
                    offset = Integer.parseInt(attribute.getParam());
                    attribute.fulfill(1);
                }
            } catch (NumberFormatException e) {
                return null;
            }

            String sql = SqlUtils.buildSelect(table, columns, where, order, limit, offset);
            return getResultSetAsList(id, sql, args, named);
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
                reportError(dbId, e, sql);
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
                    reportErrorNoState(id, e, "CHECK TABLE EXISTS");
                }
            }
            return new ElementTag(false);
        });

        // <--[tag]
        // @Attribute <db_value[<id>].sql[<query>].args[<list>].named_args[<map>]>
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
                MapTag named = namedArgs(attribute);
                return readQuery(id, sql, args, named, ResultMapper::firstValue);
            }
            return null;
        });

        // <--[tag]
        // @Attribute <db_exists[<id>].sql[<query>].args[<list>].named_args[<map>]>
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
                MapTag named = namedArgs(attribute);
                Boolean exists = readQuery(id, sql, args, named, ResultMapper::hasRow);
                return new ElementTag(exists != null && exists);
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
                    reportErrorNoState(id, e, "GET COLUMNS");
                }
            }
            return null;
        });

        // <--[tag]
        // @Attribute <db_column_types[<id>].table[<name>]>
        // @Returns MapTag
        // @Group Datenizen
        // @Description Returns a MapTag of column name -> SQL type name.
        // -->
        TagManager.registerTagHandler(MapTag.class, "db_column_types", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (!attribute.startsWith("table") || !attribute.hasParam()) return null;
            String table = attribute.getParam();
            attribute.fulfill(1);
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id)) {
                DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet rs = meta.getColumns(null, null, table, null)) {
                    MapTag map = new MapTag();
                    while (rs.next()) {
                        map.putObject(rs.getString("COLUMN_NAME"),
                            new ElementTag(rs.getString("TYPE_NAME") != null ? rs.getString("TYPE_NAME") : ""));
                    }
                    return map;
                }
            } catch (Exception e) {
                reportErrorNoState(id, e, "GET COLUMN TYPES");
                return null;
            }
        });

        // <--[tag]
        // @Attribute <db_column_exists[<id>].table[<name>].column[<name>]>
        // @Returns ElementTag(Boolean)
        // @Group Datenizen
        // @Description Returns true if the column exists in the table.
        // -->
        TagManager.registerTagHandler(ElementTag.class, "db_column_exists", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (!attribute.startsWith("table") || !attribute.hasParam()) return null;
            String table = attribute.getParam();
            attribute.fulfill(1);
            if (!attribute.startsWith("column") || !attribute.hasParam()) return null;
            String column = attribute.getParam();
            attribute.fulfill(1);
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id)) {
                DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet rs = meta.getColumns(null, null, table, column)) {
                    while (rs.next()) {
                        if (rs.getString("COLUMN_NAME").equalsIgnoreCase(column)) {
                            return new ElementTag(true);
                        }
                    }
                }
            } catch (Exception e) {
                reportErrorNoState(id, e, "CHECK COLUMN EXISTS");
            }
            return new ElementTag(false);
        });

        // <--[tag]
        // @Attribute <db_indexes[<id>].table[<name>]>
        // @Returns ListTag
        // @Group Datenizen
        // @Description Returns index metadata as a ListTag of MapTags.
        // -->
        TagManager.registerTagHandler(ListTag.class, "db_indexes", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (!attribute.startsWith("table") || !attribute.hasParam()) return null;
            String table = attribute.getParam();
            attribute.fulfill(1);
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id)) {
                DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet rs = meta.getIndexInfo(null, null, table, false, false)) {
                    ListTag list = new ListTag();
                    while (rs.next()) {
                        String name = rs.getString("INDEX_NAME");
                        if (name == null) continue;
                        MapTag map = new MapTag();
                        map.putObject("name", new ElementTag(name));
                        map.putObject("column", new ElementTag(rs.getString("COLUMN_NAME") != null ? rs.getString("COLUMN_NAME") : ""));
                        map.putObject("unique", new ElementTag(!rs.getBoolean("NON_UNIQUE")));
                        map.putObject("position", new ElementTag(rs.getShort("ORDINAL_POSITION")));
                        list.addObject(map);
                    }
                    return list;
                }
            } catch (Exception e) {
                reportErrorNoState(id, e, "GET INDEXES");
                return null;
            }
        });

        // <--[tag]
        // @Attribute <db_foreign_keys[<id>].table[<name>]>
        // @Returns ListTag
        // @Group Datenizen
        // @Description Returns imported foreign keys as a ListTag of MapTags.
        // -->
        TagManager.registerTagHandler(ListTag.class, "db_foreign_keys", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (!attribute.startsWith("table") || !attribute.hasParam()) return null;
            String table = attribute.getParam();
            attribute.fulfill(1);
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id)) {
                DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet rs = meta.getImportedKeys(null, null, table)) {
                    ListTag list = new ListTag();
                    while (rs.next()) {
                        MapTag map = new MapTag();
                        map.putObject("name", new ElementTag(rs.getString("FK_NAME") != null ? rs.getString("FK_NAME") : ""));
                        map.putObject("column", new ElementTag(rs.getString("FKCOLUMN_NAME") != null ? rs.getString("FKCOLUMN_NAME") : ""));
                        map.putObject("ref_table", new ElementTag(rs.getString("PKTABLE_NAME") != null ? rs.getString("PKTABLE_NAME") : ""));
                        map.putObject("ref_column", new ElementTag(rs.getString("PKCOLUMN_NAME") != null ? rs.getString("PKCOLUMN_NAME") : ""));
                        map.putObject("update_rule", new ElementTag(rs.getShort("UPDATE_RULE")));
                        map.putObject("delete_rule", new ElementTag(rs.getShort("DELETE_RULE")));
                        list.addObject(map);
                    }
                    return list;
                }
            } catch (Exception e) {
                reportErrorNoState(id, e, "GET FOREIGN KEYS");
                return null;
            }
        });

        // <--[tag]
        // @Attribute <db_primary_key[<id>].table[<name>]>
        // @Returns ListTag
        // @Group Datenizen
        // @Description Returns primary-key column names in key order.
        // -->
        TagManager.registerTagHandler(ListTag.class, "db_primary_key", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (!attribute.startsWith("table") || !attribute.hasParam()) return null;
            String table = attribute.getParam();
            attribute.fulfill(1);
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id)) {
                DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet rs = meta.getPrimaryKeys(null, null, table)) {
                    TreeMap<Integer, String> ordered = new TreeMap<>();
                    while (rs.next()) {
                        ordered.put((int) rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
                    }
                    ListTag list = new ListTag();
                    for (String column : ordered.values()) {
                        list.addObject(new ElementTag(column));
                    }
                    return list;
                }
            } catch (Exception e) {
                reportErrorNoState(id, e, "GET PRIMARY KEY");
                return null;
            }
        });

        // <--[tag]
        // @Attribute <db_version[<id>]>
        // @Returns ElementTag
        // @Group Datenizen
        // @Description Returns the database product name and version.
        // -->
        TagManager.registerTagHandler(ElementTag.class, "db_version", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id)) {
                DatabaseMetaData meta = conn.getMetaData();
                return new ElementTag(meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion());
            } catch (Exception e) {
                reportErrorNoState(id, e, "GET VERSION");
                return null;
            }
        });

        // <--[tag]
        // @Attribute <db_databases[<id>]>
        // @Returns ListTag
        // @Group Datenizen
        // @Description Returns catalog/database names reported by the driver.
        // -->
        TagManager.registerTagHandler(ListTag.class, "db_databases", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            ListTag list = new ListTag();
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id)) {
                DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet rs = meta.getCatalogs()) {
                    while (rs.next()) {
                        String catalog = rs.getString("TABLE_CAT");
                        if (catalog != null) list.addObject(new ElementTag(catalog));
                    }
                }
                return list;
            } catch (Exception e) {
                return list;
            }
        });

        // <--[tag]
        // @Attribute <db_tables[<id>]>
        // @Returns ListTag
        // @Group Datenizen
        // @Description Returns all table names in the database.
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
                reportErrorNoState(id, e, "GET TABLES");
            }
            return null;
        });

        // <--[tag]
        // @Attribute <db_count[<id>].table[<name>].where[<sql>].args[<list>].named_args[<map>]>
        // @Returns ElementTag
        // @Group Datenizen
        // @Description Returns total row count for a table, optionally with a WHERE clause.
        // Table name must be alphanumeric/underscores only.
        // -->
        TagManager.registerTagHandler(ElementTag.class, "db_count", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (!attribute.startsWith("table") || !attribute.hasParam()) return null;
            String table = attribute.getParam();
            attribute.fulfill(1);
            if (!Identifiers.isValid(table)) {
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, "Invalid table name: " + table, null, "db_count"));
                return null;
            }

            String where = attribute.startsWith("where") && attribute.hasParam() ? attribute.getParam() : null;
            if (where != null) attribute.fulfill(1);
            ListTag args = attribute.startsWith("args") && attribute.hasParam() ? attribute.contextAsType(1, ListTag.class) : null;
            if (args != null) attribute.fulfill(1);
            MapTag named = namedArgs(attribute);

            String sql = "SELECT COUNT(*) FROM " + table + (where != null && !where.isBlank() ? " WHERE " + where : "");
            return readQuery(id, sql, args, named, ResultMapper::firstValue);
        });

        // <--[tag]
        // @Attribute <db_aggregate[<id>].table[<table>].function[<sum|avg|min|max|count>].column[<column>].where[<sql>].args[<list>]>
        // @Returns ElementTag
        // @Group Datenizen
        // @Description Returns an aggregate value for a column.
        // count without column uses COUNT(*).
        // -->
        TagManager.registerTagHandler(ElementTag.class, "db_aggregate", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (!attribute.startsWith("table") || !attribute.hasParam()) return null;
            String table = attribute.getParam();
            attribute.fulfill(1);
            if (!Identifiers.isValid(table)) return null;
            if (!attribute.startsWith("function") || !attribute.hasParam()) return null;
            String function = attribute.getParam().toLowerCase();
            attribute.fulfill(1);
            if (!function.equals("sum") && !function.equals("avg") && !function.equals("min")
                && !function.equals("max") && !function.equals("count")) return null;

            String column = attribute.startsWith("column") && attribute.hasParam() ? attribute.getParam() : null;
            if (column != null) attribute.fulfill(1);
            if (column != null && !Identifiers.isValid(column)) return null;
            if (column == null && !function.equals("count")) return null;

            String where = attribute.startsWith("where") && attribute.hasParam() ? attribute.getParam() : null;
            if (where != null) attribute.fulfill(1);
            ListTag args = attribute.startsWith("args") && attribute.hasParam() ? attribute.contextAsType(1, ListTag.class) : null;
            if (args != null) attribute.fulfill(1);
            MapTag named = namedArgs(attribute);

            String target = column != null ? column : "*";
            String sql = "SELECT " + function.toUpperCase() + "(" + target + ") FROM " + table
                + (where != null && !where.isBlank() ? " WHERE " + where : "");
            return readQuery(id, sql, args, named, ResultMapper::firstValue);
        });

        // <--[tag]
        // @Attribute <db_leaderboard[<id>].table[<table>].column[<column>].limit[<n>].order[<asc|desc>].where[<sql>].args[<list>]>
        // @Returns ListTag
        // @Group Datenizen
        // @Description Returns rows ordered by a column, each with a 1-based 'rank' key.
        // -->
        TagManager.registerTagHandler(ListTag.class, "db_leaderboard", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (!attribute.startsWith("table") || !attribute.hasParam()) return null;
            String table = attribute.getParam();
            attribute.fulfill(1);
            if (!Identifiers.isValid(table)) return null;
            if (!attribute.startsWith("column") || !attribute.hasParam()) return null;
            String column = attribute.getParam();
            attribute.fulfill(1);
            if (!Identifiers.isValid(column)) return null;

            int limit = 10;
            if (attribute.startsWith("limit") && attribute.hasParam()) {
                try { limit = Integer.parseInt(attribute.getParam()); } catch (NumberFormatException e) { return null; }
                attribute.fulfill(1);
            }
            String direction = "DESC";
            if (attribute.startsWith("order") && attribute.hasParam()) {
                direction = attribute.getParam().equalsIgnoreCase("asc") ? "ASC" : "DESC";
                attribute.fulfill(1);
            }
            if (limit < 1) limit = 1;

            String where = attribute.startsWith("where") && attribute.hasParam() ? attribute.getParam() : null;
            if (where != null) attribute.fulfill(1);
            ListTag args = attribute.startsWith("args") && attribute.hasParam() ? attribute.contextAsType(1, ListTag.class) : null;
            if (args != null) attribute.fulfill(1);
            MapTag named = namedArgs(attribute);

            String sql = "SELECT * FROM " + table
                + (where != null && !where.isBlank() ? " WHERE " + where : "")
                + " ORDER BY " + column + " " + direction
                + " LIMIT " + limit;

            return readQuery(id, sql, args, named, rs -> {
                ListTag rows = ResultMapper.rows(rs);
                for (int i = 0; i < rows.size(); i++) {
                    MapTag row = rows.objectForms != null && i < rows.objectForms.size()
                        && rows.objectForms.get(i) instanceof MapTag
                        ? (MapTag) rows.objectForms.get(i)
                        : MapTag.valueOf(rows.get(i), null);
                    if (row != null) row.putObject("rank", new ElementTag(i + 1));
                }
                return rows;
            });
        });

        // <--[tag]
        // @Attribute <db_rank[<id>].table[<table>].column[<column>].where[<sql>].args[<list>]>
        // @Returns ElementTag
        // @Group Datenizen
        // @Description Returns the 1-based rank of the row matched by the WHERE clause.
        // -->
        TagManager.registerTagHandler(ElementTag.class, "db_rank", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (!attribute.startsWith("table") || !attribute.hasParam()) return null;
            String table = attribute.getParam();
            attribute.fulfill(1);
            if (!Identifiers.isValid(table)) return null;
            if (!attribute.startsWith("column") || !attribute.hasParam()) return null;
            String column = attribute.getParam();
            attribute.fulfill(1);
            if (!Identifiers.isValid(column)) return null;
            if (!attribute.startsWith("where") || !attribute.hasParam()) return null;
            String where = attribute.getParam();
            attribute.fulfill(1);
            ListTag args = attribute.startsWith("args") && attribute.hasParam() ? attribute.contextAsType(1, ListTag.class) : null;
            if (args != null) attribute.fulfill(1);
            MapTag named = namedArgs(attribute);

            String sql = "SELECT COUNT(*) + 1 FROM " + table
                + " WHERE " + column + " > (SELECT " + column + " FROM " + table + " WHERE " + where + " LIMIT 1)";
            return readQuery(id, sql, args, named, ResultMapper::firstValue);
        });

        // <--[tag]
        // @Attribute <db_info[<id>]>
        // @Returns MapTag
        // @Group Datenizen
        // @Description Returns HikariCP pool statistics.
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
                    map.putObject("idle_connections", new ElementTag(mx.getIdleConnections()));
                    map.putObject("total_connections", new ElementTag(mx.getTotalConnections()));
                    map.putObject("threads_awaiting", new ElementTag(mx.getThreadsAwaitingConnection()));
                    return map;
                }
            }
            return null;
        });

        // <--[tag]
        // @Attribute <db_health[<id>]>
        // @Returns MapTag
        // @Group Datenizen
        // @Description Returns connection health, latency, database type and pool state.
        // -->
        TagManager.registerTagHandler(MapTag.class, "db_health", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            DatabaseManager manager = Datenizen.getInstance().getDatabaseManager();
            HikariDataSource ds = manager.getDataSource(id);
            MapTag map = new MapTag();
            map.putObject("id", new ElementTag(id));
            map.putObject("type", new ElementTag(manager.getDatabaseType(id)));
            map.putObject("connected", new ElementTag(ds != null && !ds.isClosed()));

            if (ds != null && !ds.isClosed()) {
                long start = System.nanoTime();
                try (Connection conn = ds.getConnection()) {
                    boolean valid = conn.isValid(1);
                    map.putObject("online", new ElementTag(valid));
                    map.putObject("latency_ms", new ElementTag((System.nanoTime() - start) / 1_000_000L));
                } catch (Exception e) {
                    map.putObject("online", new ElementTag(false));
                    map.putObject("latency_ms", new ElementTag(-1));
                }
                HikariPoolMXBean mx = ds.getHikariPoolMXBean();
                if (mx != null) {
                    map.putObject("active_connections", new ElementTag(mx.getActiveConnections()));
                    map.putObject("idle_connections", new ElementTag(mx.getIdleConnections()));
                    map.putObject("threads_awaiting", new ElementTag(mx.getThreadsAwaitingConnection()));
                }
            }
            return map;
        });

        // <--[tag]
        // @Attribute <db_stats[<id>]>
        // @Returns MapTag
        // @Group Datenizen
        // @Description Returns query counters, average/max duration and slow-query counts.
        // -->
        TagManager.registerTagHandler(MapTag.class, "db_stats", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            return Datenizen.getInstance().getDatabaseManager().statsFor(id).toMap();
        });

        // <--[tag]
        // @Attribute <db_last_error[<id>]>
        // @Returns MapTag
        // @Group Datenizen
        // @Description Returns the most recent error for the database, or null.
        // -->
        TagManager.registerTagHandler(MapTag.class, "db_last_error", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            DbStats stats = Datenizen.getInstance().getDatabaseManager().getStats(id);
            return stats != null ? stats.lastError() : null;
        });

        // <--[tag]
        // @Attribute <db_slow_queries[<id>]>
        // @Returns ListTag
        // @Group Datenizen
        // @Description Returns the most recent slow queries for the database.
        // -->
        TagManager.registerTagHandler(ListTag.class, "db_slow_queries", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            DbStats stats = Datenizen.getInstance().getDatabaseManager().getStats(id);
            return stats != null ? stats.slowQueries() : new ListTag();
        });

        // <--[tag]
        // @Attribute <db_transactions>
        // @Returns ListTag
        // @Group Datenizen
        // @Description Returns active transactions with tx_id, db_id and age_ms.
        // -->
        TagManager.registerTagHandler(ListTag.class, "db_transactions", attribute ->
            Datenizen.getInstance().getDatabaseManager().getTransactions());

        // <--[tag]
        // @Attribute <db_connected[<id>]>
        // @Returns ElementTag(Boolean)
        // @Group Datenizen
        // @Description Returns true if the database ID is registered and its pool is open.
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
        // -->
        TagManager.registerTagHandler(ListTag.class, "db_list", attribute -> {
            ListTag list = new ListTag();
            for (String id : Datenizen.getInstance().getDatabaseManager().getActiveIds()) {
                list.addObject(new ElementTag(id));
            }
            return list;
        });

        // <--[tag]
        // @Attribute <db_cached_query[<id>].sql[<query>].ttl[<ticks>].args[<list>].named_args[<map>]>
        // @Returns ListTag
        // @Group Datenizen
        // @Description Returns query results cached for the given TTL in ticks.
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
            MapTag named = namedArgs(attribute);

            String cacheKey = id + ":" + sql + ":" + (args != null ? args.identify() : "")
                + ":" + (named != null ? named.toString() : "");
            long now = System.currentTimeMillis();

            ListTag cached = Datenizen.getInstance().getDatabaseManager().getCached(cacheKey, now);
            if (cached != null) return cached;

            ListTag result = getResultSetAsList(id, sql, args, named);
            if (result != null) {
                Datenizen.getInstance().getDatabaseManager().putCache(cacheKey, result, now + ttlMs);
            }
            return result;
        });

        // <--[tag]
        // @Attribute <db_query_page[<id>].sql[<query>].page[<n>].size[<size>].args[<list>].named_args[<map>]>
        // @Returns ListTag
        // @Group Datenizen
        // @Description Returns a paginated slice of query results (page is 1-based, default size 20).
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
            MapTag named = namedArgs(attribute);

            if (page < 1) page = 1;
            if (size < 1) size = 1;
            int offset = (page - 1) * size;
            String pagedSql = sql + " LIMIT " + size + " OFFSET " + offset;
            return getResultSetAsList(id, pagedSql, args, named);
        });

        // <--[tag]
        // @Attribute <db_kv_get[<id>].key[<key>].path[<json.path>]>
        // @Returns ElementTag
        // @Group Datenizen
        // @Description Returns a value stored by db_kv_set. When path is used the value is parsed as JSON.
        // -->
        TagManager.registerTagHandler(ElementTag.class, "db_kv_get", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (!attribute.startsWith("key") || !attribute.hasParam()) return null;
            String key = attribute.getParam();
            attribute.fulfill(1);
            String path = attribute.startsWith("path") && attribute.hasParam() ? attribute.getParam() : null;
            if (path != null) attribute.fulfill(1);

            String sql = "SELECT v, expires_at FROM " + Tables.KV + " WHERE k=?";
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    long expiresAt = rs.getLong(2);
                    if (expiresAt > 0 && expiresAt <= System.currentTimeMillis()) return null;
                    String value = rs.getString(1);
                    if (value == null) return null;
                    if (path != null) {
                        String extracted = JsonUtils.getPath(value, path);
                        return extracted != null ? new ElementTag(extracted) : null;
                    }
                    return new ElementTag(value);
                }
            } catch (Exception e) {
                return null;
            }
        });

        // <--[tag]
        // @Attribute <db_cooldown_remaining[<id>].key[<key>]>
        // @Returns ElementTag
        // @Group Datenizen
        // @Description Returns remaining cooldown in milliseconds, or 0 if ready.
        // -->
        TagManager.registerTagHandler(ElementTag.class, "db_cooldown_remaining", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (!attribute.startsWith("key") || !attribute.hasParam()) return null;
            String key = attribute.getParam();
            attribute.fulfill(1);
            String sql = "SELECT expires_at FROM " + Tables.COOLDOWNS + " WHERE k=?";
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return new ElementTag(0);
                    long remaining = rs.getLong(1) - System.currentTimeMillis();
                    return new ElementTag(Math.max(0, remaining));
                }
            } catch (Exception e) {
                return new ElementTag(0);
            }
        });

        // <--[tag]
        // @Attribute <db_migrate_status[<id>].path[<folder>]>
        // @Returns MapTag
        // @Group Datenizen
        // @Description Compares migration files with __datenizen_migrations.
        // Keys: applied, pending, mismatched.
        // -->
        TagManager.registerTagHandler(MapTag.class, "db_migrate_status", attribute -> {
            if (!attribute.hasParam()) return null;
            String id = attribute.getParam();
            attribute.fulfill(1);
            if (!attribute.startsWith("path") || !attribute.hasParam()) return null;
            File folder = Settings.resolvePath(attribute.getParam());
            attribute.fulfill(1);
            if (!folder.isDirectory()) return null;

            File[] fileArray = folder.listFiles((dir, name) -> name.endsWith(".sql"));
            if (fileArray == null) fileArray = new File[0];
            Arrays.sort(fileArray, (a, b) -> a.getName().compareTo(b.getName()));

            Map<String, String> appliedChecksums = new HashMap<>();
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                 Statement st = conn.createStatement()) {
                try (ResultSet rs = st.executeQuery("SELECT name, checksum FROM __datenizen_migrations")) {
                    while (rs.next()) appliedChecksums.put(rs.getString(1), rs.getString(2));
                } catch (Exception ignored) {
                    try (ResultSet rs = st.executeQuery("SELECT name FROM __datenizen_migrations")) {
                        while (rs.next()) appliedChecksums.put(rs.getString(1), null);
                    } catch (Exception ignored2) {}
                }
            } catch (Exception ignored) {}

            ListTag applied = new ListTag();
            for (String name : appliedChecksums.keySet()) applied.addObject(new ElementTag(name));

            ListTag pending = new ListTag();
            ListTag mismatched = new ListTag();
            for (File file : fileArray) {
                String name = file.getName();
                if (!appliedChecksums.containsKey(name)) {
                    pending.addObject(new ElementTag(name));
                    continue;
                }
                if (!Settings.migrationChecksums) continue;
                String recorded = appliedChecksums.get(name);
                if (recorded == null || recorded.isEmpty()) continue;
                try {
                    String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                    if (!recorded.equals(SqlUtils.checksum(content))) {
                        mismatched.addObject(new ElementTag(name));
                    }
                } catch (Exception ignored) {}
            }

            MapTag result = new MapTag();
            result.putObject("applied", applied);
            result.putObject("pending", pending);
            result.putObject("mismatched", mismatched);
            return result;
        });
    }
}
