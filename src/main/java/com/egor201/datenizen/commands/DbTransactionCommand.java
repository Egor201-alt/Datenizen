package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbErrorEvent;
import org.bukkit.Bukkit;

public class DbTransactionCommand extends AbstractCommand {

    // <--[command]
    // @Name db_transaction
    // @Syntax db_transaction [id:<id>] [action:start/commit/rollback] [tx:<tx_id>]
    // @Required 3
    // @Maximum 3
    // @Short Manages SQL transactions.
    // @Group Datenizen
    //
    // @Description
    // Starts, commits, or rolls back a transaction.
    // -->

    public DbTransactionCommand() {
        setName("db_transaction");
        setSyntax("db_transaction [id:<id>] [action:start/commit/rollback] [tx:<tx_id>]");
        setRequiredArguments(3, 3);
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsException {
        for (Argument arg : scriptEntry) {
            if (!scriptEntry.hasObject("id") && arg.matchesPrefix("id")) {
                scriptEntry.addObject("id", arg.asElement());
            } else if (!scriptEntry.hasObject("action") && arg.matchesPrefix("action")) {
                scriptEntry.addObject("action", arg.asElement());
            } else if (!scriptEntry.hasObject("tx") && arg.matchesPrefix("tx")) {
                scriptEntry.addObject("tx", arg.asElement());
            } else {
                arg.reportUnhandled();
            }
        }
        if (!scriptEntry.hasObject("action") || !scriptEntry.hasObject("tx")) {
            throw new InvalidArgumentsException("Must specify action and tx!");
        }
    }

    @Override
    public void execute(ScriptEntry scriptEntry) {
        ElementTag idTag = scriptEntry.getElement("id");
        ElementTag actionTag = scriptEntry.getElement("action");
        ElementTag txTag = scriptEntry.getElement("tx");

        if (actionTag == null || txTag == null) {
            return;
        }

        String id = idTag != null ? idTag.asString() : "";
        String action = actionTag.asString().toLowerCase();
        String txId = txTag.asString();

        try {
            switch (action) {
                case "start" -> {
                    if (id.isEmpty()) {
                        DbErrorEvent.instance.fireFor("", "id is required for db_transaction action:start", null, "db_transaction");
                        return;
                    }
                    Datenizen.getInstance().getDatabaseManager().startTransaction(txId, id);
                }
                case "commit" -> Datenizen.getInstance().getDatabaseManager().commitTransaction(txId);
                case "rollback" -> Datenizen.getInstance().getDatabaseManager().rollbackTransaction(txId);
                default -> {
                    Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                        DbErrorEvent.instance.fireFor(id, "Unknown action: " + action, null, "db_transaction")
                    );
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                DbErrorEvent.instance.fireFor(id, e.getMessage(), e instanceof java.sql.SQLException ? ((java.sql.SQLException)e).getSQLState() : null, "db_transaction " + action)
            );
        }
    }
}