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

public class DbDropColumnCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_drop_column
    // @Syntax db_drop_column [id:<id>] [table:<table>] [column:<column>]
    // @Required 3
    // @Maximum 3
    // @Short Drops a column from a table via ALTER TABLE.
    // @Group Datenizen
    //
    // @Description
    // Executes ALTER TABLE table DROP COLUMN column asynchronously.
    // Note: SQLite versions before 3.35.0 do not support DROP COLUMN — the driver will throw if unsupported.
    // Fires 'db executed' with label 'db_drop_column' on success.
    //
    // @Usage
    // - db_drop_column id:main table:players column:old_field
    // -->

    private static final Pattern SAFE_NAME = com.egor201.datenizen.util.Identifiers.PATTERN;

    public DbDropColumnCommand() {
        super("db_drop_column",
              "db_drop_column [id:<id>] [table:<table>] [column:<column>]",
              3, 3,
              "Must specify id, table, and column!",
              ArgSpec.element("id"), ArgSpec.element("table"), ArgSpec.element("column"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id     = se.getElement("id").asString();
        String table  = se.getElement("table").asString();
        String column = se.getElement("column").asString();

        if (!SAFE_NAME.matcher(table).matches() || !SAFE_NAME.matcher(column).matches()) {
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                DbErrorEvent.instance.fireFor(id, "Invalid table or column name", null, "db_drop_column")
            );
            return;
        }

        String sql = "ALTER TABLE " + table + " DROP COLUMN " + column;

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                 Statement st = conn.createStatement()) {
                st.executeUpdate(sql);
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbExecutedEvent.instance.fireFor(id, "db_drop_column", 0)
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
