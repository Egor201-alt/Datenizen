package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.database.DatabaseManager;
import com.egor201.datenizen.util.SqlUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DbUpsertCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_upsert
    // @Syntax db_upsert [id:<id>] [table:<table>] [key_column:<col>] [key_value:<val>] [set:<list>] (returning:<column>) (timeout:<ms>) (label:<label>)
    // @Required 5
    // @Maximum 8
    // @Short Insert or update a row without writing SQL manually.
    // @Group Datenizen
    //
    // @Description
    // Builds INSERT ... ON CONFLICT DO UPDATE SET col=EXCLUDED.col (SQLite/PostgreSQL)
    // or INSERT ... ON DUPLICATE KEY UPDATE (MySQL/MariaDB).
    // 'set' is a ListTag of "column=value" pairs. The key column must have a UNIQUE or PRIMARY KEY constraint.
    // Column names must be alphanumeric/underscores only. Values are safely parameterized.
    // 'returning' reads the inserted value back where the database supports it.
    // Fires 'db executed' on success with <context.affected_rows> and <context.generated_key>.
    //
    // @Usage
    // - db_upsert id:main table:players key_column:uuid key_value:<player.uuid> set:<list[name=<player.name>|coins=0]> label:save_player
    // -->
    public DbUpsertCommand() {
        super("db_upsert",
              "db_upsert [id:<id>] [table:<table>] [key_column:<col>] [key_value:<val>] [set:<list>] (returning:<column>) (timeout:<ms>) (label:<label>)",
              5, 8,
              "Must specify id, table, key_column, key_value, and set!",
              ArgSpec.element("id"),
              ArgSpec.element("table"),
              ArgSpec.element("key_column"),
              ArgSpec.element("key_value"),
              ArgSpec.list("set"),
              ArgSpec.optionalElement("returning"),
              ArgSpec.optionalElement("timeout"),
              ArgSpec.optionalElement("label"));
    }

    private record Pair(String col, String val) {}

    @Override
    public void execute(ScriptEntry se) {
        String id       = se.getElement("id").asString();
        String table    = se.getElement("table").asString();
        String keyCol   = se.getElement("key_column").asString();
        String keyVal   = se.getElement("key_value").asString();
        ListTag setList = se.getObjectTag("set");
        ElementTag returningTag = se.getElement("returning");
        ElementTag timeoutTag = se.getElement("timeout");
        String label    = se.hasObject("label") ? se.getElement("label").asString() : null;

        if (!com.egor201.datenizen.util.Identifiers.isValid(table)
            || !com.egor201.datenizen.util.Identifiers.isValid(keyCol)) {
            DbCommandSupport.error(id, "Invalid table or key_column name", "db_upsert");
            return;
        }

        List<Pair> pairs = new ArrayList<>();
        for (String entry : setList) {
            int eq = entry.indexOf('=');
            if (eq < 1) {
                DbCommandSupport.error(id, "Invalid set entry: " + entry, "db_upsert");
                return;
            }
            String col = entry.substring(0, eq).trim();
            String val = entry.substring(eq + 1);
            if (!com.egor201.datenizen.util.Identifiers.isValid(col)) {
                DbCommandSupport.error(id, "Invalid column name: " + col, "db_upsert");
                return;
            }
            pairs.add(new Pair(col, val));
        }

        if (pairs.isEmpty()) {
            DbCommandSupport.error(id, "set list must not be empty", "db_upsert");
            return;
        }

        String returning = returningTag != null && !returningTag.asString().isBlank()
            ? returningTag.asString()
            : null;
        if (returning != null && !com.egor201.datenizen.util.Identifiers.isValid(returning)) {
            DbCommandSupport.error(id, "Invalid returning column: " + returning, "db_upsert");
            return;
        }

        long timeoutMs = -1;
        if (timeoutTag != null) {
            timeoutMs = SqlUtils.parseDurationMillis(timeoutTag.asString());
            if (timeoutMs < 0) {
                DbCommandSupport.error(id, "Invalid timeout: " + timeoutTag.asString(), "db_upsert");
                return;
            }
        }
        final long finalTimeout = timeoutMs;

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            DatabaseManager manager = Datenizen.getInstance().getDatabaseManager();
            String dbType = manager.getDatabaseType(id);
            String baseSql = buildSql(dbType, table, keyCol, pairs);
            boolean useReturningClause = returning != null
                && (dbType.equals("sqlite") || dbType.equals("postgresql"));
            String sql = useReturningClause ? baseSql + " RETURNING " + returning : baseSql;

            long start = System.nanoTime();
            try (Connection conn = manager.getConnection(id)) {
                PreparedStatement ps = useReturningClause
                    ? conn.prepareStatement(sql)
                    : conn.prepareStatement(sql, returning != null ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS);

                try {
                    DbCommandSupport.applySettings(ps, finalTimeout);

                    int idx = 1;
                    ps.setObject(idx++, keyVal);
                    for (Pair p : pairs) ps.setObject(idx++, p.val());
                    if (dbType.equals("mysql") || dbType.equals("mariadb")) {
                        for (Pair p : pairs) ps.setObject(idx++, p.val());
                    }
                    DbCommandSupport.log(sql);

                    int affected;
                    String generatedKey = null;

                    if (useReturningClause) {
                        try (ResultSet rs = ps.executeQuery()) {
                            affected = rs.next() ? 1 : 0;
                            if (affected > 0) generatedKey = rs.getString(1);
                        }
                    } else {
                        affected = ps.executeUpdate();
                        if (returning != null) {
                            try (ResultSet keys = ps.getGeneratedKeys()) {
                                if (keys.next()) generatedKey = keys.getString(1);
                            }
                        }
                    }

                    manager.afterWrite(id);
                    long duration = DbCommandSupport.elapsedMs(start);
                    manager.recordQuery(id, duration, true, sql);
                    DbCommandSupport.executed(id, label, affected, generatedKey, duration);
                } finally {
                    try { ps.close(); } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                long duration = DbCommandSupport.elapsedMs(start);
                manager.recordQuery(id, duration, false, sql);
                manager.recordError(id, e, sql);
                DbCommandSupport.error(id, e, sql);
            }
        });
    }

    private String buildSql(String dbType, String table, String keyCol, List<Pair> pairs) {
        StringBuilder cols = new StringBuilder(keyCol);
        StringBuilder placeholders = new StringBuilder("?");
        StringBuilder updatePart = new StringBuilder();

        for (int i = 0; i < pairs.size(); i++) {
            cols.append(",").append(pairs.get(i).col());
            placeholders.append(",?");
            if (i > 0) updatePart.append(",");
            updatePart.append(pairs.get(i).col());
            if (dbType.equals("postgresql") || dbType.equals("sqlite")) {
                updatePart.append("=EXCLUDED.").append(pairs.get(i).col());
            } else {
                updatePart.append("=?");
            }
        }

        return switch (dbType) {
            case "sqlite", "postgresql" ->
                "INSERT INTO " + table + " (" + cols + ") VALUES (" + placeholders + ")" +
                " ON CONFLICT (" + keyCol + ") DO UPDATE SET " + updatePart;
            default ->
                "INSERT INTO " + table + " (" + cols + ") VALUES (" + placeholders + ")" +
                " ON DUPLICATE KEY UPDATE " + updatePart;
        };
    }
}
