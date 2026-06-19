package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbErrorEvent;
import org.bukkit.Bukkit;

public class DbAnalyzeCommand extends AbstractCommand {
    // <--[command]
    // @Name db_analyze
    // @Syntax db_analyze [id:<id>]
    // @Required 1
    // @Maximum 1
    // @Short Runs VACUUM or ANALYZE to optimize the database.
    // @Group Datenizen
    // -->
    public DbAnalyzeCommand() {
        setName("db_analyze");
        setSyntax("db_analyze [id:<id>]");
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
        String id = se.getElement("id").asString();
        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            try {
                Datenizen.getInstance().getDatabaseManager().analyze(id);
            } catch (java.sql.SQLException e) {
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, e.getMessage(), e.getSQLState(), "ANALYZE/VACUUM"));
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, e.getMessage(), null, "ANALYZE/VACUUM"));
            }
        });
    }
}