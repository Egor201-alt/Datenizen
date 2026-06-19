package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.database.DatabaseManager;
import com.egor201.datenizen.events.DbConnectedEvent;
import com.egor201.datenizen.events.DbErrorEvent;
import org.bukkit.Bukkit;

public class DbConnectCommand extends AbstractCommand {

    // <--[command]
    // @Name db_connect
    // @Syntax db_connect [id:<id>] [driver:<driver>] [url:<url>] (user:<user>) (pass:<pass>)
    // @Required 3
    // @Maximum 5
    // @Short Connects to a database using HikariCP connection pooling.
    // @Group Datenizen
    //
    // @Description
    // Connects to a database asynchronously and stores the connection pool under the specified ID.
    // Fires 'db connected' on success, or 'db error' on failure.
    //
    // The 'driver' argument accepts short aliases (recommended) or full class names:
    //   sqlite      -> org.sqlite.JDBC
    //   mysql       -> com.mysql.cj.jdbc.Driver
    //   mariadb     -> org.mariadb.jdbc.Driver
    //   postgresql  -> org.postgresql.Driver
    //   postgres    -> org.postgresql.Driver
    //
    // The 'url' argument can be a short form (no 'jdbc:' prefix needed):
    //   For sqlite:     path to the .db file     -> plugins/Datenizen/data.db
    //   For mysql:      host:port/dbname          -> localhost:3306/mydb
    //   For postgresql: host:port/dbname          -> localhost:5432/mydb
    // Or you can still use the full JDBC URL:
    //   jdbc:sqlite:plugins/Datenizen/data.db
    //   jdbc:mysql://localhost:3306/mydb
    //
    // @Usage
    // Use to connect to an SQLite database.
    // - db_connect id:local driver:sqlite url:plugins/Datenizen/local.db
    //
    // @Usage
    // Use to connect to a MySQL database.
    // - db_connect id:main driver:mysql url:localhost:3306/mydb user:root pass:1234
    //
    // @Usage
    // Use to connect to a PostgreSQL database.
    // - db_connect id:pg driver:postgres url:localhost:5432/mydb user:admin pass:secret
    // -->

    public DbConnectCommand() {
        setName("db_connect");
        setSyntax("db_connect [id:<id>] [driver:<driver>] [url:<url>] (user:<user>) (pass:<pass>)");
        setRequiredArguments(3, 5);
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsException {
        for (Argument arg : scriptEntry) {
            if (!scriptEntry.hasObject("id") && arg.matchesPrefix("id")) {
                scriptEntry.addObject("id", arg.asElement());
            } else if (!scriptEntry.hasObject("driver") && arg.matchesPrefix("driver")) {
                scriptEntry.addObject("driver", arg.asElement());
            } else if (!scriptEntry.hasObject("url") && arg.matchesPrefix("url")) {
                scriptEntry.addObject("url", arg.asElement());
            } else if (!scriptEntry.hasObject("user") && arg.matchesPrefix("user")) {
                scriptEntry.addObject("user", arg.asElement());
            } else if (!scriptEntry.hasObject("pass") && arg.matchesPrefix("pass")) {
                scriptEntry.addObject("pass", arg.asElement());
            } else {
                arg.reportUnhandled();
            }
        }
        if (!scriptEntry.hasObject("id") || !scriptEntry.hasObject("driver") || !scriptEntry.hasObject("url")) {
            throw new InvalidArgumentsException("Must specify id, driver, and url!");
        }
    }

    @Override
    public void execute(ScriptEntry scriptEntry) {
        String id = scriptEntry.getElement("id").asString();
        String driverInput = scriptEntry.getElement("driver").asString();
        String urlInput = scriptEntry.getElement("url").asString();
        ElementTag userTag = scriptEntry.getElement("user");
        ElementTag passTag = scriptEntry.getElement("pass");
        String user = userTag != null ? userTag.asString() : "";
        String pass = passTag != null ? passTag.asString() : "";

        String driver = DatabaseManager.resolveDriver(driverInput);
        if (driver == null) {
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                DbErrorEvent.instance.fireFor(id,
                    "Unknown driver alias '" + driverInput + "'. Use: sqlite, mysql, mariadb, postgresql",
                    "db_connect")
            );
            return;
        }

        String url = DatabaseManager.resolveUrl(driver, urlInput);

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            if (Datenizen.getInstance().getDatabaseManager().getDataSource(id) != null) {
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, "A connection with id '" + id + "' already exists", "db_connect")
                );
                return;
            }
            boolean success = Datenizen.getInstance().getDatabaseManager().connect(id, driver, url, user, pass);
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () -> {
                if (success) {
                    DbConnectedEvent.instance.fireFor(id);
                } else {
                    DbErrorEvent.instance.fireFor(id, "Failed to connect to '" + id + "'. Check server log for details.", "db_connect");
                }
            });
        });
    }
}