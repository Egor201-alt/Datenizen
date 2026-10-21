package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.database.DatabaseManager;
import com.egor201.datenizen.database.ResultMapper;
import com.egor201.datenizen.util.Identifiers;
import com.egor201.datenizen.util.SqlUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DbTableSelectCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_table_select
    // @Syntax db_table_select [id:<id>] [table:<table>] (columns:<list>) (where:<sql>) (where_args:<list>) (order:<sql>) (limit:<n>) (offset:<n>) (timeout:<ms>) (label:<label>)
    // @Required 2
    // @Maximum 10
    // @Short Selects rows from a table without writing SQL manually.
    // @Group Datenizen
    //
    // @Description
    // Builds and runs a SELECT asynchronously.
    // 'where' is a raw SQL fragment with ? placeholders, bound from 'where_args'.
    // 'order' accepts column names with optional ASC/DESC, separated by commas.
    // Fires 'db queried' with <context.rows> on success.
    //
    // @Usage
    // - db_table_select id:main table:players columns:<list[name|score]> order:score DESC limit:10 label:top_players
    // -->
    public DbTableSelectCommand() {
        super("db_table_select",
              "db_table_select [id:<id>] [table:<table>] (columns:<list>) (where:<sql>) (where_args:<list>) (order:<sql>) (limit:<n>) (offset:<n>) (timeout:<ms>) (label:<label>)",
              2, 10,
              "Must specify id and table!",
              ArgSpec.element("id"),
              ArgSpec.element("table"),
              ArgSpec.optionalList("columns"),
              ArgSpec.optionalElement("where"),
              ArgSpec.optionalList("where_args"),
              ArgSpec.optionalElement("order"),
              ArgSpec.optionalElement("limit"),
              ArgSpec.optionalElement("offset"),
              ArgSpec.optionalElement("timeout"),
              ArgSpec.optionalElement("label"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id = se.getElement("id").asString();
        String table = se.getElement("table").asString();
        ListTag columns = se.getObjectTag("columns");
        ElementTag whereTag = se.getElement("where");
        ListTag whereArgs = se.getObjectTag("where_args");
        ElementTag orderTag = se.getElement("order");
        ElementTag limitTag = se.getElement("limit");
        ElementTag offsetTag = se.getElement("offset");
        ElementTag timeoutTag = se.getElement("timeout");
        String label = se.hasObject("label") ? se.getElement("label").asString() : null;

        if (!Identifiers.isValid(table)) {
            DbCommandSupport.error(id, "Invalid table name: " + table, "db_table_select");
            return;
        }
        if (columns != null) {
            for (String column : columns) {
                if (!Identifiers.isValid(column)) {
                    DbCommandSupport.error(id, "Invalid column name: " + column, "db_table_select");
                    return;
                }
            }
        }

        String where = whereTag != null && !whereTag.asString().isBlank() ? whereTag.asString() : null;
        String order = orderTag != null && !orderTag.asString().isBlank() ? orderTag.asString() : null;
        if (order != null && !SqlUtils.isSafeOrder(order)) {
            DbCommandSupport.error(id, "Invalid order clause: " + order, "db_table_select");
            return;
        }

        Integer limit = null;
        Integer offset = null;
        try {
            if (limitTag != null) limit = Integer.parseInt(limitTag.asString());
            if (offsetTag != null) offset = Integer.parseInt(offsetTag.asString());
        } catch (NumberFormatException e) {
            DbCommandSupport.error(id, "limit and offset must be integers", "db_table_select");
            return;
        }
        if ((limit != null && limit < 0) || (offset != null && offset < 0)) {
            DbCommandSupport.error(id, "limit and offset must not be negative", "db_table_select");
            return;
        }

        long timeoutMs = -1;
        if (timeoutTag != null) {
            timeoutMs = SqlUtils.parseDurationMillis(timeoutTag.asString());
            if (timeoutMs < 0) {
                DbCommandSupport.error(id, "Invalid timeout: " + timeoutTag.asString(), "db_table_select");
                return;
            }
        }

        String sql = SqlUtils.buildSelect(table, columns, where, order, limit, offset);
        final long finalTimeout = timeoutMs;

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            long start = System.nanoTime();
            DatabaseManager manager = Datenizen.getInstance().getDatabaseManager();
            try (Connection conn = manager.getConnection(id);
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ResultMapper.bind(ps, whereArgs);
                DbCommandSupport.applySettings(ps, finalTimeout);
                DbCommandSupport.log(sql);

                try (ResultSet rs = ps.executeQuery()) {
                    ListTag rows = ResultMapper.rows(rs);
                    long duration = DbCommandSupport.elapsedMs(start);
                    manager.recordQuery(id, duration, true, sql);
                    DbCommandSupport.queried(id, label, rows, duration);
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
