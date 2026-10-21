package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.database.DatabaseManager;
import com.egor201.datenizen.util.SqlUtils;
import com.egor201.datenizen.util.Tables;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class DbKvSetCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_kv_set
    // @Syntax db_kv_set [id:<id>] [key:<key>] [value:<value>] (ttl:<duration>) (label:<label>)
    // @Required 3
    // @Maximum 5
    // @Short Stores a value in the internal KV table.
    // @Group Datenizen
    //
    // @Description
    // Creates datenizen_kv if needed and inserts/updates a value.
    // ttl accepts 500ms, 30s, 15m, 2h or 7d. Omit it for a permanent value.
    // Read it back with <db_kv_get[id].key[...]>.
    //
    // @Usage
    // - db_kv_set id:main key:motd value:"Hello!" ttl:1d label:set_motd
    // -->
    public DbKvSetCommand() {
        super("db_kv_set",
              "db_kv_set [id:<id>] [key:<key>] [value:<value>] (ttl:<duration>) (label:<label>)",
              3, 5,
              "Must specify id, key, and value!",
              ArgSpec.element("id"),
              ArgSpec.element("key"),
              ArgSpec.element("value"),
              ArgSpec.optionalElement("ttl"),
              ArgSpec.optionalElement("label"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id = se.getElement("id").asString();
        String key = se.getElement("key").asString();
        String value = se.getElement("value").asString();
        ElementTag ttlTag = se.getElement("ttl");
        String label = se.hasObject("label") ? se.getElement("label").asString() : null;

        long ttlMs = 0;
        if (ttlTag != null) {
            ttlMs = SqlUtils.parseDurationMillis(ttlTag.asString());
            if (ttlMs < 0) {
                DbCommandSupport.error(id, "Invalid ttl: " + ttlTag.asString(), "db_kv_set");
                return;
            }
        }
        final long expiresAt = ttlMs > 0 ? System.currentTimeMillis() + ttlMs : 0L;

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            DatabaseManager manager = Datenizen.getInstance().getDatabaseManager();
            String type = manager.getDatabaseType(id);
            String createSql = "CREATE TABLE IF NOT EXISTS " + Tables.KV
                + " (k VARCHAR(255) PRIMARY KEY, v TEXT, expires_at BIGINT)";
            String sql = (type.equals("sqlite") || type.equals("postgresql"))
                ? "INSERT INTO " + Tables.KV + " (k, v, expires_at) VALUES (?, ?, ?)"
                    + " ON CONFLICT (k) DO UPDATE SET v=EXCLUDED.v, expires_at=EXCLUDED.expires_at"
                : "INSERT INTO " + Tables.KV + " (k, v, expires_at) VALUES (?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE v=VALUES(v), expires_at=VALUES(expires_at)";

            long start = System.nanoTime();
            try (Connection conn = manager.getConnection(id)) {
                try (PreparedStatement create = conn.prepareStatement(createSql)) {
                    create.execute();
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, key);
                    ps.setString(2, value);
                    ps.setLong(3, expiresAt);
                    ps.executeUpdate();
                }
                manager.afterWrite(id);
                long duration = DbCommandSupport.elapsedMs(start);
                manager.recordQuery(id, duration, true, sql);
                DbCommandSupport.executed(id, label, 1, null, duration);
            } catch (Exception e) {
                long duration = DbCommandSupport.elapsedMs(start);
                manager.recordQuery(id, duration, false, sql);
                manager.recordError(id, e, sql);
                DbCommandSupport.error(id, e, sql);
            }
        });
    }
}
