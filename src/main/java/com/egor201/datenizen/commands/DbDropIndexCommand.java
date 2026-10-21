package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbErrorEvent;
import com.egor201.datenizen.events.DbExecutedEvent;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.Statement;
import java.util.regex.Pattern;

public class DbDropIndexCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_drop_index
    // @Syntax db_drop_index [id:<id>] [name:<index_name>] (table:<table>)
    // @Required 2
    // @Maximum 3
    // @Short Drops an index by name.
    // @Group Datenizen
    //
    // @Description
    // SQLite and PostgreSQL: DROP INDEX IF EXISTS name
    // MySQL and MariaDB: DROP INDEX name ON table — 'table' argument is required for these databases.
    // Fires 'db executed' with label 'db_drop_index' on success.
    //
    // @Usage
    // - db_drop_index id:main name:idx_players_uuid
    // - db_drop_index id:mysql_db name:idx_players_uuid table:players
    // -->

    private static final Pattern SAFE_NAME = com.egor201.datenizen.util.Identifiers.PATTERN;

    public DbDropIndexCommand() {
        super("db_drop_index",
              "db_drop_index [id:<id>] [name:<index_name>] (table:<table>)",
              2, 3,
              "Must specify id and name!",
              ArgSpec.element("id"),
              ArgSpec.element("name"),
              ArgSpec.optionalElement("table"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id    = se.getElement("id").asString();
        String name  = se.getElement("name").asString();
        String table = se.hasObject("table") ? se.getElement("table").asString() : null;

        if (!SAFE_NAME.matcher(name).matches() || (table != null && !SAFE_NAME.matcher(table).matches())) {
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                DbErrorEvent.instance.fireFor(id, "Invalid index or table name", null, "db_drop_index")
            );
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            String dbType = Datenizen.getInstance().getDatabaseManager().getDatabaseType(id);
            String sql;
            if ((dbType.equals("mysql") || dbType.equals("mariadb")) && table != null) {
                sql = "DROP INDEX " + name + " ON " + table;
            } else {
                sql = "DROP INDEX IF EXISTS " + name;
            }

            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                 Statement st = conn.createStatement()) {
                st.executeUpdate(sql);
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbExecutedEvent.instance.fireFor(id, "db_drop_index", 0)
                );
            } catch (java.sql.SQLException e) {
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, e.getMessage(), e.getSQLState(), sql)
                );
            } catch (Exception e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, e.getMessage(), null, sql)
                );
            }
        });
    }
}
