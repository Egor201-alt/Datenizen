package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbConnectedEvent;
import com.egor201.datenizen.events.DbErrorEvent;
import org.bukkit.Bukkit;

public class DbReconnectCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_reconnect
    // @Syntax db_reconnect [id:<id>]
    // @Required 1
    // @Maximum 1
    // @Short Reconnects to a database using the same settings as the original db_connect.
    // @Group Datenizen
    //
    // @Description
    // Closes the current pool (if open) and reopens it with the exact same HikariCP config
    // that was used when db_connect was first called for this id.
    // Useful when the remote database server restarts and the pool becomes stale.
    // Fires 'db connected' on success, or 'db error' on failure.
    // Note: if the id was never connected, or was disconnected with db_disconnect, this will fail.
    //
    // @Usage
    // - db_reconnect id:main
    // -->

    public DbReconnectCommand() {
        super("db_reconnect", "db_reconnect [id:<id>]", 1, 1, "Must specify id!",
              ArgSpec.element("id"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id = se.getElement("id").asString();

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            boolean success = Datenizen.getInstance().getDatabaseManager().reconnect(id);
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () -> {
                if (success) {
                    DbConnectedEvent.instance.fireFor(id);
                } else {
                    DbErrorEvent.instance.fireFor(id, "Reconnect failed — no saved config or connection error.", "db_reconnect");
                }
            });
        });
    }
}