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

public class DbCreateIndexCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_create_index
    // @Syntax db_create_index [id:<id>] [table:<table>] [name:<index_name>] [column:<column>] (unique:true/false)
    // @Required 4
    // @Maximum 5
    // @Short Creates an index on a table column.
    // @Group Datenizen
    //
    // @Description
    // Executes CREATE [UNIQUE] INDEX IF NOT EXISTS name ON table (column) asynchronously.
    // Fires 'db executed' with label 'db_create_index' on success.
    //
    // @Usage
    // - db_create_index id:main table:players name:idx_players_uuid column:uuid unique:true
    // -->

    private static final Pattern SAFE_NAME = com.egor201.datenizen.util.Identifiers.PATTERN;

    public DbCreateIndexCommand() {
        super("db_create_index",
              "db_create_index [id:<id>] [table:<table>] [name:<index_name>] [column:<column>] (unique:true/false)",
              4, 5,
              "Must specify id, table, name, and column!",
              ArgSpec.element("id"),
              ArgSpec.element("table"),
              ArgSpec.element("name"),
              ArgSpec.element("column"),
              ArgSpec.optionalElement("unique"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id     = se.getElement("id").asString();
        String table  = se.getElement("table").asString();
        String name   = se.getElement("name").asString();
        String column = se.getElement("column").asString();
        boolean unique = se.hasObject("unique") && se.getElement("unique").asBoolean();

        if (!SAFE_NAME.matcher(table).matches() || !SAFE_NAME.matcher(name).matches() || !SAFE_NAME.matcher(column).matches()) {
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                DbErrorEvent.instance.fireFor(id, "Invalid table, index, or column name", null, "db_create_index")
            );
            return;
        }

        String sql = "CREATE " + (unique ? "UNIQUE " : "") + "INDEX IF NOT EXISTS " + name + " ON " + table + " (" + column + ")";

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                 Statement st = conn.createStatement()) {
                st.executeUpdate(sql);
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbExecutedEvent.instance.fireFor(id, "db_create_index", 0)
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
