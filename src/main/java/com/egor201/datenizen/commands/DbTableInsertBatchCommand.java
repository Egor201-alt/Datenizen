package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.utilities.text.StringHolder;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.database.DatabaseManager;
import com.egor201.datenizen.util.Identifiers;
import com.egor201.datenizen.util.SqlUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DbTableInsertBatchCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_table_insert_batch
    // @Syntax db_table_insert_batch [id:<id>] [table:<table>] [rows:<list>] (mode:<insert|ignore>) (timeout:<ms>) (label:<label>)
    // @Required 3
    // @Maximum 6
    // @Short Inserts many MapTag rows in one transaction.
    // @Group Datenizen
    //
    // @Description
    // Accepts a ListTag of MapTags. The first row determines the column list;
    // missing keys in later rows are inserted as NULL.
    // mode:insert (default) or mode:ignore (skip conflicting rows where supported).
    // Runs in a transaction; rolled back entirely on failure.
    // Fires 'db executed' with the total affected row count.
    //
    // @Usage
    // - db_table_insert_batch id:main table:players rows:<list[<map[name=Steve|score=0]>|<map[name=Alex|score=5]>]> label:bulk_insert
    // -->
    public DbTableInsertBatchCommand() {
        super("db_table_insert_batch",
              "db_table_insert_batch [id:<id>] [table:<table>] [rows:<list>] (mode:<insert|ignore>) (timeout:<ms>) (label:<label>)",
              3, 6,
              "Must specify id, table, and rows!",
              ArgSpec.element("id"),
              ArgSpec.element("table"),
              ArgSpec.list("rows"),
              ArgSpec.optionalElement("mode"),
              ArgSpec.optionalElement("timeout"),
              ArgSpec.optionalElement("label"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id = se.getElement("id").asString();
        String table = se.getElement("table").asString();
        ListTag rowsTag = se.getObjectTag("rows");
        String mode = se.hasObject("mode") ? se.getElement("mode").asString().toLowerCase() : "insert";
        ElementTag timeoutTag = se.getElement("timeout");
        String label = se.hasObject("label") ? se.getElement("label").asString() : null;

        if (!Identifiers.isValid(table)) {
            DbCommandSupport.error(id, "Invalid table name: " + table, "db_table_insert_batch");
            return;
        }
        if (!mode.equals("insert") && !mode.equals("ignore")) {
            DbCommandSupport.error(id, "mode must be insert or ignore", "db_table_insert_batch");
            return;
        }

        List<MapTag> parsed = SqlUtils.parseMapRows(rowsTag);
        if (parsed == null || parsed.isEmpty()) {
            DbCommandSupport.error(id, "rows must be a non-empty list of maps", "db_table_insert_batch");
            return;
        }

        List<String> columns = new ArrayList<>();
        for (StringHolder holder : parsed.get(0).map.keySet()) {
            if (!Identifiers.isValid(holder.str)) {
                DbCommandSupport.error(id, "Invalid column name: " + holder.str, "db_table_insert_batch");
                return;
            }
            columns.add(holder.str);
        }
        if (columns.isEmpty()) {
            DbCommandSupport.error(id, "rows contain no columns", "db_table_insert_batch");
            return;
        }

        long timeoutMs = -1;
        if (timeoutTag != null) {
            timeoutMs = SqlUtils.parseDurationMillis(timeoutTag.asString());
            if (timeoutMs < 0) {
                DbCommandSupport.error(id, "Invalid timeout: " + timeoutTag.asString(), "db_table_insert_batch");
                return;
            }
        }
        final long finalTimeout = timeoutMs;
        final String finalMode = mode;

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            DatabaseManager manager = Datenizen.getInstance().getDatabaseManager();
            String dbType = manager.getDatabaseType(id);

            StringBuilder colPart = new StringBuilder();
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    colPart.append(",");
                    placeholders.append(",");
                }
                colPart.append(columns.get(i));
                placeholders.append("?");
            }

            String prefix;
            String suffix = "";
            if (finalMode.equals("ignore")) {
                if (dbType.equals("mysql") || dbType.equals("mariadb")) {
                    prefix = "INSERT IGNORE INTO ";
                } else {
                    prefix = "INSERT INTO ";
                    suffix = " ON CONFLICT DO NOTHING";
                }
            } else {
                prefix = "INSERT INTO ";
            }
            String sql = prefix + table + " (" + colPart + ") VALUES (" + placeholders + ")" + suffix;

            long start = System.nanoTime();
            Connection conn = null;
            try {
                conn = manager.getConnection(id);
                conn.setAutoCommit(false);

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    DbCommandSupport.applySettings(ps, finalTimeout);
                    for (MapTag row : parsed) {
                        int index = 1;
                        for (String column : columns) {
                            ObjectTag value = row.map.get(new StringHolder(column));
                            ps.setString(index++, value != null ? value.toString() : null);
                        }
                        ps.addBatch();
                    }

                    int[] results = ps.executeBatch();
                    conn.commit();

                    int total = 0;
                    for (int result : results) {
                        if (result > 0) total += result;
                        else if (result == Statement.SUCCESS_NO_INFO) total++;
                    }

                    manager.afterWrite(id);
                    long duration = DbCommandSupport.elapsedMs(start);
                    manager.recordQuery(id, duration, true, sql);
                    DbCommandSupport.executed(id, label, total, null, duration);
                }
            } catch (Exception e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (Exception ignored) {}
                }
                long duration = DbCommandSupport.elapsedMs(start);
                manager.recordQuery(id, duration, false, sql);
                manager.recordError(id, e, sql);
                DbCommandSupport.error(id, e, sql);
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(true); } catch (Exception ignored) {}
                    try { conn.close(); } catch (Exception ignored) {}
                }
            }
        });
    }
}
