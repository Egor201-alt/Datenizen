package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbErrorEvent;
import com.egor201.datenizen.events.DbExecutedEvent;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class DbExecuteCommand extends AbstractCommand {

    // <--[command]
    // @Name db_execute
    // @Syntax db_execute [id:<id>] [sql:<query>] (args:<list>) (tx:<tx_id>) (label:<label>)
    // @Required 2
    // @Maximum 5
    // @Short Executes an async SQL update, insert, or delete query.
    // @Group Datenizen
    //
    // @Description
    // Executes a query asynchronously.
    // The sql argument supports both prefixed and quoted forms:
    //   sql:UPDATE players SET coins=? WHERE uuid=?
    //   "sql:UPDATE players SET coins=? WHERE uuid=?"
    // Use 'tx' to execute within a specific transaction started by db_transaction.
    // Use 'label' to identify the operation in the 'db executed' event via <context.label>.
    // Fires 'db executed' on success with <context.affected_rows>.
    //
    // @Usage
    // - db_execute id:main sql:INSERT INTO players (name) VALUES (?) args:<list[<player.name>]> label:insert_player
    // - db_execute id:main "sql:UPDATE players SET coins = coins - ? WHERE uuid = ?" args:<list[<[amount]>|<player.uuid>]>
    // -->

    public DbExecuteCommand() {
        setName("db_execute");
        setSyntax("db_execute [id:<id>] [sql:<query>] (args:<list>) (tx:<tx_id>) (label:<label>)");
        setRequiredArguments(2, 5);
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsException {
        for (Argument arg : scriptEntry) {
            if (!scriptEntry.hasObject("id") && arg.matchesPrefix("id")) {
                scriptEntry.addObject("id", arg.asElement());
            } else if (!scriptEntry.hasObject("sql") && arg.matchesPrefix("sql")) {
                scriptEntry.addObject("sql", arg.asElement());
            } else if (!scriptEntry.hasObject("sql") && !arg.hasPrefix()
                    && arg.getValue().startsWith("sql:")) {
                scriptEntry.addObject("sql", new ElementTag(arg.getValue().substring(4)));
            } else if (!scriptEntry.hasObject("args") && arg.matchesPrefix("args")) {
                scriptEntry.addObject("args", arg.asType(ListTag.class));
            } else if (!scriptEntry.hasObject("tx") && arg.matchesPrefix("tx")) {
                scriptEntry.addObject("tx", arg.asElement());
            } else if (!scriptEntry.hasObject("label") && arg.matchesPrefix("label")) {
                scriptEntry.addObject("label", arg.asElement());
            } else {
                arg.reportUnhandled();
            }
        }
        if (!scriptEntry.hasObject("id") || !scriptEntry.hasObject("sql")) {
            throw new InvalidArgumentsException("Must specify id and sql!");
        }
    }

    @Override
    public void execute(ScriptEntry scriptEntry) {
        String id = scriptEntry.getElement("id").asString();
        String sql = scriptEntry.getElement("sql").asString();
        ListTag args = scriptEntry.getObjectTag("args");
        ElementTag txTag = scriptEntry.getElement("tx");
        ElementTag labelTag = scriptEntry.getElement("label");
        String txId = txTag != null ? txTag.asString() : null;
        String label = labelTag != null ? labelTag.asString() : null;

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            Connection conn = null;
            PreparedStatement ps = null;
            try {
                conn = txId != null
                        ? Datenizen.getInstance().getDatabaseManager().getTransactionConnection(txId)
                        : Datenizen.getInstance().getDatabaseManager().getConnection(id);

                if (conn == null) {
                    String msg = txId != null
                        ? "Transaction '" + txId + "' not found or already committed/rolled back"
                        : "Database ID '" + id + "' not found";
                    throw new Exception(msg);
                }

                ps = conn.prepareStatement(sql);
                if (args != null) {
                    for (int i = 0; i < args.size(); i++) {
                        ps.setObject(i + 1, args.get(i));
                    }
                }
                int affected = ps.executeUpdate();
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbExecutedEvent.instance.fireFor(id, label, affected)
                );
            } catch (java.sql.SQLException e) {
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, e.getMessage(), e.getSQLState(), sql)
                );
            } catch (Exception e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, e.getMessage(), null, sql)
                );
            } finally {
                try { if (ps != null) ps.close(); } catch (Exception ignored) {}
                try { if (conn != null && txId == null) conn.close(); } catch (Exception ignored) {}
            }
        });
    }
}