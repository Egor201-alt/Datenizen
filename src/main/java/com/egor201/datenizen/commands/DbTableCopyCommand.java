package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbErrorEvent;
import com.egor201.datenizen.events.DbExecutedEvent;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.Statement;
import java.util.regex.Pattern;

public class DbTableCopyCommand extends AbstractCommand {

    // <--[command]
    // @Name db_table_copy
    // @Syntax db_table_copy [id:<id>] [from:<table>] [to:<table>] (insert_only:true/false)
    // @Required 3
    // @Maximum 4
    // @Short Copies data from one table to another.
    // @Group Datenizen
    //
    // @Description
    // By default (insert_only:false) creates a new table as a copy of the source.
    // With insert_only:true, inserts source rows into an existing target table.
    // Both table names must be alphanumeric/underscores only.
    // Fires 'db executed' with label 'db_table_copy' on success.
    //
    // @Usage
    // - db_table_copy id:main from:players to:players_backup
    // - db_table_copy id:main from:players to:players_archive insert_only:true
    // -->

    private static final Pattern SAFE_NAME = Pattern.compile("^[a-zA-Z0-9_]+$");

    public DbTableCopyCommand() {
        setName("db_table_copy");
        setSyntax("db_table_copy [id:<id>] [from:<table>] [to:<table>] (insert_only:true/false)");
        setRequiredArguments(3, 4);
    }

    @Override
    public void parseArgs(ScriptEntry se) throws InvalidArgumentsException {
        for (Argument arg : se) {
            if (!se.hasObject("id") && arg.matchesPrefix("id")) {
                se.addObject("id", arg.asElement());
            } else if (!se.hasObject("from") && arg.matchesPrefix("from")) {
                se.addObject("from", arg.asElement());
            } else if (!se.hasObject("to") && arg.matchesPrefix("to")) {
                se.addObject("to", arg.asElement());
            } else if (!se.hasObject("insert_only") && arg.matchesPrefix("insert_only")) {
                se.addObject("insert_only", arg.asElement());
            } else {
                arg.reportUnhandled();
            }
        }
        if (!se.hasObject("id") || !se.hasObject("from") || !se.hasObject("to")) {
            throw new InvalidArgumentsException("Must specify id, from, and to!");
        }
    }

    @Override
    public void execute(ScriptEntry se) {
        String id         = se.getElement("id").asString();
        String fromTable  = se.getElement("from").asString();
        String toTable    = se.getElement("to").asString();
        ElementTag ioTag  = se.getElement("insert_only");
        boolean insertOnly = ioTag != null && ioTag.asBoolean();

        if (!SAFE_NAME.matcher(fromTable).matches() || !SAFE_NAME.matcher(toTable).matches()) {
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                DbErrorEvent.instance.fireFor(id, "Invalid table name in from or to", null, "db_table_copy")
            );
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            String sql;
            if (insertOnly) {
                sql = "INSERT INTO " + toTable + " SELECT * FROM " + fromTable;
            } else {
                String dbType = Datenizen.getInstance().getDatabaseManager().getDatabaseType(id);
                // MySQL does not support AS in CREATE TABLE ... SELECT
                sql = dbType.equals("mysql") || dbType.equals("mariadb")
                    ? "CREATE TABLE " + toTable + " SELECT * FROM " + fromTable
                    : "CREATE TABLE " + toTable + " AS SELECT * FROM " + fromTable;
            }

            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                 Statement st = conn.createStatement()) {
                st.execute(sql);
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbExecutedEvent.instance.fireFor(id, "db_table_copy", 0)
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
