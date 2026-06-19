package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;

public class DbDisconnectCommand extends AbstractCommand {
    // <--[command]
    // @Name db_disconnect
    // @Syntax db_disconnect [id:<id>]
    // @Required 1
    // @Maximum 1
    // @Short Disconnects and removes a connection pool.
    // @Group Datenizen
    // -->
    public DbDisconnectCommand() { setName("db_disconnect"); setSyntax("db_disconnect [id:<id>]"); setRequiredArguments(1, 1); }
    @Override public void parseArgs(ScriptEntry se) throws InvalidArgumentsException {
        for (Argument arg : se) {
            if (!se.hasObject("id") && arg.matchesPrefix("id")) se.addObject("id", arg.asElement());
            else arg.reportUnhandled();
        }
        if (!se.hasObject("id")) throw new InvalidArgumentsException("Must specify id!");
    }
    @Override public void execute(ScriptEntry se) { Datenizen.getInstance().getDatabaseManager().disconnect(se.getElement("id").asString()); }
}