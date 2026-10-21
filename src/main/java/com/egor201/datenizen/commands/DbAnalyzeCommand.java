package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbErrorEvent;
import org.bukkit.Bukkit;

public class DbAnalyzeCommand extends AbstractDbCommand {
    // <--[command]
    // @Name db_analyze
    // @Syntax db_analyze [id:<id>]
    // @Required 1
    // @Maximum 1
    // @Short Runs VACUUM or ANALYZE to optimize the database.
    // @Group Datenizen
    // -->
    public DbAnalyzeCommand() {
        super("db_analyze", "db_analyze [id:<id>]", 1, 1, "Must specify id!",
              ArgSpec.element("id"));
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