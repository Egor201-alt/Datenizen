package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.database.DatabaseManager;
import com.egor201.datenizen.util.Identifiers;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class DbRenameColumnCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_rename_column
    // @Syntax db_rename_column [id:<id>] [table:<table>] [old:<column>] [new:<column>] (label:<label>)
    // @Required 4
    // @Maximum 5
    // @Short Renames a column without writing SQL manually.
    // @Group Datenizen
    //
    // @Description
    // Runs ALTER TABLE ... RENAME COLUMN ... TO ...
    // Requires MySQL 8+, MariaDB 10.5+, PostgreSQL or SQLite 3.25+.
    //
    // @Usage
    // - db_rename_column id:main table:players old:coins new:balance label:rename_coins
    // -->
    public DbRenameColumnCommand() {
        super("db_rename_column",
              "db_rename_column [id:<id>] [table:<table>] [old:<column>] [new:<column>] (label:<label>)",
              4, 5,
              "Must specify id, table, old, and new!",
              ArgSpec.element("id"),
              ArgSpec.element("table"),
              ArgSpec.element("old"),
              ArgSpec.element("new"),
              ArgSpec.optionalElement("label"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id = se.getElement("id").asString();
        String table = se.getElement("table").asString();
        String oldName = se.getElement("old").asString();
        String newName = se.getElement("new").asString();
        String label = se.hasObject("label") ? se.getElement("label").asString() : null;

        if (!Identifiers.isValid(table) || !Identifiers.isValid(oldName) || !Identifiers.isValid(newName)) {
            DbCommandSupport.error(id, "Invalid table or column name", "db_rename_column");
            return;
        }

        String sql = "ALTER TABLE " + table + " RENAME COLUMN " + oldName + " TO " + newName;

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            DatabaseManager manager = Datenizen.getInstance().getDatabaseManager();
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
