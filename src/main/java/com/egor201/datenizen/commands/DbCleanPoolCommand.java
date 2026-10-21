package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;

public class DbCleanPoolCommand extends AbstractDbCommand {
    // <--[command]
    // @Name db_clean_pool
    // @Syntax db_clean_pool[id:<id>]
    // @Required 1
    // @Maximum 1
    // @Short Evicts idle connections from the pool.
    // @Group Datenizen
    // -->
    public DbCleanPoolCommand() {
        super("db_clean_pool", "db_clean_pool [id:<id>]", 1, 1, "Must specify id!",
              ArgSpec.element("id"));
    }

    @Override
    public void execute(ScriptEntry se) {
        Datenizen.getInstance().getDatabaseManager().cleanPool(se.getElement("id").asString());
    }
}