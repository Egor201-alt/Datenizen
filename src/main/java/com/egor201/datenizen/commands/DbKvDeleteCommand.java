package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.database.DatabaseManager;
import com.egor201.datenizen.util.Tables;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class DbKvDeleteCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_kv_delete
    // @Syntax db_kv_delete [id:<id>] [key:<key>] (label:<label>)
    // @Required 2
    // @Maximum 3
    // @Short Deletes a value from the internal KV table.
    // @Group Datenizen
    //
    // @Description
    // Deletes the key from datenizen_kv. Missing keys are ignored.
    //
    // @Usage
    // - db_kv_delete id:main key:motd label:clear_motd
    // -->
    public DbKvDeleteCommand() {
        super("db_kv_delete",
              "db_kv_delete [id:<id>] [key:<key>] (label:<label>)",
              2, 3,
              "Must specify id and key!",
              ArgSpec.element("id"),
              ArgSpec.element("key"),
              ArgSpec.optionalElement("label"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id = se.getElement("id").asString();
        String key = se.getElement("key").asString();
        String label = se.hasObject("label") ? se.getElement("label").asString() : null;

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            DatabaseManager manager = Datenizen.getInstance().getDatabaseManager();
            String createSql = "CREATE TABLE IF NOT EXISTS " + Tables.KV
                + " (k VARCHAR(255) PRIMARY KEY, v TEXT, expires_at BIGINT)";
            String sql = "DELETE FROM " + Tables.KV + " WHERE k=?";

            long start = System.nanoTime();
            try (Connection conn = manager.getConnection(id)) {
                try (PreparedStatement create = conn.prepareStatement(createSql)) {
                    create.execute();
                }
                int affected;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, key);
                    affected = ps.executeUpdate();
                }
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
