package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.Settings;
import com.egor201.datenizen.database.DatabaseManager;
import com.egor201.datenizen.util.Identifiers;
import com.egor201.datenizen.util.SqlUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DbImportJsonCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_import_json
    // @Syntax db_import_json [id:<id>] [table:<table>] [path:<path>] (mode:<insert|upsert>) (key_column:<col>) (format:<auto|json|jsonl>) (batch_size:<n>) (timeout:<ms>) (label:<label>)
    // @Required 3
    // @Maximum 9
    // @Short Imports a JSON array or JSON Lines file into a table.
    // @Group Datenizen
    //
    // @Description
    // Reads a JSON array of objects, a single object, or one object per line.
    // The first object determines the column list; missing keys become NULL.
    // mode:upsert requires key_column and uses ON CONFLICT / ON DUPLICATE KEY UPDATE.
    // Runs in a transaction and rolls back entirely on failure.
    // Fires 'db executed' with the total affected row count.
    //
    // @Usage
    // - db_import_json id:main table:players path:plugins/Datenizen/import/players.json mode:upsert key_column:uuid label:import_players
    // -->
    public DbImportJsonCommand() {
        super("db_import_json",
              "db_import_json [id:<id>] [table:<table>] [path:<path>] (mode:<insert|upsert>) (key_column:<col>) (format:<auto|json|jsonl>) (batch_size:<n>) (timeout:<ms>) (label:<label>)",
              3, 9,
              "Must specify id, table, and path!",
              ArgSpec.element("id"),
              ArgSpec.element("table"),
              ArgSpec.element("path"),
              ArgSpec.optionalElement("mode"),
              ArgSpec.optionalElement("key_column"),
              ArgSpec.optionalElement("format"),
              ArgSpec.optionalElement("batch_size"),
              ArgSpec.optionalElement("timeout"),
              ArgSpec.optionalElement("label"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id = se.getElement("id").asString();
        String table = se.getElement("table").asString();
        String path = se.getElement("path").asString();
        String mode = se.hasObject("mode") ? se.getElement("mode").asString().toLowerCase() : "insert";
        String keyColumn = se.hasObject("key_column") ? se.getElement("key_column").asString() : null;
        String format = se.hasObject("format") ? se.getElement("format").asString().toLowerCase() : "auto";
        ElementTag batchTag = se.getElement("batch_size");
        ElementTag timeoutTag = se.getElement("timeout");
        String label = se.hasObject("label") ? se.getElement("label").asString() : null;

        if (!Identifiers.isValid(table)) {
            DbCommandSupport.error(id, "Invalid table name: " + table, "db_import_json");
            return;
        }
        if (!mode.equals("insert") && !mode.equals("upsert")) {
            DbCommandSupport.error(id, "mode must be insert or upsert", "db_import_json");
            return;
        }
        if (mode.equals("upsert") && (keyColumn == null || !Identifiers.isValid(keyColumn))) {
            DbCommandSupport.error(id, "mode:upsert requires a valid key_column", "db_import_json");
            return;
        }
        if (!format.equals("auto") && !format.equals("json") && !format.equals("jsonl")) {
            DbCommandSupport.error(id, "format must be auto, json or jsonl", "db_import_json");
            return;
        }

        int batchSize = 500;
        if (batchTag != null) {
            try {
                batchSize = Integer.parseInt(batchTag.asString());
            } catch (NumberFormatException e) {
                DbCommandSupport.error(id, "batch_size must be an integer", "db_import_json");
                return;
            }
        }
        if (batchSize < 1 || batchSize > 10_000) {
            DbCommandSupport.error(id, "batch_size must be between 1 and 10000", "db_import_json");
            return;
        }

        long timeoutMs = -1;
        if (timeoutTag != null) {
            timeoutMs = SqlUtils.parseDurationMillis(timeoutTag.asString());
            if (timeoutMs < 0) {
                DbCommandSupport.error(id, "Invalid timeout: " + timeoutTag.asString(), "db_import_json");
                return;
            }
        }

        File file = Settings.resolvePath(path);
        if (!file.isFile()) {
            DbCommandSupport.error(id, "File not found: " + path, "db_import_json");
            return;
        }

        final String finalMode = mode;
        final String finalKey = keyColumn;
        final String finalFormat = format;
        final int finalBatchSize = batchSize;
        final long finalTimeout = timeoutMs;

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            List<Map<String, String>> parsedRows;
            try {
                parsedRows = readRows(file, finalFormat);
            } catch (Exception e) {
                DbCommandSupport.error(id, "Failed to parse JSON: " + e.getMessage(), "db_import_json");
                return;
            }
            if (parsedRows.isEmpty()) {
                DbCommandSupport.executed(id, label, 0, null, 0);
                return;
            }

            List<String> columns = new ArrayList<>(parsedRows.get(0).keySet());
            for (String column : columns) {
                if (!Identifiers.isValid(column)) {
                    DbCommandSupport.error(id, "Invalid column name: " + column, "db_import_json");
                    return;
                }
            }
            if (columns.isEmpty()) {
                DbCommandSupport.error(id, "JSON objects contain no columns", "db_import_json");
                return;
            }
            String keyColumnName = finalKey;
            if (finalMode.equals("upsert")) {
                String match = null;
                for (String column : columns) {
                    if (column.equalsIgnoreCase(finalKey)) {
                        match = column;
                        break;
                    }
                }
                if (match == null) {
                    DbCommandSupport.error(id, "key_column '" + finalKey + "' is not present in the JSON rows", "db_import_json");
                    return;
                }
                keyColumnName = match;
            }
            final String resolvedKey = keyColumnName;

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

            List<String> updateColumns = new ArrayList<>();
            for (String column : columns) {
                if (finalMode.equals("upsert") && column.equalsIgnoreCase(resolvedKey)) continue;
                updateColumns.add(column);
            }

            String sql;
            if (finalMode.equals("insert")) {
                sql = "INSERT INTO " + table + " (" + colPart + ") VALUES (" + placeholders + ")";
            } else if (dbType.equals("sqlite") || dbType.equals("postgresql")) {
                StringBuilder updates = new StringBuilder();
                for (String column : updateColumns) {
                    if (updates.length() > 0) updates.append(",");
                    updates.append(column).append("=EXCLUDED.").append(column);
                }
                sql = "INSERT INTO " + table + " (" + colPart + ") VALUES (" + placeholders + ")"
                    + " ON CONFLICT (" + resolvedKey + ") "
                    + (updates.length() > 0 ? "DO UPDATE SET " + updates : "DO NOTHING");
            } else {
                StringBuilder updates = new StringBuilder();
                for (String column : updateColumns) {
                    if (updates.length() > 0) updates.append(",");
                    updates.append(column).append("=VALUES(").append(column).append(")");
                }
                if (updates.length() == 0) {
                    updates.append(resolvedKey).append("=VALUES(").append(resolvedKey).append(")");
                }
                sql = "INSERT INTO " + table + " (" + colPart + ") VALUES (" + placeholders + ")"
                    + " ON DUPLICATE KEY UPDATE " + updates;
            }

            long start = System.nanoTime();
            Connection conn = null;
            try {
                conn = manager.getConnection(id);
                conn.setAutoCommit(false);

                int total = 0;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    DbCommandSupport.applySettings(ps, finalTimeout);
                    int pending = 0;
                    for (Map<String, String> row : parsedRows) {
                        int index = 1;
                        for (String column : columns) {
                            ps.setString(index++, row.get(column));
                        }
                        ps.addBatch();
                        pending++;
                        if (pending >= finalBatchSize) {
                            total += sum(ps.executeBatch());
                            pending = 0;
                        }
                    }
                    if (pending > 0) {
                        total += sum(ps.executeBatch());
                    }
                }

                conn.commit();
                manager.afterWrite(id);
                long duration = DbCommandSupport.elapsedMs(start);
                manager.recordQuery(id, duration, true, sql);
                DbCommandSupport.executed(id, label, total, null, duration);
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

    private static int sum(int[] results) {
        int total = 0;
        for (int result : results) {
            if (result > 0) total += result;
            else if (result == java.sql.Statement.SUCCESS_NO_INFO) total++;
        }
        return total;
    }

    private static List<Map<String, String>> readRows(File file, String format) throws Exception {
        boolean jsonl = format.equals("jsonl")
            || (format.equals("auto") && (file.getName().endsWith(".jsonl") || file.getName().endsWith(".ndjson")));

        if (jsonl) {
            return readJsonLines(file);
        }

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            JsonElement root = JsonParser.parseReader(reader);
            List<Map<String, String>> rows = new ArrayList<>();

            if (root.isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray()) {
                    if (!element.isJsonObject()) {
                        throw new IllegalArgumentException("array entries must be objects");
                    }
                    rows.add(toRow(element.getAsJsonObject()));
                }
                return rows;
            }
            if (root.isJsonObject()) {
                rows.add(toRow(root.getAsJsonObject()));
                return rows;
            }
            throw new IllegalArgumentException("root must be an object or an array");
        } catch (JsonSyntaxException e) {
            if (format.equals("auto")) {
                return readJsonLines(file);
            }
            throw e;
        }
    }

    private static List<Map<String, String>> readJsonLines(File file) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonElement element = JsonParser.parseString(line);
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("each JSONL line must be an object");
                }
                rows.add(toRow(element.getAsJsonObject()));
            }
        }
        return rows;
    }

    private static Map<String, String> toRow(JsonObject object) {
        Map<String, String> row = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) {
                row.put(entry.getKey(), null);
            } else if (value.isJsonPrimitive()) {
                row.put(entry.getKey(), value.getAsString());
            } else {
                row.put(entry.getKey(), value.toString());
            }
        }
        return row;
    }
}
