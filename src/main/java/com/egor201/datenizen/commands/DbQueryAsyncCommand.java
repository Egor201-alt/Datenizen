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
    // @Syntax db_query_async [id:<id>] [sql:<query>] (args:<list>) (label:<label>)
    // @Required 2
    // @Maximum 4
    // @Short Runs a SELECT query asynchronously and fires 'db queried' with results.
    // @Group Datenizen
    //
    // @Description
    // Executes a SELECT query asynchronously.
    // Fires 'db queried' with <context.rows> (ListTag of MapTags) on success.
    // Fires 'db error' on failure.
    //
    // @Usage
    // - db_query_async id:main sql:"SELECT * FROM players WHERE active=1" label:load_all
    // on db queried label:load_all:
    //   - foreach <context.rows> as:row:
    //       - narrate <[row].get[name]>
    // -->

    public DbQueryAsyncCommand() {
        setName("db_query_async");
        setSyntax("db_query_async [id:<id>] [sql:<query>] (args:<list>) (label:<label>)");
        setRequiredArguments(2, 4);
    }

    @Override
    public void parseArgs(ScriptEntry se) throws InvalidArgumentsException {
        for (Argument arg : se) {
            if (!se.hasObject("id") && arg.matchesPrefix("id")) se.addObject("id", arg.asElement());
            else if (!se.hasObject("sql") && arg.matchesPrefix("sql")) se.addObject("sql", arg.asElement());
            else if (!se.hasObject("args") && arg.matchesPrefix("args")) se.addObject("args", arg.asType(ListTag.class));
            else if (!se.hasObject("label") && arg.matchesPrefix("label")) se.addObject("label", arg.asElement());
            else arg.reportUnhandled();
        }
        if (!se.hasObject("id") || !se.hasObject("sql"))
            throw new InvalidArgumentsException("Must specify id and sql!");
    }

    @Override
    public void execute(ScriptEntry se) {
        String id    = se.getElement("id").asString();
        String sql   = se.getElement("sql").asString();
        ListTag args = se.getObjectTag("args");
        String label = se.hasObject("label") ? se.getElement("label").asString() : null;

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
                    Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                        DbQueriedEvent.instance.fireFor(id, label, rows)
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
