package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbErrorEvent;
import org.bukkit.Bukkit;

public class DbListenCommand extends AbstractCommand {

    // <--[command]
    // @Name db_listen
    // @Syntax db_listen [id:<id>] [channel:<channel>] [action:<start|stop>]
    // @Required 3
    // @Maximum 3
    // @Short Subscribes to or unsubscribes from a PostgreSQL LISTEN/NOTIFY channel.
    // @Group Datenizen
    //
    // @Description
    // PostgreSQL only. Starts or stops listening on the given channel.
    // When a NOTIFY is received, fires 'db notify' with <context.id>, <context.channel>, <context.payload>.
    // Notifications are polled every second (20 ticks).
    //
    // @Usage
    // - db_listen id:pg channel:player_events action:start
    // on db notify channel:player_events:
    //   - narrate "Got: <context.payload>"
    // - db_listen id:pg channel:player_events action:stop
    // -->

    public DbListenCommand() {
        setName("db_listen");
        setSyntax("db_listen [id:<id>] [channel:<channel>] [action:<start|stop>]");
        setRequiredArguments(3, 3);
    }

    @Override
    public void parseArgs(ScriptEntry se) throws InvalidArgumentsException {
        for (Argument arg : se) {
            if (!se.hasObject("id") && arg.matchesPrefix("id")) se.addObject("id", arg.asElement());
            else if (!se.hasObject("channel") && arg.matchesPrefix("channel")) se.addObject("channel", arg.asElement());
            else if (!se.hasObject("action") && arg.matchesPrefix("action")) se.addObject("action", arg.asElement());
            else arg.reportUnhandled();
        }
        if (!se.hasObject("id") || !se.hasObject("channel") || !se.hasObject("action"))
            throw new InvalidArgumentsException("Must specify id, channel, and action!");
    }

    @Override
    public void execute(ScriptEntry se) {
        String id      = se.getElement("id").asString();
        String channel = se.getElement("channel").asString();
        String action  = se.getElement("action").asString().toLowerCase();

        String dbType = Datenizen.getInstance().getDatabaseManager().getDatabaseType(id);
        if (!dbType.equals("postgresql")) {
            DbErrorEvent.instance.fireFor(id, "db_listen only supports PostgreSQL", null, "db_listen");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            try {
                if (action.equals("start")) {
                    Datenizen.getInstance().getDatabaseManager().startListen(id, channel);
                } else if (action.equals("stop")) {
                    Datenizen.getInstance().getDatabaseManager().stopListen(id, channel);
                } else {
                    Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                        DbErrorEvent.instance.fireFor(id, "Unknown action: " + action + " (use start or stop)", null, "db_listen")
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, e.getMessage(), null, "db_listen")
                );
            }
        });
    }
}
