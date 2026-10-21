package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbErrorEvent;
import com.egor201.datenizen.events.DbExecutedEvent;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class DbExecuteBatchCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_execute_batch
    // @Syntax db_execute_batch [id:<id>] [sql:<query>] [args:<list_of_lists>] (label:<label>)
    // @Required 3
    // @Maximum 4
    // @Short Executes a parameterized SQL query multiple times efficiently using JDBC batching.
    // @Group Datenizen
    //
    // @Description
    // Uses JDBC batching inside a transaction to insert or update multiple rows in one operation.
    // Provide a ListTag where each element is a ListTag of arguments for one row.
    // If any row fails the entire batch is rolled back.
    // The sql argument supports both prefixed and quoted forms:
    //   sql:INSERT INTO t (a,b) VALUES (?,?)
    //   "sql:INSERT INTO t (a,b) VALUES (?,?)"
    // Fires 'db executed' on success with <context.affected_rows> as total rows affected.
    //
    // @Usage
    // - db_execute_batch id:main "sql:INSERT INTO players (name, uuid) VALUES (?, ?)" args:<list[<list[Steve|uuid1]>|<list[Alex|uuid2]>]> label:bulk_insert
    // -->

    public DbExecuteBatchCommand() {
        super("db_execute_batch",
              "db_execute_batch [id:<id>] [sql:<query>] [args:<list_of_lists>] (label:<label>)",
              3, 4,
              "Must specify id, sql, and args!",
              ArgSpec.element("id"),
              ArgSpec.sql("sql"),
              ArgSpec.list("args"),
              ArgSpec.optionalElement("label"));
    }

    @Override
    public void execute(ScriptEntry scriptEntry) {
        String id = scriptEntry.getElement("id").asString();
        String sql = scriptEntry.getElement("sql").asString();
        ListTag argsList = scriptEntry.getObjectTag("args");
        String label = scriptEntry.hasObject("label") ? scriptEntry.getElement("label").asString() : null;

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            Connection conn = null;
            try {
                conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                conn.setAutoCommit(false);

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (String row : argsList) {
                        ListTag rowArgs = ListTag.valueOf(row, scriptEntry.getContext());
                        for (int i = 0; i < rowArgs.size(); i++) {
                            ps.setObject(i + 1, rowArgs.get(i));
                        }
                        ps.addBatch();
                    }
                    int[] results = ps.executeBatch();
                    int total = 0;
                    for (int r : results) if (r > 0) total += r;
                    final int finalTotal = total;
                    conn.commit();
                    Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                        DbExecutedEvent.instance.fireFor(id, label, finalTotal)
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (conn != null) {
                    try { conn.rollback(); } catch (Exception ignored) {}
                }
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, e.getMessage(), e instanceof java.sql.SQLException ? ((java.sql.SQLException)e).getSQLState() : null, sql)
                );
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(true); conn.close(); } catch (Exception ignored) {}
                }
            }
        });
    }
}