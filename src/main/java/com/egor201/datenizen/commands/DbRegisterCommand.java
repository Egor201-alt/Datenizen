package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;

public class DbRegisterCommand extends AbstractCommand {

    // <--[command]
    // @Name db_register
    // @Syntax db_register [id:<id>] [name:<name>] [sql:<query>]
    // @Required 3
    // @Maximum 3
    // @Short Registers a named SQL query for later use with db_run.
    // @Group Datenizen
    //
    // @Description
    // Stores a SQL query under a name scoped to a database id.
    // Use db_run to execute it by name. Avoids repeating SQL across scripts.
    //
    // @Usage
    // - db_register id:main name:get_player sql:"SELECT * FROM players WHERE uuid=?"
    // - db_run id:main name:get_player args:<list[<player.uuid>]> label:got_player
    // -->

    public DbRegisterCommand() {
        setName("db_register");
        setSyntax("db_register [id:<id>] [name:<name>] [sql:<query>]");
        setRequiredArguments(3, 3);
    }

    @Override
    public void parseArgs(ScriptEntry se) throws InvalidArgumentsException {
        for (Argument arg : se) {
            if (!se.hasObject("id") && arg.matchesPrefix("id")) {
                se.addObject("id", arg.asElement());
            } else if (!se.hasObject("name") && arg.matchesPrefix("name")) {
                se.addObject("name", arg.asElement());
            } else if (!se.hasObject("sql") && arg.matchesPrefix("sql")) {
                se.addObject("sql", arg.asElement());
            } else {
                arg.reportUnhandled();
            }
        }
        if (!se.hasObject("id") || !se.hasObject("name") || !se.hasObject("sql")) {
            throw new InvalidArgumentsException("Must specify id, name, and sql!");
        }
    }

    @Override
    public void execute(ScriptEntry se) {
        String id   = se.getElement("id").asString();
        String name = se.getElement("name").asString();
        String sql  = se.getElement("sql").asString();
        Datenizen.getInstance().getDatabaseManager().registerQuery(id, name, sql);
    }
}
