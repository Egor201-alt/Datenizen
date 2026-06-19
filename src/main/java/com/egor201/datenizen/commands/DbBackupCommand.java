package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbBackedUpEvent;
import com.egor201.datenizen.events.DbErrorEvent;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class DbBackupCommand extends AbstractCommand {

    // <--[command]
    // @Name db_backup
    // @Syntax db_backup [id:<id>] [path:<path>]
    // @Required 2
    // @Maximum 2
    // @Short Backups an SQLite database asynchronously.
    // @Group Datenizen
    //
    // @Description
    // Copies the SQLite file to a new location asynchronously.
    // -->

    public DbBackupCommand() {
        setName("db_backup");
        setSyntax("db_backup [id:<id>] [path:<path>]");
        setRequiredArguments(2, 2);
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsException {
        for (Argument arg : scriptEntry) {
            if (!scriptEntry.hasObject("id") && arg.matchesPrefix("id")) {
                scriptEntry.addObject("id", arg.asElement());
            } else if (!scriptEntry.hasObject("path") && arg.matchesPrefix("path")) {
                scriptEntry.addObject("path", arg.asElement());
            } else {
                arg.reportUnhandled();
            }
        }
        if (!scriptEntry.hasObject("id") || !scriptEntry.hasObject("path")) {
            throw new InvalidArgumentsException("Must specify id and path!");
        }
    }

    @Override
    public void execute(ScriptEntry scriptEntry) {
        String id = scriptEntry.getElement("id").asString();
        String path = scriptEntry.getElement("path").asString();

        HikariDataSource ds = Datenizen.getInstance().getDatabaseManager().getDataSource(id);
        if (ds == null) return;

        String url = ds.getJdbcUrl();
        if (!url.startsWith("jdbc:sqlite:")) {
            DbErrorEvent.instance.fireFor(id, "db_backup only supports SQLite databases", null, "db_backup");
            return;
        }

        String sourceFile = url.replace("jdbc:sqlite:", "");

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            try {
                File source = new File(sourceFile);
                File target = new File(path);

                if (target.getParentFile() != null) {
                    target.getParentFile().mkdirs();
                }

                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbBackedUpEvent.instance.fireFor(id, path)
                );
            } catch (Exception e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, e.getMessage(), null, "db_backup")
                );
            }
        });
    }
}