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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DbTableInsertCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_table_insert
    // @Syntax db_table_insert [id:<id>] [table:<table>] (columns:<list>) (values:<list>) (row:<map>) (returning:<column>) (timeout:<ms>) (label:<label>)
    // @Required 2
    // @Maximum 8
    // @Short Inserts a row into a table without writing SQL manually.
    // @Group Datenizen
    //
    // @Description
    // Builds and executes an INSERT asynchronously.
    // Use either columns+values, or a single row:<map>.
    // 'returning' reads the inserted value back: RETURNING on SQLite/PostgreSQL,
    // getGeneratedKeys() on MySQL/MariaDB.
    // Fires 'db executed' with <context.affected_rows> and <context.generated_key>.
    //
    // @Usage
    // - db_table_insert id:main table:players columns:<list[name|uuid|score]> values:<list[Steve|abc-123|0]> label:new_player
    // - db_table_insert id:main table:players row:<map[name=Steve|uuid=abc-123|score=0]> returning:id label:new_player
    // -->
    public DbTableInsertCommand() {
        super("db_table_insert",
              "db_table_insert [id:<id>] [table:<table>] (columns:<list>) (values:<list>) (row:<map>) (returning:<column>) (timeout:<ms>) (label:<label>)",
              2, 8,
              "Must specify id and table!",
              ArgSpec.element("id"),
              ArgSpec.element("table"),
              ArgSpec.optionalList("columns"),
              ArgSpec.optionalList("values"),
              ArgSpec.optionalMap("row"),
              ArgSpec.optionalElement("returning"),
              ArgSpec.optionalElement("timeout"),
              ArgSpec.optionalElement("label"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id = se.getElement("id").asString();
        String table = se.getElement("table").asString();
        ListTag columns = se.getObjectTag("columns");
        ListTag values = se.getObjectTag("values");
        MapTag row = se.getObjectTag("row");
        ElementTag returningTag = se.getElement("returning");
        ElementTag timeoutTag = se.getElement("timeout");
        String label = se.hasObject("label") ? se.getElement("label").asString() : null;

        if (!Identifiers.isValid(table)) {
            DbCommandSupport.error(id, "Invalid table name: " + table, "db_table_insert");
            return;
        }

        List<String> colList = new ArrayList<>();
        List<ObjectTag> valueList = new ArrayList<>();

        if (row != null) {
            if (columns != null || values != null) {
                DbCommandSupport.error(id, "Use either row or columns+values, not both", "db_table_insert");
                return;
            }
            for (StringHolder holder : row.map.keySet()) {
                if (!Identifiers.isValid(holder.str)) {
                    DbCommandSupport.error(id, "Invalid column name: " + holder.str, "db_table_insert");
                    return;
                }
                colList.add(holder.str);
                valueList.add(row.map.get(holder));
            }
            if (colList.isEmpty()) {
                DbCommandSupport.error(id, "row must contain at least one column", "db_table_insert");
                return;
            }
        } else {
            if (columns == null || values == null) {
                DbCommandSupport.error(id, "Must specify either row or columns+values", "db_table_insert");
                return;
            }
            if (columns.size() != values.size()) {
                DbCommandSupport.error(id, "columns and values lists must be the same size", "db_table_insert");
                return;
            }
            if (columns.isEmpty()) {
                DbCommandSupport.error(id, "columns and values must not be empty", "db_table_insert");
                return;
            }
            for (String column : columns) {
                if (!Identifiers.isValid(column)) {
                    DbCommandSupport.error(id, "Invalid column name: " + column, "db_table_insert");
                    return;
                }
                colList.add(column);
            }
            for (int i = 0; i < values.size(); i++) {
                valueList.add(new ElementTag(values.get(i)));
            }
        }

        String returning = returningTag != null && !returningTag.asString().isBlank()
            ? returningTag.asString()
            : null;
        if (returning != null && !Identifiers.isValid(returning)) {
            DbCommandSupport.error(id, "Invalid returning column: " + returning, "db_table_insert");
            return;
        }

        long timeoutMs = -1;
        if (timeoutTag != null) {
            timeoutMs = SqlUtils.parseDurationMillis(timeoutTag.asString());
            if (timeoutMs < 0) {
                DbCommandSupport.error(id, "Invalid timeout: " + timeoutTag.asString(), "db_table_insert");
                return;
            }
        }
        final long finalTimeout = timeoutMs;

        StringBuilder colPart = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < colList.size(); i++) {
            if (i > 0) {
                colPart.append(",");
                placeholders.append(",");
            }
            colPart.append(colList.get(i));
            placeholders.append("?");
        }
        String baseSql = "INSERT INTO " + table + " (" + colPart + ") VALUES (" + placeholders + ")";

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            DatabaseManager manager = Datenizen.getInstance().getDatabaseManager();
            String dbType = manager.getDatabaseType(id);
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
                    for (int i = 0; i < valueList.size(); i++) {
                        ObjectTag value = valueList.get(i);
                        ps.setString(i + 1, value != null ? value.toString() : null);
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
}
