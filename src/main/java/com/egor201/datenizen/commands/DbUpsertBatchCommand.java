package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.utilities.text.StringHolder;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbErrorEvent;
import com.egor201.datenizen.events.DbExecutedEvent;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class DbUpsertBatchCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_upsert_batch
    // @Syntax db_upsert_batch [id:<id>] [table:<table>] [key_column:<col>] [rows:<list>] (timeout:<ms>) (label:<label>)
    // @Required 4
    // @Maximum 6
    // @Short Upserts multiple rows in a single batched statement.
    // @Group Datenizen
    //
    // @Description
    // Accepts a ListTag of MapTags, each representing one row.
    // All rows must contain the key_column. Column names and table name must be alphanumeric/underscores only.
    // Uses ON CONFLICT ... DO UPDATE (SQLite/PostgreSQL) or ON DUPLICATE KEY UPDATE (MySQL/MariaDB).
    // Runs in a transaction; rolled back entirely on failure.
    // Fires 'db executed' with total affected rows on success.
    //
    // @Usage
    // - db_upsert_batch id:main table:players key_column:uuid rows:<list[<map[uuid=abc|coins=0]>|<map[uuid=def|coins=5]>]> label:bulk_save
    // -->

    private static final Pattern SAFE_NAME = com.egor201.datenizen.util.Identifiers.PATTERN;

    public DbUpsertBatchCommand() {
        super("db_upsert_batch",
              "db_upsert_batch [id:<id>] [table:<table>] [key_column:<col>] [rows:<list>] (timeout:<ms>) (label:<label>)",
              4, 6,
              "Must specify id, table, key_column, and rows!",
              ArgSpec.element("id"),
              ArgSpec.element("table"),
              ArgSpec.element("key_column"),
              ArgSpec.list("rows"),
              ArgSpec.optionalElement("timeout"),
              ArgSpec.optionalElement("label"));
    }

    private String buildSql(String dbType, String table, String keyCol, List<String> cols) {
        StringBuilder colsPart = new StringBuilder(keyCol);
        StringBuilder placeholders = new StringBuilder("?");
        StringBuilder updatePart = new StringBuilder();

        for (int i = 0; i < cols.size(); i++) {
            colsPart.append(",").append(cols.get(i));
            placeholders.append(",?");
            if (i > 0) updatePart.append(",");
            updatePart.append(cols.get(i));
            if (dbType.equals("postgresql") || dbType.equals("sqlite")) {
                updatePart.append("=EXCLUDED.").append(cols.get(i));
            } else {
                updatePart.append("=?");
            }
        }

        return switch (dbType) {
            case "sqlite", "postgresql" ->
                "INSERT INTO " + table + " (" + colsPart + ") VALUES (" + placeholders + ")" +
                " ON CONFLICT (" + keyCol + ") DO UPDATE SET " + updatePart;
            default ->
                "INSERT INTO " + table + " (" + colsPart + ") VALUES (" + placeholders + ")" +
                " ON DUPLICATE KEY UPDATE " + updatePart;
        };
    }

    @Override
    public void execute(ScriptEntry se) {
        String id     = se.getElement("id").asString();
        String table  = se.getElement("table").asString();
        String keyCol = se.getElement("key_column").asString();
        ListTag rows  = se.getObjectTag("rows");
        com.denizenscript.denizencore.objects.core.ElementTag timeoutTag = se.getElement("timeout");
        String label  = se.hasObject("label") ? se.getElement("label").asString() : null;

        long timeoutMs = -1;
        if (timeoutTag != null) {
            timeoutMs = com.egor201.datenizen.util.SqlUtils.parseDurationMillis(timeoutTag.asString());
            if (timeoutMs < 0) {
                DbCommandSupport.error(id, "Invalid timeout: " + timeoutTag.asString(), "db_upsert_batch");
                return;
            }
        }
        final long finalTimeout = timeoutMs;

        if (!SAFE_NAME.matcher(table).matches() || !SAFE_NAME.matcher(keyCol).matches()) {
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                DbErrorEvent.instance.fireFor(id, "Invalid table or key_column name", null, "db_upsert_batch")
            );
            return;
        }

        if (rows == null || rows.isEmpty()) {
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                DbErrorEvent.instance.fireFor(id, "rows list must not be empty", null, "db_upsert_batch")
            );
            return;
        }

        List<MapTag> parsedRows = new ArrayList<>();
        List<String> cols = null;
        StringHolder keyHolder = new StringHolder(keyCol);

        for (int i = 0; i < rows.size(); i++) {
            MapTag map;
            ObjectTag objForm = (rows.objectForms != null && i < rows.objectForms.size()) ? rows.objectForms.get(i) : null;
            if (objForm instanceof MapTag) {
                map = (MapTag) objForm;
            } else {
                map = MapTag.valueOf(rows.get(i), null);
            }
            if (map == null) {
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, "Each row entry must be a valid MapTag", null, "db_upsert_batch")
                );
                return;
            }
            if (!map.map.containsKey(keyHolder)) {
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, "key_column '" + keyCol + "' missing from a row", null, "db_upsert_batch")
                );
                return;
            }
            if (cols == null) {
                cols = new ArrayList<>();
                for (StringHolder sh : map.map.keySet()) {
                    if (sh.low.equals(keyHolder.low)) continue;
                    if (!SAFE_NAME.matcher(sh.str).matches()) {
                        final String bad = sh.str;
                        Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                            DbErrorEvent.instance.fireFor(id, "Invalid column name: " + bad, null, "db_upsert_batch")
                        );
                        return;
                    }
                    cols.add(sh.str);
                }
                if (cols.isEmpty()) {
                    Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                        DbErrorEvent.instance.fireFor(id, "rows contain no columns besides key_column", null, "db_upsert_batch")
                    );
                    return;
                }
            }
            parsedRows.add(map);
        }

        final List<String> finalCols = cols;

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            com.egor201.datenizen.database.DatabaseManager manager = Datenizen.getInstance().getDatabaseManager();
            String dbType = manager.getDatabaseType(id);
            String sql = buildSql(dbType, table, keyCol, finalCols);
            long start = System.nanoTime();
            Connection conn = null;
            try {
                conn = manager.getConnection(id);
                conn.setAutoCommit(false);

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    DbCommandSupport.applySettings(ps, finalTimeout);
                    for (MapTag row : parsedRows) {
                        int idx = 1;
                        ObjectTag kv = row.map.get(keyHolder);
                        ps.setString(idx++, kv != null ? kv.toString() : null);
                        for (String col : finalCols) {
                            ObjectTag cv = row.map.get(new StringHolder(col));
                            ps.setString(idx++, cv != null ? cv.toString() : null);
                        }
                        if (dbType.equals("mysql") || dbType.equals("mariadb")) {
                            for (String col : finalCols) {
                                ObjectTag cv = row.map.get(new StringHolder(col));
                                ps.setString(idx++, cv != null ? cv.toString() : null);
                            }
                        }
                        ps.addBatch();
                    }

                    int[] results = ps.executeBatch();
                    conn.commit();

                    int total = 0;
                    for (int r : results) if (r > 0) total += r;
                    manager.afterWrite(id);
                    long duration = DbCommandSupport.elapsedMs(start);
                    manager.recordQuery(id, duration, true, sql);
                    DbCommandSupport.executed(id, label, total, null, duration);
                }
            } catch (Exception e) {
                if (conn != null) { try { conn.rollback(); } catch (Exception ignored) {} }
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
