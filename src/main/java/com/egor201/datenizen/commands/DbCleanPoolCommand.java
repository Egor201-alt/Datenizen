package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;

public class DbCleanPoolCommand extends AbstractCommand {
    // <--[command]
    // @Name db_clean_pool
    // @Syntax db_clean_pool[id:<id>]
    // @Required 1
    // @Maximum 1
    // @Short Evicts idle connections from the pool.
    // @Group Datenizen
    // -->
    public DbCleanPoolCommand() {
        setName("db_clean_pool");
        setSyntax("db_clean_pool [id:<id>]");
        setRequiredArguments(1, 1);
    }

    @Override
    public void parseArgs(ScriptEntry se) throws InvalidArgumentsException {
        for (Argument arg : se) {
            if (!se.hasObject("id") && arg.matchesPrefix("id")) {
                se.addObject("id", arg.asElement());
            } else {
                arg.reportUnhandled();
            }
        }
        if (!se.hasObject("id")) {
            throw new InvalidArgumentsException("Must specify id!");
        }
    }

    @Override
    public void execute(ScriptEntry se) {
        Datenizen.getInstance().getDatabaseManager().cleanPool(se.getElement("id").asString());
    }
}