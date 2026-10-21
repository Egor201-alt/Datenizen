package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;

public class DbRegisterCommand extends AbstractDbCommand {

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
        super("db_register",
              "db_register [id:<id>] [name:<name>] [sql:<query>]",
              3, 3,
              "Must specify id, name, and sql!",
              ArgSpec.element("id"),
              ArgSpec.element("name"),
              ArgSpec.element("sql"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id   = se.getElement("id").asString();
        String name = se.getElement("name").asString();
        String sql  = se.getElement("sql").asString();
        Datenizen.getInstance().getDatabaseManager().registerQuery(id, name, sql);
    }
}
