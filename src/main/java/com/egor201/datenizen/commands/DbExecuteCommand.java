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

public class DbExecuteCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_execute
    // @Syntax db_execute [id:<id>] [sql:<query>] (args:<list>) (named_args:<map>) (tx:<tx_id>) (timeout:<ms>) (allow_unsafe:<true/false>) (label:<label>)
    // @Required 2
    // @Maximum 8
    // @Short Executes an async SQL update, insert, or delete query.
    // @Group Datenizen
    //
    // @Description
    // Executes a query asynchronously.
    // Use either ? placeholders with 'args', or :name placeholders with 'named_args'.
    // Use 'tx' to execute within a transaction started by db_transaction.
    // Fires 'db executed' on success with <context.affected_rows>, <context.generated_key>
    // and <context.duration_ms>.
    //
    // @Usage
    // - db_execute id:main sql:INSERT INTO players (name) VALUES (?) args:<list[<player.name>]> label:insert_player
    // - db_execute id:main sql:"UPDATE players SET coins=:coins WHERE uuid=:uuid" named_args:<map[coins=10|uuid=abc]> label:add_coins
    // -->
    public DbExecuteCommand() {
        super("db_execute",
              "db_execute [id:<id>] [sql:<query>] (args:<list>) (named_args:<map>) (tx:<tx_id>) (timeout:<ms>) (allow_unsafe:<true/false>) (label:<label>)",
              2, 8,
              "Must specify id and sql!",
              ArgSpec.element("id"),
              ArgSpec.sql("sql"),
              ArgSpec.optionalList("args"),
              ArgSpec.optionalMap("named_args"),
              ArgSpec.optionalElement("tx"),
              ArgSpec.optionalElement("timeout"),
              ArgSpec.optionalElement("allow_unsafe"),
              ArgSpec.optionalElement("label"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id = se.getElement("id").asString();
        String sql = se.getElement("sql").asString();
        ListTag args = se.getObjectTag("args");
        MapTag namedArgs = se.getObjectTag("named_args");
        ElementTag txTag = se.getElement("tx");
        ElementTag timeoutTag = se.getElement("timeout");
        ElementTag unsafeTag = se.getElement("allow_unsafe");
        String txId = txTag != null ? txTag.asString() : null;
        String label = se.hasObject("label") ? se.getElement("label").asString() : null;
        boolean allowUnsafe = unsafeTag != null && unsafeTag.asBoolean();

        if (!DbCommandSupport.allowUnsafe(id, sql, allowUnsafe, "db_execute")) return;

        long timeoutMs = -1;
        if (timeoutTag != null) {
            timeoutMs = SqlUtils.parseDurationMillis(timeoutTag.asString());
            if (timeoutMs < 0) {
                DbCommandSupport.error(id, "Invalid timeout: " + timeoutTag.asString(), "db_execute");
                return;
            }
        }
        final long finalTimeout = timeoutMs;

        NamedParams.Bound bound;
        try {
            bound = NamedParams.bind(sql, namedArgs);
        } catch (IllegalArgumentException e) {
            DbCommandSupport.error(id, e.getMessage(), "db_execute");
            return;
        }

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            long start = System.nanoTime();
            DatabaseManager manager = Datenizen.getInstance().getDatabaseManager();
            Connection conn = null;
            PreparedStatement ps = null;
            try {
                conn = txId != null
                    ? manager.getTransactionConnection(txId)
                    : manager.getConnection(id);

                if (conn == null) {
                    String msg = txId != null
                        ? "Transaction '" + txId + "' not found or already committed/rolled back"
                        : "Database ID '" + id + "' not found";
                    throw new Exception(msg);
                }

                ps = conn.prepareStatement(bound.sql());
                DbCommandSupport.applySettings(ps, finalTimeout);
                if (!bound.values().isEmpty()) {
                    ResultMapper.bindValues(ps, bound.values());
                } else {
                    ResultMapper.bind(ps, args);
                }
                DbCommandSupport.log(bound.sql());

                int affected = ps.executeUpdate();
                manager.afterWrite(id);
                long duration = DbCommandSupport.elapsedMs(start);
                manager.recordQuery(id, duration, true, bound.sql());
                DbCommandSupport.executed(id, label, affected, null, duration);
            } catch (Exception e) {
                long duration = DbCommandSupport.elapsedMs(start);
                manager.recordQuery(id, duration, false, bound.sql());
                manager.recordError(id, e, bound.sql());
                DbCommandSupport.error(id, e, bound.sql());
            } finally {
                try { if (ps != null) ps.close(); } catch (Exception ignored) {}
                try { if (conn != null && txId == null) conn.close(); } catch (Exception ignored) {}
            }
        });
    }
}
