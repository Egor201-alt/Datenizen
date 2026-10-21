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

public class DbRenameTableCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_rename_table
    // @Syntax db_rename_table [id:<id>] [from:<table>] [to:<table>]
    // @Required 3
    // @Maximum 3
    // @Short Renames a table via ALTER TABLE RENAME TO.
    // @Group Datenizen
    //
    // @Usage
    // - db_rename_table id:main from:players to:players_old
    // -->

    private static final Pattern SAFE_NAME = com.egor201.datenizen.util.Identifiers.PATTERN;

    public DbRenameTableCommand() {
        super("db_rename_table",
              "db_rename_table [id:<id>] [from:<table>] [to:<table>]",
              3, 3,
              "Must specify id, from, and to!",
              ArgSpec.element("id"),
              ArgSpec.element("from"),
              ArgSpec.element("to"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id   = se.getElement("id").asString();
        String from = se.getElement("from").asString();
        String to   = se.getElement("to").asString();

        if (!SAFE_NAME.matcher(from).matches() || !SAFE_NAME.matcher(to).matches()) {
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                DbErrorEvent.instance.fireFor(id, "Invalid table name", null, "db_rename_table")
            );
            return;
        }

        String sql = "ALTER TABLE " + from + " RENAME TO " + to;

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                 Statement st = conn.createStatement()) {
                st.executeUpdate(sql);
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbExecutedEvent.instance.fireFor(id, "db_rename_table", 0)
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
