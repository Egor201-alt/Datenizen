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

public class DbExecuteIfCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_execute_if
    // @Syntax db_execute_if [id:<id>] [check:<sql>] (check_args:<list>) [sql:<sql>] (args:<list>) (named_args:<map>) (timeout:<ms>) (allow_unsafe:<true/false>) (label:<label>)
    // @Required 3
    // @Maximum 9
    // @Short Runs SQL only if a check query returns at least one row.
    // @Group Datenizen
    //
    // @Description
    // Runs the 'check' SELECT first. If it returns at least one row, executes 'sql'.
    // If the check returns no rows, fires 'db executed' with affected_rows=0 (skipped).
    // Both queries run on the same connection in a single async task.
    // 'named_args' applies to the main 'sql' statement.
    // Fires 'db error' on any failure.
    //
    // @Usage
    // - db_execute_if id:main check:"SELECT 1 FROM players WHERE uuid=?" check_args:<list[<player.uuid>]> sql:"UPDATE players SET online=1 WHERE uuid=?" args:<list[<player.uuid>]> label:mark_online
    // -->
    public DbExecuteIfCommand() {
        super("db_execute_if",
              "db_execute_if [id:<id>] [check:<sql>] (check_args:<list>) [sql:<sql>] (args:<list>) (named_args:<map>) (timeout:<ms>) (allow_unsafe:<true/false>) (label:<label>)",
              3, 9,
              "Must specify id, check, and sql!",
              ArgSpec.element("id"),
              ArgSpec.element("check"),
              ArgSpec.optionalList("check_args"),
              ArgSpec.element("sql"),
              ArgSpec.optionalList("args"),
              ArgSpec.optionalMap("named_args"),
              ArgSpec.optionalElement("timeout"),
              ArgSpec.optionalElement("allow_unsafe"),
              ArgSpec.optionalElement("label"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id = se.getElement("id").asString();
        String checkSql = se.getElement("check").asString();
        ListTag checkArgs = se.getObjectTag("check_args");
        String sql = se.getElement("sql").asString();
        ListTag args = se.getObjectTag("args");
        MapTag namedArgs = se.getObjectTag("named_args");
        ElementTag timeoutTag = se.getElement("timeout");
        ElementTag unsafeTag = se.getElement("allow_unsafe");
        String label = se.hasObject("label") ? se.getElement("label").asString() : null;
        boolean allowUnsafe = unsafeTag != null && unsafeTag.asBoolean();

        if (!DbCommandSupport.allowUnsafe(id, sql, allowUnsafe, "db_execute_if")) return;

        long timeoutMs = -1;
        if (timeoutTag != null) {
            timeoutMs = SqlUtils.parseDurationMillis(timeoutTag.asString());
            if (timeoutMs < 0) {
                DbCommandSupport.error(id, "Invalid timeout: " + timeoutTag.asString(), "db_execute_if");
                return;
            }
        }
        final long finalTimeout = timeoutMs;

        NamedParams.Bound bound;
        try {
            bound = NamedParams.bind(sql, namedArgs);
        } catch (IllegalArgumentException e) {
            DbCommandSupport.error(id, e.getMessage(), "db_execute_if");
            return;
        }

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            long start = System.nanoTime();
            DatabaseManager manager = Datenizen.getInstance().getDatabaseManager();
            try (Connection conn = manager.getConnection(id)) {
                boolean hasRows;
                try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    DbCommandSupport.applySettings(ps, finalTimeout);
                    ResultMapper.bind(ps, checkArgs);
                    try (ResultSet rs = ps.executeQuery()) {
                        hasRows = rs.next();
                    }
                }

                if (!hasRows) {
                    long skippedDuration = DbCommandSupport.elapsedMs(start);
                    manager.recordQuery(id, skippedDuration, true, checkSql);
                    DbCommandSupport.executed(id, label, 0, null, skippedDuration);
                    return;
                }

                int affected;
                try (PreparedStatement ps = conn.prepareStatement(bound.sql())) {
                    DbCommandSupport.applySettings(ps, finalTimeout);
                    if (!bound.values().isEmpty()) {
                        ResultMapper.bindValues(ps, bound.values());
                    } else {
                        ResultMapper.bind(ps, args);
                    }
                    DbCommandSupport.log(bound.sql());
                    affected = ps.executeUpdate();
                }

                manager.afterWrite(id);
                long duration = DbCommandSupport.elapsedMs(start);
                manager.recordQuery(id, duration, true, bound.sql());
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
