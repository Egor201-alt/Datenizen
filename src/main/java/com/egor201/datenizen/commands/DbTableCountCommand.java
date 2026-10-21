package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.database.DatabaseManager;
import com.egor201.datenizen.database.ResultMapper;
import com.egor201.datenizen.util.Identifiers;
import com.egor201.datenizen.util.SqlUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DbTableCountCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_table_count
    // @Syntax db_table_count [id:<id>] [table:<table>] (where:<sql>) (where_args:<list>) (timeout:<ms>) (label:<label>)
    // @Required 2
    // @Maximum 6
    // @Short Counts rows in a table, optionally with a WHERE clause.
    // @Group Datenizen
    //
    // @Description
    // Runs SELECT COUNT(*) asynchronously.
    // Fires 'db queried' with one row containing the 'count' key.
    //
    // @Usage
    // - db_table_count id:main table:players where:"score > ?" where_args:<list[100]> label:rich_players
    // -->
    public DbTableCountCommand() {
        super("db_table_count",
              "db_table_count [id:<id>] [table:<table>] (where:<sql>) (where_args:<list>) (timeout:<ms>) (label:<label>)",
              2, 6,
              "Must specify id and table!",
              ArgSpec.element("id"),
              ArgSpec.element("table"),
              ArgSpec.optionalElement("where"),
              ArgSpec.optionalList("where_args"),
              ArgSpec.optionalElement("timeout"),
              ArgSpec.optionalElement("label"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id = se.getElement("id").asString();
        String table = se.getElement("table").asString();
        ElementTag whereTag = se.getElement("where");
        ListTag whereArgs = se.getObjectTag("where_args");
        ElementTag timeoutTag = se.getElement("timeout");
        String label = se.hasObject("label") ? se.getElement("label").asString() : null;

        if (!Identifiers.isValid(table)) {
            DbCommandSupport.error(id, "Invalid table name: " + table, "db_table_count");
            return;
        }

        String where = whereTag != null && !whereTag.asString().isBlank() ? whereTag.asString() : null;
        String sql = "SELECT COUNT(*) FROM " + table + (where != null ? " WHERE " + where : "");

        long timeoutMs = -1;
        if (timeoutTag != null) {
            timeoutMs = SqlUtils.parseDurationMillis(timeoutTag.asString());
            if (timeoutMs < 0) {
                DbCommandSupport.error(id, "Invalid timeout: " + timeoutTag.asString(), "db_table_count");
                return;
            }
        }
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
                    ElementTag count = ResultMapper.firstValue(rs);
                    MapTag row = new MapTag();
                    row.putObject("count", count != null ? count : new ElementTag(0));
                    ListTag rows = new ListTag();
                    rows.addObject(row);

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
