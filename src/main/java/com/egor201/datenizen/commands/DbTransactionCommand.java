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
    // @Syntax db_transaction [id:<id>] [action:start/commit/rollback/savepoint/rollback_to/release] [tx:<tx_id>] (savepoint:<name>)
    // @Required 3
    // @Maximum 4
    // @Short Manages SQL transactions and savepoints.
    // @Group Datenizen
    //
    // @Description
    // Starts, commits, or rolls back a transaction.
    // Savepoint actions (savepoint, rollback_to, release) require tx:<tx_id> and savepoint:<name>.
    // Savepoint names must be alphanumeric/underscores only.
    // -->

    private static final java.util.regex.Pattern SAFE_NAME = java.util.regex.Pattern.compile("^[a-zA-Z0-9_]+$");

    public DbTransactionCommand() {
        setName("db_transaction");
        setSyntax("db_transaction [id:<id>] [action:start/commit/rollback/savepoint/rollback_to/release] [tx:<tx_id>] (savepoint:<name>)");
        setRequiredArguments(3, 4);
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
            } else if (!scriptEntry.hasObject("savepoint") && arg.matchesPrefix("savepoint")) {
                scriptEntry.addObject("savepoint", arg.asElement());
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

        if (action.equals("savepoint") || action.equals("rollback_to") || action.equals("release")) {
            ElementTag spTag = scriptEntry.getElement("savepoint");
            if (spTag == null) {
                DbErrorEvent.instance.fireFor(id, "savepoint name required for action:" + action, null, "db_transaction");
                return;
            }
            String spName = spTag.asString();
            if (!SAFE_NAME.matcher(spName).matches()) {
                DbErrorEvent.instance.fireFor(id, "Invalid savepoint name: " + spName, null, "db_transaction");
                return;
            }
            java.sql.Connection spConn = Datenizen.getInstance().getDatabaseManager().getTransactionConnection(txId);
            if (spConn == null) {
                DbErrorEvent.instance.fireFor(id, "Transaction '" + txId + "' not found or already committed/rolled back", null, "db_transaction");
                return;
            }
            String spSql = switch (action) {
                case "savepoint"   -> "SAVEPOINT " + spName;
                case "rollback_to" -> "ROLLBACK TO SAVEPOINT " + spName;
                default            -> "RELEASE SAVEPOINT " + spName;
            };
            Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
                try (java.sql.Statement st = spConn.createStatement()) {
                    st.execute(spSql);
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                        DbErrorEvent.instance.fireFor(id, e.getMessage(),
                            e instanceof java.sql.SQLException ? ((java.sql.SQLException) e).getSQLState() : null,
                            "db_transaction " + action)
                    );
                }
            });
            return;
        }

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