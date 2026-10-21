package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbCsvImportedEvent;
import com.egor201.datenizen.events.DbErrorEvent;
import com.egor201.datenizen.util.SqlUtils;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.regex.Pattern;

public class DbImportCsvCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_import_csv
    // @Syntax db_import_csv [id:<id>] [table:<table>] [path:<path>]
    // @Required 3
    // @Maximum 3
    // @Short Imports a CSV file into a database table.
    // @Group Datenizen
    //
    // @Description
    // Reads a CSV file asynchronously and inserts rows into the specified table using a transaction.
    // If any row fails the entire import is rolled back.
    // The first line of the file is treated as the header and used as the INSERT column list.
    // Supports quoted fields containing commas and escaped quotes.
    // Table and column names must be alphanumeric/underscores only.
    // If a CSV row has fewer columns than the header, missing values are inserted as NULL.
    // -->

    private static final Pattern SAFE_NAME = com.egor201.datenizen.util.Identifiers.PATTERN;

    public DbImportCsvCommand() {
        super("db_import_csv", "db_import_csv [id:<id>] [table:<table>] [path:<path>]", 3, 3,
              "Must specify id, table, and path!",
              ArgSpec.element("id"), ArgSpec.element("table"), ArgSpec.element("path"));
    }

    @Override
    public void execute(ScriptEntry scriptEntry) {
        String id    = scriptEntry.getElement("id").asString();
        String table = scriptEntry.getElement("table").asString();
        String path  = scriptEntry.getElement("path").asString();

        if (!SAFE_NAME.matcher(table).matches()) {
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                DbErrorEvent.instance.fireFor(id, "Invalid table name: " + table, null, "CSV IMPORT")
            );
            return;
        }

        File file = com.egor201.datenizen.Settings.resolvePath(path);
        if (!file.exists()) return;

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            Connection conn = null;
            try {
                conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                conn.setAutoCommit(false);

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

                    String headerLine = reader.readLine();
                    if (headerLine == null) return;
                    if (headerLine.startsWith("\uFEFF")) headerLine = headerLine.substring(1);

                    List<String> headers = SqlUtils.parseCsvLine(headerLine);
                    int columnCount = headers.size();

                    StringBuilder columnNames = new StringBuilder();
                    StringBuilder placeholders = new StringBuilder();
                    for (int i = 0; i < columnCount; i++) {
                        String column = headers.get(i).trim();
                        if (!SAFE_NAME.matcher(column).matches()) {
                            final String invalidColumn = column;
                            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                                DbErrorEvent.instance.fireFor(id, "Invalid CSV column name: " + invalidColumn, null, "CSV IMPORT " + table)
                            );
                            return;
                        }
                        if (i > 0) {
                            columnNames.append(",");
                            placeholders.append(",");
                        }
                        columnNames.append(column);
                        placeholders.append("?");
                    }

                    String sql = "INSERT INTO " + table + " (" + columnNames + ") VALUES (" + placeholders + ")";
                    int rowsCount = 0;

                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank()) continue;
                            List<String> values = SqlUtils.parseCsvLine(line);
                            int limit = Math.min(values.size(), columnCount);
                            for (int i = 0; i < limit; i++) {
                                ps.setObject(i + 1, values.get(i));
                            }
                            for (int i = limit; i < columnCount; i++) {
                                ps.setObject(i + 1, null);
                            }
                            ps.addBatch();
                            rowsCount++;
                        }
                        ps.executeBatch();
                    }

                    conn.commit();
                    final int finalRowsCount = rowsCount;
                    Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                        DbCsvImportedEvent.instance.fireFor(id, table, finalRowsCount)
                    );
                }
            } catch (java.sql.SQLException e) {
                if (conn != null) { try { conn.rollback(); } catch (Exception ignored) {} }
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, e.getMessage(), e.getSQLState(), "CSV IMPORT " + table)
                );
            } catch (Exception e) {
                e.printStackTrace();
                if (conn != null) { try { conn.rollback(); } catch (Exception ignored) {} }
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, e.getMessage(), null, "CSV IMPORT " + table)
                );
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(true); } catch (Exception ignored) {}
                    try { conn.close(); } catch (Exception ignored) {}
                }
            }
        });
    }
}
