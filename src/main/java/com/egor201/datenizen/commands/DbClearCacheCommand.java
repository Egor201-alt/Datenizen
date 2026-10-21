package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;

public class DbClearCacheCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_clear_cache
    // @Syntax db_clear_cache [id:<id>]
    // @Required 1
    // @Maximum 1
    // @Short Invalidates all cached query results for a database ID.
    // @Group Datenizen
    // -->

    public DbClearCacheCommand() {
        super("db_clear_cache", "db_clear_cache [id:<id>]", 1, 1, "Must specify id!",
              ArgSpec.element("id"));
    }

    @Override
    public void execute(ScriptEntry se) {
        Datenizen.getInstance().getDatabaseManager().invalidateCache(se.getElement("id").asString());
    }
}
