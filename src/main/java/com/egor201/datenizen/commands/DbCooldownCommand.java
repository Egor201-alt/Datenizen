package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.database.DatabaseManager;
import com.egor201.datenizen.util.SqlUtils;
import com.egor201.datenizen.util.Tables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DbCooldownCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_cooldown
    // @Syntax db_cooldown [id:<id>] [key:<key>] [duration:<duration>] (label:<label>)
    // @Required 3
    // @Maximum 4
    // @Short Checks and starts a persistent cooldown.
    // @Group Datenizen
    //
    // @Description
    // If the key is ready, starts a new cooldown and fires 'db executed' with affected_rows=1.
    // If it is still cooling down, fires 'db executed' with affected_rows=0.
    // Use <db_cooldown_remaining[id].key[...]> to show the remaining time.
    // duration accepts 500ms, 30s, 15m, 2h or 7d.
    //
    // @Usage
    // - db_cooldown id:main key:daily_<player.uuid> duration:1d label:daily
    // on db executed label:daily:
    //   - if <context.affected_rows> == 1:
    //       - narrate "Daily reward claimed!"
    // -->
    public DbCooldownCommand() {
        super("db_cooldown",
              "db_cooldown [id:<id>] [key:<key>] [duration:<duration>] (label:<label>)",
              3, 4,
              "Must specify id, key, and duration!",
              ArgSpec.element("id"),
              ArgSpec.element("key"),
              ArgSpec.element("duration"),
              ArgSpec.optionalElement("label"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id = se.getElement("id").asString();
        String key = se.getElement("key").asString();
        ElementTag durationTag = se.getElement("duration");
        String label = se.hasObject("label") ? se.getElement("label").asString() : null;

        long durationMs = SqlUtils.parseDurationMillis(durationTag.asString());
        if (durationMs <= 0) {
            DbCommandSupport.error(id, "Invalid duration: " + durationTag.asString(), "db_cooldown");
            return;
        }

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            DatabaseManager manager = Datenizen.getInstance().getDatabaseManager();
            String createSql = "CREATE TABLE IF NOT EXISTS " + Tables.COOLDOWNS
                + " (k VARCHAR(255) PRIMARY KEY, expires_at BIGINT)";
            String selectSql = "SELECT expires_at FROM " + Tables.COOLDOWNS + " WHERE k=?";
            String upsertSql = manager.getDatabaseType(id).equals("mysql") || manager.getDatabaseType(id).equals("mariadb")
                ? "INSERT INTO " + Tables.COOLDOWNS + " (k, expires_at) VALUES (?, ?)"
                    + " ON DUPLICATE KEY UPDATE expires_at=VALUES(expires_at)"
                : "INSERT INTO " + Tables.COOLDOWNS + " (k, expires_at) VALUES (?, ?)"
                    + " ON CONFLICT (k) DO UPDATE SET expires_at=EXCLUDED.expires_at";

            long start = System.nanoTime();
            try (Connection conn = manager.getConnection(id)) {
                try (PreparedStatement create = conn.prepareStatement(createSql)) {
                    create.execute();
                }

                long now = System.currentTimeMillis();
                long existing = 0;
                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setString(1, key);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) existing = rs.getLong(1);
                    }
                }

                boolean available = existing <= now;
                if (available) {
                    try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                        ps.setString(1, key);
                        ps.setLong(2, now + durationMs);
                        ps.executeUpdate();
                    }
                    manager.afterWrite(id);
                }

                long duration = DbCommandSupport.elapsedMs(start);
                manager.recordQuery(id, duration, true, upsertSql);
                DbCommandSupport.executed(id, label, available ? 1 : 0, null, duration);
            } catch (Exception e) {
                long duration = DbCommandSupport.elapsedMs(start);
                manager.recordQuery(id, duration, false, upsertSql);
                manager.recordError(id, e, upsertSql);
                DbCommandSupport.error(id, e, upsertSql);
            }
        });
    }
}
