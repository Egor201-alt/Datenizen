package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbErrorEvent;
import com.egor201.datenizen.events.DbExecutedEvent;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class DbExecuteAsyncListCommand extends AbstractCommand {

    // <--[command]
    // @Name db_execute_async_list
    // @Syntax db_execute_async_list [id:<id>] [sql:<list>]
    // @Required 2
    // @Maximum 2
    // @Short Executes a list of parameterized SQL queries asynchronously.
    // @Group Datenizen
    //
    // @Description
    // Runs multiple SQL statements sequentially in a single async task.
    // Each entry in the list is treated as a separate PreparedStatement to prevent SQL injection.
    // Statements are executed inside a transaction and rolled back on failure.
    // -->

    public DbExecuteAsyncListCommand() {
        setName("db_execute_async_list");
        setSyntax("db_execute_async_list [id:<id>] [sql:<list>]");
        setRequiredArguments(2, 2);
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsException {
        for (Argument arg : scriptEntry) {
            if (!scriptEntry.hasObject("id") && arg.matchesPrefix("id")) {
                scriptEntry.addObject("id", arg.asElement());
            } else if (!scriptEntry.hasObject("sql") && arg.matchesPrefix("sql")) {
                scriptEntry.addObject("sql", arg.asType(ListTag.class));
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
        ListTag sqlList = scriptEntry.getObjectTag("sql");

        if (sqlList == null || sqlList.isEmpty()) return;

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            Connection conn = null;
            try {
                conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                conn.setAutoCommit(false);

                for (String sql : sqlList) {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.executeUpdate();
                    }
                }

                conn.commit();
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbExecutedEvent.instance.fireFor(id, null, 0)
                );
            } catch (Exception e) {
                e.printStackTrace();
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (Exception ignored) {}
                }
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, e.getMessage(), null, "ASYNC LIST")
                );
            } finally {
                if (conn != null) {
                    try {
                        conn.setAutoCommit(true);
                        conn.close();
                    } catch (Exception ignored) {}
                }
            }
        });
    }
}