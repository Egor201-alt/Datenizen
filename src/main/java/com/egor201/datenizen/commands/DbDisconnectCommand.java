package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;

public class DbDisconnectCommand extends AbstractDbCommand {
    // <--[command]
    // @Name db_disconnect
    // @Syntax db_disconnect [id:<id>]
    // @Required 1
    // @Maximum 1
    // @Short Disconnects and removes a connection pool.
    // @Group Datenizen
    // -->
    public DbDisconnectCommand() {
        super("db_disconnect", "db_disconnect [id:<id>]", 1, 1, "Must specify id!",
              ArgSpec.element("id"));
    }
    @Override public void execute(ScriptEntry se) { Datenizen.getInstance().getDatabaseManager().disconnect(se.getElement("id").asString()); }
}