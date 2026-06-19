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
import com.egor201.datenizen.events.DbQueriedEvent;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

public class DbQueryAsyncCommand extends AbstractCommand {

    // <--[command]
    // @Name db_query_async
    // @Syntax db_query_async [id:<id>] [sql:<query>] (args:<list>) (label:<label>) (define:<name>)
    // @Required 2
    // @Maximum 5
    // @Short Runs a SELECT query asynchronously and fires 'db queried' or injects into a definition.
    // @Group Datenizen
    //
    // @Description
    // Executes a SELECT query asynchronously.
    // Without 'define': fires 'db queried' with <context.rows> (ListTag of MapTags).
    // With 'define:<name>': pauses the queue, injects results as a definition, then resumes —
    //   allowing immediate use via <[name]> in the next line without an event handler.
    // Fires 'db error' on failure (and resumes queue if define was set).
    //
    // @Usage
    // Event-driven (no define):
    // - db_query_async id:main sql:"SELECT * FROM players WHERE active=1" label:load_all
    // on db queried label:load_all:
    //   - foreach <context.rows> as:row:
    //       - narrate <[row].get[name]>
    //
    // @Usage
    // Inline with define (queue pauses until results arrive):
    // - db_query_async id:main sql:"SELECT * FROM players" define:players
    // - narrate "Found <[players].size> players"
    // -->

    public DbQueryAsyncCommand() {
        setName("db_query_async");
        setSyntax("db_query_async [id:<id>] [sql:<query>] (args:<list>) (label:<label>) (define:<name>)");
        setRequiredArguments(2, 5);
    }

    @Override
    public void parseArgs(ScriptEntry se) throws InvalidArgumentsException {
        for (Argument arg : se) {
            if (!se.hasObject("id") && arg.matchesPrefix("id")) se.addObject("id", arg.asElement());
            else if (!se.hasObject("sql") && arg.matchesPrefix("sql")) se.addObject("sql", arg.asElement());
            else if (!se.hasObject("args") && arg.matchesPrefix("args")) se.addObject("args", arg.asType(ListTag.class));
            else if (!se.hasObject("label") && arg.matchesPrefix("label")) se.addObject("label", arg.asElement());
            else if (!se.hasObject("define") && arg.matchesPrefix("define")) se.addObject("define", arg.asElement());
            else arg.reportUnhandled();
        }
        if (!se.hasObject("id") || !se.hasObject("sql"))
            throw new InvalidArgumentsException("Must specify id and sql!");
    }

    @Override
    public void execute(ScriptEntry se) {
        String id         = se.getElement("id").asString();
        String sql        = se.getElement("sql").asString();
        ListTag args      = se.getObjectTag("args");
        String label      = se.hasObject("label") ? se.getElement("label").asString() : null;
        String defineName = se.hasObject("define") ? se.getElement("define").asString() : null;

        if (defineName != null) se.getResidingQueue().pause();

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                if (args != null) {
                    for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                }

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
                    Bukkit.getScheduler().runTask(Datenizen.getInstance(), () -> {
                        if (defineName != null) {
                            se.getResidingQueue().addDefinition(defineName, rows);
                            se.getResidingQueue().resume();
                        } else {
                            DbQueriedEvent.instance.fireFor(id, label, rows);
                        }
                    });
                }
            } catch (java.sql.SQLException e) {
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () -> {
                    if (defineName != null) {
                        se.getResidingQueue().addDefinition(defineName, new ListTag());
                        se.getResidingQueue().resume();
                    }
                    DbErrorEvent.instance.fireFor(id, e.getMessage(), e.getSQLState(), sql);
                });
            } catch (Exception e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () -> {
                    if (defineName != null) {
                        se.getResidingQueue().addDefinition(defineName, new ListTag());
                        se.getResidingQueue().resume();
                    }
                    DbErrorEvent.instance.fireFor(id, e.getMessage(), null, sql);
                });
            }
        });
    }
}
