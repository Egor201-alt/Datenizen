package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbErrorEvent;
import com.egor201.datenizen.events.DbExecutedEvent;
import org.bukkit.Bukkit;

import com.egor201.datenizen.util.SqlUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

public class DbExecuteScriptCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_execute_script
    // @Syntax db_execute_script [id:<id>] [path:<path>]
    // @Required 2
    // @Maximum 2
    // @Short Executes a full SQL script from a file.
    // @Group Datenizen
    //
    // @Description
    // Reads a .sql file and executes each statement separated by semicolons asynchronously.
    // Handles single-quoted strings, double-quoted identifiers, and both -- and /* */ comments
    // so semicolons inside literals or comments do not split statements incorrectly.
    // All statements run inside a transaction and are rolled back on failure.
    // -->

    public DbExecuteScriptCommand() {
        super("db_execute_script", "db_execute_script [id:<id>] [path:<path>]", 2, 2,
              "Must specify id and path!",
              ArgSpec.element("id"), ArgSpec.element("path"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id   = se.getElement("id").asString();
        String path = se.getElement("path").asString();

        File f = new File(path);
        if (!f.exists()) return;

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            Connection conn = null;
            try {
                String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                List<String> queries = SqlUtils.splitStatements(content);

                conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                conn.setAutoCommit(false);

                for (String q : queries) {
                    try (PreparedStatement ps = conn.prepareStatement(q)) {
                        ps.executeUpdate();
                    }
                }

                conn.commit();
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbExecutedEvent.instance.fireFor(id, "db_execute_script", 0)
                );
            } catch (Exception e) {
                e.printStackTrace();
                if (conn != null) {
                    try { conn.rollback(); } catch (Exception ignored) {}
                }
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, e.getMessage(), null, "SCRIPT EXECUTION")
                );
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(true); conn.close(); } catch (Exception ignored) {}
                }
            }
        });
    }
}
