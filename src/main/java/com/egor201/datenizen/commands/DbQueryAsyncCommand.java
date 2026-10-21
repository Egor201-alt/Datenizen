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

public class DbQueryAsyncCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_query_async
    // @Syntax db_query_async [id:<id>] [sql:<query>] (args:<list>) (named_args:<map>) (timeout:<ms>) (label:<label>)
    // @Required 2
    // @Maximum 6
    // @Short Runs a SELECT query asynchronously and fires 'db queried' with results.
    // @Group Datenizen
    //
    // @Description
    // Executes a SELECT query asynchronously.
    // Fires 'db queried' with <context.rows> and <context.duration_ms> on success.
    // Use either ? placeholders with 'args', or :name placeholders with 'named_args'.
    //
    // @Usage
    // - db_query_async id:main sql:"SELECT * FROM players WHERE active=1" label:load_all
    // -->
    public DbQueryAsyncCommand() {
        super("db_query_async",
              "db_query_async [id:<id>] [sql:<query>] (args:<list>) (named_args:<map>) (timeout:<ms>) (label:<label>)",
              2, 6,
              "Must specify id and sql!",
              ArgSpec.element("id"),
              ArgSpec.element("sql"),
              ArgSpec.optionalList("args"),
              ArgSpec.optionalMap("named_args"),
              ArgSpec.optionalElement("timeout"),
              ArgSpec.optionalElement("label"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id = se.getElement("id").asString();
        String sql = se.getElement("sql").asString();
        ListTag args = se.getObjectTag("args");
        MapTag namedArgs = se.getObjectTag("named_args");
        ElementTag timeoutTag = se.getElement("timeout");
        String label = se.hasObject("label") ? se.getElement("label").asString() : null;

        long timeoutMs = -1;
        if (timeoutTag != null) {
            timeoutMs = SqlUtils.parseDurationMillis(timeoutTag.asString());
            if (timeoutMs < 0) {
                DbCommandSupport.error(id, "Invalid timeout: " + timeoutTag.asString(), "db_query_async");
                return;
            }
        }
        final long finalTimeout = timeoutMs;

        NamedParams.Bound bound;
        try {
            bound = NamedParams.bind(sql, namedArgs);
        } catch (IllegalArgumentException e) {
            DbCommandSupport.error(id, e.getMessage(), "db_query_async");
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

                try (ResultSet rs = ps.executeQuery()) {
                    ListTag rows = ResultMapper.rows(rs);
                    long duration = DbCommandSupport.elapsedMs(start);
                    manager.recordQuery(id, duration, true, bound.sql());
                    DbCommandSupport.queried(id, label, rows, duration);
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
