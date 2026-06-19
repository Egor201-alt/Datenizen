package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbErrorEvent;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;

public class DbSetPoolSizeCommand extends AbstractCommand {

    // <--[command]
    // @Name db_set_pool_size
    // @Syntax db_set_pool_size [id:<id>] [size:<int>]
    // @Required 2
    // @Maximum 2
    // @Short Modifies the maximum pool size dynamically.
    // @Group Datenizen
    //
    // @Description
    // Changes the HikariCP maximum pool size without restarting the plugin.
    // Size must be between 1 and 100.
    // -->

    public DbSetPoolSizeCommand() {
        setName("db_set_pool_size");
        setSyntax("db_set_pool_size [id:<id>] [size:<int>]");
        setRequiredArguments(2, 2);
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsException {
        for (Argument arg : scriptEntry) {
            if (!scriptEntry.hasObject("id") && arg.matchesPrefix("id")) {
                scriptEntry.addObject("id", arg.asElement());
            } else if (!scriptEntry.hasObject("size") && arg.matchesPrefix("size")) {
                scriptEntry.addObject("size", arg.asElement());
            } else {
                arg.reportUnhandled();
            }
        }
        if (!scriptEntry.hasObject("id") || !scriptEntry.hasObject("size")) {
            throw new InvalidArgumentsException("Must specify id and size!");
        }
    }

    @Override
    public void execute(ScriptEntry scriptEntry) {
        String id = scriptEntry.getElement("id").asString();
        int size = scriptEntry.getElement("size").asInt();

        if (size < 1 || size > 100) {
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                DbErrorEvent.instance.fireFor(id, "Pool size must be between 1 and 100, got: " + size, "db_set_pool_size")
            );
            return;
        }

        HikariDataSource ds = Datenizen.getInstance().getDatabaseManager().getDataSource(id);
        if (ds == null) {
            DbErrorEvent.instance.fireFor(id, "Database ID not found: " + id, null, "db_set_pool_size");
            return;
        }
        ds.setMaximumPoolSize(size);
    }
}