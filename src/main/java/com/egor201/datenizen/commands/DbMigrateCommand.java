package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbErrorEvent;
import com.egor201.datenizen.events.DbMigratedEvent;
import org.bukkit.Bukkit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DbMigrateCommand extends AbstractCommand {

    // <--[command]
    // @Name db_migrate
    // @Syntax db_migrate [id:<id>] [path:<folder>]
    // @Required 2
    // @Maximum 2
    // @Short Runs pending SQL migration files from a folder in alphabetical order.
    // @Group Datenizen
    //
    // @Description
    // Scans a folder for *.sql files sorted alphabetically and applies any not yet
    // recorded in __datenizen_migrations. Each file runs in its own transaction.
    // Stops on first failure and fires 'db error'. Fires 'db migrated' on completion
    // with the count of newly applied files (0 if already up to date).
    //
    // @Usage
    // - db_migrate id:main path:plugins/MyPlugin/migrations
    // on db migrated id:main:
    //   - narrate "Applied <context.count> migrations."
    // -->

    public DbMigrateCommand() {
        setName("db_migrate");
        setSyntax("db_migrate [id:<id>] [path:<folder>]");
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

    private List<String> splitStatements(String content) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false, inDoubleQuote = false;
        boolean inLineComment = false, inBlockComment = false;

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
                if (c == '*' && next == '/') { current.append(next); i++; inBlockComment = false; }
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote) {
                if (c == '-' && next == '-') { inLineComment = true; current.append(c); continue; }
                if (c == '/' && next == '*') { inBlockComment = true; current.append(c); continue; }
                if (c == ';') {
                    String stmt = current.toString().trim();
                    if (!stmt.isEmpty()) statements.add(stmt);
                    current.setLength(0);
                    continue;
                }
            }
            if (c == '\'' && !inDoubleQuote) inSingleQuote = !inSingleQuote;
            else if (c == '"' && !inSingleQuote) inDoubleQuote = !inDoubleQuote;
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

        File folder = new File(path);
        if (!folder.isDirectory()) {
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                DbErrorEvent.instance.fireFor(id, "Path is not a directory: " + path, null, "db_migrate")
            );
            return;
        }

        File[] sqlFiles = folder.listFiles((dir, name) -> name.endsWith(".sql"));
        if (sqlFiles == null) sqlFiles = new File[0];
        Arrays.sort(sqlFiles, (a, b) -> a.getName().compareTo(b.getName()));
        final File[] files = sqlFiles;

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id)) {

                try (Statement st = conn.createStatement()) {
                    st.execute("CREATE TABLE IF NOT EXISTS __datenizen_migrations (name TEXT PRIMARY KEY, applied_at INTEGER)");
                }

                int applied = 0;
                for (File file : files) {
                    String fileName = file.getName();

                    try (PreparedStatement check = conn.prepareStatement(
                            "SELECT 1 FROM __datenizen_migrations WHERE name=?")) {
                        check.setString(1, fileName);
                        try (ResultSet rs = check.executeQuery()) {
                            if (rs.next()) continue;
                        }
                    }

                    String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                    List<String> stmts = splitStatements(content);

                    conn.setAutoCommit(false);
                    try {
                        for (String stmt : stmts) {
                            try (Statement st = conn.createStatement()) {
                                st.execute(stmt);
                            }
                        }
                        try (PreparedStatement insert = conn.prepareStatement(
                                "INSERT INTO __datenizen_migrations (name, applied_at) VALUES (?, ?)")) {
                            insert.setString(1, fileName);
                            insert.setLong(2, System.currentTimeMillis());
                            insert.executeUpdate();
                        }
                        conn.commit();
                        applied++;
                    } catch (Exception e) {
                        try { conn.rollback(); } catch (Exception ignored) {}
                        conn.setAutoCommit(true);
                        final String errMsg = e.getMessage();
                        final String sqlState = e instanceof java.sql.SQLException ? ((java.sql.SQLException) e).getSQLState() : null;
                        Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                            DbErrorEvent.instance.fireFor(id, "Migration '" + fileName + "' failed: " + errMsg, sqlState, "db_migrate")
                        );
                        return;
                    }
                    conn.setAutoCommit(true);
                }

                final int finalApplied = applied;
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbMigratedEvent.instance.fireFor(id, finalApplied, path)
                );
            } catch (Exception e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, e.getMessage(),
                        e instanceof java.sql.SQLException ? ((java.sql.SQLException) e).getSQLState() : null,
                        "db_migrate")
                );
            }
        });
    }
}
