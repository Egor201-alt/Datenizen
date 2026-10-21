package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbErrorEvent;
import com.egor201.datenizen.events.DbMigratedEvent;
import com.egor201.datenizen.util.SqlUtils;
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

public class DbMigrateCommand extends AbstractDbCommand {

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
        super("db_migrate", "db_migrate [id:<id>] [path:<folder>]", 2, 2,
              "Must specify id and path!",
              ArgSpec.element("id"), ArgSpec.element("path"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id   = se.getElement("id").asString();
        String path = se.getElement("path").asString();

        File folder = com.egor201.datenizen.Settings.resolvePath(path);
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
                    st.execute("CREATE TABLE IF NOT EXISTS __datenizen_migrations (name VARCHAR(255) PRIMARY KEY, applied_at BIGINT, checksum VARCHAR(64))");
                }

                boolean hasChecksum = columnExists(conn, "__datenizen_migrations", "checksum");
                if (!hasChecksum) {
                    try (Statement st = conn.createStatement()) {
                        st.execute("ALTER TABLE __datenizen_migrations ADD COLUMN checksum VARCHAR(64)");
                        hasChecksum = true;
                    } catch (Exception ignored) {}
                }

                int applied = 0;
                for (File file : files) {
                    String fileName = file.getName();
                    String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                    String checksum = SqlUtils.checksum(content);

                    String recordedChecksum = null;
                    boolean alreadyApplied = false;
                    try (PreparedStatement check = conn.prepareStatement(
                            "SELECT checksum FROM __datenizen_migrations WHERE name=?")) {
                        check.setString(1, fileName);
                        try (ResultSet rs = check.executeQuery()) {
                            if (rs.next()) {
                                alreadyApplied = true;
                                recordedChecksum = rs.getString(1);
                            }
                        }
                    } catch (java.sql.SQLException e) {
                        try (PreparedStatement check = conn.prepareStatement(
                                "SELECT 1 FROM __datenizen_migrations WHERE name=?")) {
                            check.setString(1, fileName);
                            try (ResultSet rs = check.executeQuery()) {
                                alreadyApplied = rs.next();
                            }
                        }
                    }

                    if (alreadyApplied) {
                        if (com.egor201.datenizen.Settings.migrationChecksums
                            && recordedChecksum != null && !recordedChecksum.isEmpty()
                            && !recordedChecksum.equals(checksum)) {
                            final String errMsg = "Migration '" + fileName + "' checksum mismatch";
                            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                                DbErrorEvent.instance.fireFor(id, errMsg, null, "db_migrate")
                            );
                            return;
                        }
                        continue;
                    }

                    List<String> stmts = SqlUtils.splitStatements(content);

                    conn.setAutoCommit(false);
                    try {
                        for (String stmt : stmts) {
                            try (Statement st = conn.createStatement()) {
                                st.execute(stmt);
                            }
                        }
                        String insertSql = hasChecksum
                            ? "INSERT INTO __datenizen_migrations (name, applied_at, checksum) VALUES (?, ?, ?)"
                            : "INSERT INTO __datenizen_migrations (name, applied_at) VALUES (?, ?)";
                        try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
                            insert.setString(1, fileName);
                            insert.setLong(2, System.currentTimeMillis());
                            if (hasChecksum) insert.setString(3, checksum);
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

    private static boolean columnExists(Connection conn, String table, String column) {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, table, column)) {
            while (rs.next()) {
                if (rs.getString("COLUMN_NAME").equalsIgnoreCase(column)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }
}
