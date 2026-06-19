package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbErrorEvent;
import com.egor201.datenizen.events.DbExecutedEvent;
import org.bukkit.Bukkit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

public class DbExecuteScriptCommand extends AbstractCommand {

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
        setName("db_execute_script");
        setSyntax("db_execute_script [id:<id>] [path:<path>]");
        setRequiredArguments(2, 2);
    }

    @Override
    public void parseArgs(ScriptEntry se) throws InvalidArgumentsException {
        for (Argument arg : se) {
            if (!se.hasObject("id") && arg.matchesPrefix("id")) {
                se.addObject("id", arg.asElement());
            } else if (!se.hasObject("path") && arg.matchesPrefix("path")) {
                se.addObject("path", arg.asElement());
            } else {
                arg.reportUnhandled();
            }
        }
        if (!se.hasObject("id") || !se.hasObject("path")) {
            throw new InvalidArgumentsException("Must specify id and path!");
        }
    }

    // Splits SQL content by ';' while ignoring semicolons inside string literals and comments.
    private List<String> splitStatements(String content) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            char next = (i + 1 < content.length()) ? content.charAt(i + 1) : 0;

            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                current.append(c);
                continue;
            }

            if (inBlockComment) {
                current.append(c);
                if (c == '*' && next == '/') {
                    current.append(next);
                    i++;
                    inBlockComment = false;
                }
                continue;
            }

            if (!inSingleQuote && !inDoubleQuote) {
                if (c == '-' && next == '-') {
                    inLineComment = true;
                    current.append(c);
                    continue;
                }
                if (c == '/' && next == '*') {
                    inBlockComment = true;
                    current.append(c);
                    continue;
                }
                if (c == ';') {
                    String stmt = current.toString().trim();
                    if (!stmt.isEmpty()) statements.add(stmt);
                    current.setLength(0);
                    continue;
                }
            }

            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            }

            current.append(c);
        }

        String remaining = current.toString().trim();
        if (!remaining.isEmpty()) statements.add(remaining);

        return statements;
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
                List<String> queries = splitStatements(content);

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
