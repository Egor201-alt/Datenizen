package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbErrorEvent;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;

public class DbTimeoutCommand extends AbstractCommand {

    // <--[command]
    // @Name db_timeout
    // @Syntax db_timeout [id:<id>] [ms:<int>]
    // @Required 2
    // @Maximum 2
    // @Short Dynamically sets the connection timeout in milliseconds.
    // @Group Datenizen
    //
    // @Description
    // Sets the HikariCP connection timeout. Minimum value is 250ms as required by HikariCP.
    // -->

    public DbTimeoutCommand() {
        setName("db_timeout");
        setSyntax("db_timeout [id:<id>] [ms:<int>]");
        setRequiredArguments(2, 2);
    }

    @Override
    public void parseArgs(ScriptEntry se) throws InvalidArgumentsException {
        for (Argument arg : se) {
            if (!se.hasObject("id") && arg.matchesPrefix("id")) {
                se.addObject("id", arg.asElement());
            } else if (!se.hasObject("ms") && arg.matchesPrefix("ms")) {
                se.addObject("ms", arg.asElement());
            } else {
                arg.reportUnhandled();
            }
        }
        if (!se.hasObject("id") || !se.hasObject("ms")) {
            throw new InvalidArgumentsException("Must specify id and ms!");
        }
    }

    @Override
    public void execute(ScriptEntry se) {
        String id = se.getElement("id").asString();
        int ms = se.getElement("ms").asInt();

        if (ms < 250) {
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                DbErrorEvent.instance.fireFor(id, "Timeout must be at least 250ms, got: " + ms, "db_timeout")
            );
            return;
        }

        HikariDataSource ds = Datenizen.getInstance().getDatabaseManager().getDataSource(id);
        if (ds == null) {
            DbErrorEvent.instance.fireFor(id, "Database ID not found: " + id, null, "db_timeout");
            return;
        }
        if (ds.getHikariConfigMXBean() == null) {
            DbErrorEvent.instance.fireFor(id, "HikariCP config MX Bean unavailable for: " + id, null, "db_timeout");
            return;
        }
        ds.getHikariConfigMXBean().setConnectionTimeout(ms);
    }
}