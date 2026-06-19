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

public class DbAddColumnCommand extends AbstractCommand {

    // <--[command]
    // @Name db_add_column
    // @Syntax db_add_column [id:<id>] [table:<table>] [column:<definition>]
    // @Required 3
    // @Maximum 3
    // @Short Adds a column to a table via ALTER TABLE.
    // @Group Datenizen
    //
    // @Description
    // Executes ALTER TABLE table ADD COLUMN definition asynchronously.
    // 'column' is a full column definition, e.g. "rank TEXT DEFAULT 'default'".
    // Table name and first word of definition must be alphanumeric/underscores only.
    // Fires 'db executed' with label 'db_add_column' on success.
    //
    // @Usage
    // - db_add_column id:main table:players column:"rank TEXT DEFAULT 'default'"
    // -->

    private static final Pattern SAFE_NAME = Pattern.compile("^[a-zA-Z0-9_]+$");

    public DbAddColumnCommand() {
        setName("db_add_column");
        setSyntax("db_add_column [id:<id>] [table:<table>] [column:<definition>]");
        setRequiredArguments(3, 3);
    }

    @Override
    public void parseArgs(ScriptEntry se) throws InvalidArgumentsException {
        for (Argument arg : se) {
            if (!se.hasObject("id") && arg.matchesPrefix("id")) se.addObject("id", arg.asElement());
            else if (!se.hasObject("table") && arg.matchesPrefix("table")) se.addObject("table", arg.asElement());
            else if (!se.hasObject("column") && arg.matchesPrefix("column")) se.addObject("column", arg.asElement());
            else arg.reportUnhandled();
        }
        if (!se.hasObject("id") || !se.hasObject("table") || !se.hasObject("column"))
            throw new InvalidArgumentsException("Must specify id, table, and column!");
    }

    @Override
    public void execute(ScriptEntry se) {
        String id         = se.getElement("id").asString();
        String table      = se.getElement("table").asString();
        String definition = se.getElement("column").asString();

        String colName = definition.trim().split("\\s+")[0];

        if (!SAFE_NAME.matcher(table).matches() || !SAFE_NAME.matcher(colName).matches()) {
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                DbErrorEvent.instance.fireFor(id, "Invalid table or column name", null, "db_add_column")
            );
            return;
        }

        String sql = "ALTER TABLE " + table + " ADD COLUMN " + definition;

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                 Statement st = conn.createStatement()) {
                st.executeUpdate(sql);
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbExecutedEvent.instance.fireFor(id, "db_add_column", 0)
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
