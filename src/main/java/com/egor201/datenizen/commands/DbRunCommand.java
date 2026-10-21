package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.database.DatabaseManager;
import com.egor201.datenizen.database.ResultMapper;
import com.egor201.datenizen.util.NamedParams;
import com.egor201.datenizen.util.SqlUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DbRunCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_run
    // @Syntax db_run [id:<id>] [name:<name>] (args:<list>) (named_args:<map>) (timeout:<ms>) (label:<label>)
    // @Required 2
    // @Maximum 6
    // @Short Executes a named query registered with db_register.
    // @Group Datenizen
    //
    // @Description
    // Runs a SQL query previously registered with db_register.
    // SELECT queries fire 'db queried'; all others fire 'db executed'.
    // Use either ? placeholders with 'args', or :name placeholders with 'named_args'.
    // Fires 'db error' on failure or if the named query does not exist.
    //
    // @Usage
    // - db_run id:main name:get_player args:<list[<player.uuid>]> label:got_player
    // -->
    public DbRunCommand() {
        super("db_run",
              "db_run [id:<id>] [name:<name>] (args:<list>) (named_args:<map>) (timeout:<ms>) (label:<label>)",
              2, 6,
              "Must specify id and name!",
              ArgSpec.element("id"),
              ArgSpec.element("name"),
              ArgSpec.optionalList("args"),
              ArgSpec.optionalMap("named_args"),
              ArgSpec.optionalElement("timeout"),
              ArgSpec.optionalElement("label"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id = se.getElement("id").asString();
        String name = se.getElement("name").asString();
        ListTag args = se.getObjectTag("args");
        MapTag namedArgs = se.getObjectTag("named_args");
        ElementTag timeoutTag = se.getElement("timeout");
        String label = se.hasObject("label") ? se.getElement("label").asString() : null;

        String sql = Datenizen.getInstance().getDatabaseManager().getNamedQuery(id, name);
        if (sql == null) {
            DbCommandSupport.error(id, "Named query '" + name + "' not found for id '" + id + "'", "db_run");
            return;
        }

        String trimmed = sql.stripLeading();
        boolean isSelect = trimmed.length() >= 6 && trimmed.substring(0, 6).equalsIgnoreCase("SELECT");

        long timeoutMs = -1;
        if (timeoutTag != null) {
            timeoutMs = SqlUtils.parseDurationMillis(timeoutTag.asString());
            if (timeoutMs < 0) {
                DbCommandSupport.error(id, "Invalid timeout: " + timeoutTag.asString(), "db_run");
                return;
            }
        }
        final long finalTimeout = timeoutMs;

        NamedParams.Bound bound;
        try {
            bound = NamedParams.bind(sql, namedArgs);
        } catch (IllegalArgumentException e) {
            DbCommandSupport.error(id, e.getMessage(), "db_run");
            return;
        }

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            long start = System.nanoTime();
            DatabaseManager manager = Datenizen.getInstance().getDatabaseManager();
            try (Connection conn = manager.getConnection(id);
                 PreparedStatement ps = conn.prepareStatement(bound.sql())) {

                DbCommandSupport.applySettings(ps, finalTimeout);
                if (!bound.values().isEmpty()) {
                    ResultMapper.bindValues(ps, bound.values());
                } else {
                    ResultMapper.bind(ps, args);
                }
                DbCommandSupport.log(bound.sql());

                if (isSelect) {
                    try (ResultSet rs = ps.executeQuery()) {
                        ListTag rows = ResultMapper.rows(rs);
                        long duration = DbCommandSupport.elapsedMs(start);
                        manager.recordQuery(id, duration, true, bound.sql());
                        DbCommandSupport.queried(id, label, rows, duration);
                    }
                } else {
                    int affected = ps.executeUpdate();
                    manager.afterWrite(id);
                    long duration = DbCommandSupport.elapsedMs(start);
                    manager.recordQuery(id, duration, true, bound.sql());
                    DbCommandSupport.executed(id, label, affected, null, duration);
                }
            } catch (Exception e) {
                long duration = DbCommandSupport.elapsedMs(start);
                manager.recordQuery(id, duration, false, bound.sql());
                manager.recordError(id, e, bound.sql());
                DbCommandSupport.error(id, e, bound.sql());
            }
        });
    }
}
