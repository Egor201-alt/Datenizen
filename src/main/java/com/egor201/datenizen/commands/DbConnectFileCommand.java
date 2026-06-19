package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.database.DatabaseManager;
import com.egor201.datenizen.events.DbConnectedEvent;
import com.egor201.datenizen.events.DbErrorEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class DbConnectFileCommand extends AbstractCommand {

    // <--[command]
    // @Name db_connect_file
    // @Syntax db_connect_file [id:<id>] [path:<path>]
    // @Required 2
    // @Maximum 2
    // @Short Connects to a database using credentials from a YAML file.
    // @Group Datenizen
    //
    // @Description
    // Reads a YAML file and connects using its 'driver', 'url', 'user', and 'password' keys.
    // 'driver' and 'url' are required. 'user' and 'password' are optional.
    // Avoids storing credentials directly in scripts.
    // Fires 'db connected' on success or 'db error' on failure.
    //
    // @Usage
    // Example db.yml:
    //   driver: sqlite
    //   url: plugins/MyPlugin/data.db
    //
    // - db_connect_file id:main path:plugins/MyPlugin/db.yml
    // -->

    public DbConnectFileCommand() {
        setName("db_connect_file");
        setSyntax("db_connect_file [id:<id>] [path:<path>]");
        setRequiredArguments(2, 2);
    }

    @Override
    public void parseArgs(ScriptEntry se) throws InvalidArgumentsException {
        for (Argument arg : se) {
            if (!se.hasObject("id") && arg.matchesPrefix("id")) {
                se.addObject("id", arg.asElement());
            } else if (!se.hasObject("path") && arg.matchesPrefix("path")) {
                se.addObject("path", arg.asElement());
            } else {
                arg.reportUnhandled();
            }
        }
        if (!se.hasObject("id") || !se.hasObject("path")) {
            throw new InvalidArgumentsException("Must specify id and path!");
        }
    }

    @Override
    public void execute(ScriptEntry se) {
        String id   = se.getElement("id").asString();
        String path = se.getElement("path").asString();

        if (Datenizen.getInstance().getDatabaseManager().getDataSource(id) != null) {
            DbErrorEvent.instance.fireFor(id, "A connection with id '" + id + "' already exists", null, "db_connect_file");
            return;
        }

        File file = new File(path);
        if (!file.exists()) {
            DbErrorEvent.instance.fireFor(id, "File not found: " + path, null, "db_connect_file");
            return;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String driverAlias = cfg.getString("driver");
        String rawUrl      = cfg.getString("url");
        String user        = cfg.getString("user", "");
        String password    = cfg.getString("password", "");

        if (driverAlias == null || driverAlias.isEmpty()) {
            DbErrorEvent.instance.fireFor(id, "Missing 'driver' in " + path, null, "db_connect_file");
            return;
        }
        if (rawUrl == null || rawUrl.isEmpty()) {
            DbErrorEvent.instance.fireFor(id, "Missing 'url' in " + path, null, "db_connect_file");
            return;
        }

        String driver = DatabaseManager.resolveDriver(driverAlias);
        if (driver == null) {
            DbErrorEvent.instance.fireFor(id, "Unknown driver alias: " + driverAlias, null, "db_connect_file");
            return;
        }

        String url = DatabaseManager.resolveUrl(driver, rawUrl);
        final String finalUser     = user;
        final String finalPassword = password;

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            boolean success = Datenizen.getInstance().getDatabaseManager().connect(id, driver, url, finalUser, finalPassword);
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () -> {
                if (success) {
                    DbConnectedEvent.instance.fireFor(id);
                } else {
                    DbErrorEvent.instance.fireFor(id, "Failed to connect. Check server log.", null, "db_connect_file");
                }
            });
        });
    }
}
