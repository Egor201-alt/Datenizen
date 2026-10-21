package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.database.DatabaseManager;
import com.egor201.datenizen.util.Identifiers;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class DbTruncateCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_truncate
    // @Syntax db_truncate [id:<id>] [table:<table>] (label:<label>)
    // @Required 2
    // @Maximum 3
    // @Short Removes all rows from a table.
    // @Group Datenizen
    //
    // @Description
    // Uses TRUNCATE TABLE on MySQL/MariaDB/PostgreSQL and DELETE FROM on SQLite.
    // Fires 'db executed' on success.
    //
    // @Usage
    // - db_truncate id:main table:temp_players label:clear_temp
    // -->
    public DbTruncateCommand() {
        super("db_truncate",
              "db_truncate [id:<id>] [table:<table>] (label:<label>)",
              2, 3,
              "Must specify id and table!",
              ArgSpec.element("id"),
              ArgSpec.element("table"),
              ArgSpec.optionalElement("label"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id = se.getElement("id").asString();
        String table = se.getElement("table").asString();
        String label = se.hasObject("label") ? se.getElement("label").asString() : null;

        if (!Identifiers.isValid(table)) {
            DbCommandSupport.error(id, "Invalid table name: " + table, "db_truncate");
            return;
        }

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            DatabaseManager manager = Datenizen.getInstance().getDatabaseManager();
            String type = manager.getDatabaseType(id);
            String sql = type.equals("sqlite") ? "DELETE FROM " + table : "TRUNCATE TABLE " + table;
            long start = System.nanoTime();

            try (Connection conn = manager.getConnection(id);
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                DbCommandSupport.applySettings(ps, -1);
                DbCommandSupport.log(sql);
                int affected = ps.executeUpdate();

                manager.afterWrite(id);
                long duration = DbCommandSupport.elapsedMs(start);
                manager.recordQuery(id, duration, true, sql);
                DbCommandSupport.executed(id, label, affected, null, duration);
            } catch (Exception e) {
                long duration = DbCommandSupport.elapsedMs(start);
                manager.recordQuery(id, duration, false, sql);
                manager.recordError(id, e, sql);
                DbCommandSupport.error(id, e, sql);
            }
        });
    }
}
