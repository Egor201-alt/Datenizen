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

public class DbDropTableCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_drop_table
    // @Syntax db_drop_table [id:<id>] [table:<table>]
    // @Required 2
    // @Maximum 2
    // @Short Drops a table securely.
    // @Group Datenizen
    //
    // @Description
    // Executes a DROP TABLE IF EXISTS statement asynchronously.
    // Table name must contain only alphanumeric characters and underscores.
    // -->

    private static final java.util.regex.Pattern SAFE_NAME = com.egor201.datenizen.util.Identifiers.PATTERN;

    public DbDropTableCommand() {
        super("db_drop_table", "db_drop_table [id:<id>] [table:<table>]", 2, 2,
              "Must specify id and table!",
              ArgSpec.element("id"), ArgSpec.element("table"));
    }

    @Override
    public void execute(ScriptEntry scriptEntry) {
        String id = scriptEntry.getElement("id").asString();
        String table = scriptEntry.getElement("table").asString();

        if (!SAFE_NAME.matcher(table).matches()) {
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                DbErrorEvent.instance.fireFor(id, "Invalid table name: " + table, "db_drop_table")
            );
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            String sql = "DROP TABLE IF EXISTS " + table;
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                 Statement st = conn.createStatement()) {
                st.executeUpdate(sql);
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbExecutedEvent.instance.fireFor(id, "db_drop_table", 0)
                );
            } catch (java.sql.SQLException e) {
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, e.getMessage(), e.getSQLState(), sql)
                );
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, e.getMessage(), null, sql)
                );
            }
        });
    }
}