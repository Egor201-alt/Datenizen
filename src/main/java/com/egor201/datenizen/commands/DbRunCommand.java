package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbErrorEvent;
import com.egor201.datenizen.events.DbExecutedEvent;
import com.egor201.datenizen.events.DbQueriedEvent;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

public class DbRunCommand extends AbstractCommand {

    // <--[command]
    // @Name db_run
    // @Syntax db_run [id:<id>] [name:<name>] (args:<list>) (label:<label>)
    // @Required 2
    // @Maximum 4
    // @Short Executes a named query registered with db_register.
    // @Group Datenizen
    //
    // @Description
    // Runs a SQL query previously registered with db_register.
    // SELECT queries fire 'db queried'; all others fire 'db executed'.
    // Fires 'db error' on failure or if the named query does not exist.
    //
    // @Usage
    // - db_run id:main name:get_player args:<list[<player.uuid>]> label:got_player
    // -->

    public DbRunCommand() {
        setName("db_run");
        setSyntax("db_run [id:<id>] [name:<name>] (args:<list>) (label:<label>)");
        setRequiredArguments(2, 4);
    }

    @Override
    public void parseArgs(ScriptEntry se) throws InvalidArgumentsException {
        for (Argument arg : se) {
            if (!se.hasObject("id") && arg.matchesPrefix("id")) {
                se.addObject("id", arg.asElement());
            } else if (!se.hasObject("name") && arg.matchesPrefix("name")) {
                se.addObject("name", arg.asElement());
            } else if (!se.hasObject("args") && arg.matchesPrefix("args")) {
                se.addObject("args", arg.asType(ListTag.class));
            } else if (!se.hasObject("label") && arg.matchesPrefix("label")) {
                se.addObject("label", arg.asElement());
            } else {
                arg.reportUnhandled();
            }
        }
        if (!se.hasObject("id") || !se.hasObject("name")) {
            throw new InvalidArgumentsException("Must specify id and name!");
        }
    }

    @Override
    public void execute(ScriptEntry se) {
        String id    = se.getElement("id").asString();
        String name  = se.getElement("name").asString();
        ListTag args = se.getObjectTag("args");
        String label = se.hasObject("label") ? se.getElement("label").asString() : null;

        String sql = Datenizen.getInstance().getDatabaseManager().getNamedQuery(id, name);
        if (sql == null) {
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                DbErrorEvent.instance.fireFor(id, "Named query '" + name + "' not found for id '" + id + "'", null, "db_run")
            );
            return;
        }

        String trimmed = sql.stripLeading();
        boolean isSelect = trimmed.length() >= 6 && trimmed.substring(0, 6).equalsIgnoreCase("SELECT");

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                if (args != null) {
                    for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                }

                if (isSelect) {
                    try (ResultSet rs = ps.executeQuery()) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int cols = meta.getColumnCount();
                        ListTag rows = new ListTag();
                        while (rs.next()) {
                            MapTag row = new MapTag();
                            for (int i = 1; i <= cols; i++) {
                                Object val = rs.getObject(i);
                                row.putObject(meta.getColumnName(i), new ElementTag(val == null ? "null" : val.toString()));
                            }
                            rows.addObject(row);
                        }
                        Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                            DbQueriedEvent.instance.fireFor(id, label, rows)
                        );
                    }
                } else {
                    int affected = ps.executeUpdate();
                    Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                        DbExecutedEvent.instance.fireFor(id, label, affected)
                    );
                }
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
