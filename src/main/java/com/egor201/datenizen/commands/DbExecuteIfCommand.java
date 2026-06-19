package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbErrorEvent;
import com.egor201.datenizen.events.DbExecutedEvent;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DbExecuteIfCommand extends AbstractCommand {

    // <--[command]
    // @Name db_execute_if
    // @Syntax db_execute_if [id:<id>] [check:<sql>] (check_args:<list>) [sql:<sql>] (args:<list>) (label:<label>)
    // @Required 3
    // @Maximum 6
    // @Short Runs SQL only if a check query returns at least one row.
    // @Group Datenizen
    //
    // @Description
    // Runs the 'check' SELECT first. If it returns at least one row, executes 'sql'.
    // If the check returns no rows, fires 'db executed' with affected_rows=0 (skipped).
    // Both queries run on the same connection in a single async task.
    // Fires 'db error' on any failure.
    //
    // @Usage
    // - db_execute_if id:main check:"SELECT 1 FROM players WHERE uuid=?" check_args:<list[<player.uuid>]> sql:"UPDATE players SET online=1 WHERE uuid=?" args:<list[<player.uuid>]> label:mark_online
    // -->

    public DbExecuteIfCommand() {
        setName("db_execute_if");
        setSyntax("db_execute_if [id:<id>] [check:<sql>] (check_args:<list>) [sql:<sql>] (args:<list>) (label:<label>)");
        setRequiredArguments(3, 6);
    }

    @Override
    public void parseArgs(ScriptEntry se) throws InvalidArgumentsException {
        for (Argument arg : se) {
            if (!se.hasObject("id") && arg.matchesPrefix("id")) se.addObject("id", arg.asElement());
            else if (!se.hasObject("check") && arg.matchesPrefix("check")) se.addObject("check", arg.asElement());
            else if (!se.hasObject("check_args") && arg.matchesPrefix("check_args")) se.addObject("check_args", arg.asType(ListTag.class));
            else if (!se.hasObject("sql") && arg.matchesPrefix("sql")) se.addObject("sql", arg.asElement());
            else if (!se.hasObject("args") && arg.matchesPrefix("args")) se.addObject("args", arg.asType(ListTag.class));
            else if (!se.hasObject("label") && arg.matchesPrefix("label")) se.addObject("label", arg.asElement());
            else arg.reportUnhandled();
        }
        if (!se.hasObject("id") || !se.hasObject("check") || !se.hasObject("sql"))
            throw new InvalidArgumentsException("Must specify id, check, and sql!");
    }

    @Override
    public void execute(ScriptEntry se) {
        String id          = se.getElement("id").asString();
        String checkSql    = se.getElement("check").asString();
        ListTag checkArgs  = se.getObjectTag("check_args");
        String sql         = se.getElement("sql").asString();
        ListTag args       = se.getObjectTag("args");
        String label       = se.hasObject("label") ? se.getElement("label").asString() : null;

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id)) {
                boolean hasRows;
                try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    if (checkArgs != null) {
                        for (int i = 0; i < checkArgs.size(); i++) ps.setObject(i + 1, checkArgs.get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        hasRows = rs.next();
                    }
                }

                if (!hasRows) {
                    Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                        DbExecutedEvent.instance.fireFor(id, label, 0)
                    );
                    return;
                }

                int affected;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (args != null) {
                        for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
                    }
                    affected = ps.executeUpdate();
                }

                final int finalAffected = affected;
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbExecutedEvent.instance.fireFor(id, label, finalAffected)
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
