package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.Settings;
import com.egor201.datenizen.events.DbBackedUpEvent;
import com.egor201.datenizen.events.DbErrorEvent;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Statement;

public class DbBackupCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_backup
    // @Syntax db_backup [id:<id>] [path:<path>]
    // @Required 2
    // @Maximum 2
    // @Short Backups an SQLite database asynchronously.
    // @Group Datenizen
    //
    // @Description
    // Uses SQLite VACUUM INTO to create a consistent copy without stopping the server.
    // The target file is replaced if it already exists.
    // For non-SQLite databases use the database engine's native backup tool.
    // -->
    public DbBackupCommand() {
        super("db_backup", "db_backup [id:<id>] [path:<path>]", 2, 2, "Must specify id and path!",
              ArgSpec.element("id"), ArgSpec.element("path"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id = se.getElement("id").asString();
        String path = se.getElement("path").asString();

        HikariDataSource ds = Datenizen.getInstance().getDatabaseManager().getDataSource(id);
        if (ds == null) return;

        String url = ds.getJdbcUrl();
        if (!url.startsWith("jdbc:sqlite:")) {
            DbErrorEvent.instance.fireFor(id, "db_backup only supports SQLite databases", null, "db_backup");
            return;
        }

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            try {
                File target = Settings.resolvePath(path);
                String sourcePath = url.substring("jdbc:sqlite:".length());
                int queryStart = sourcePath.indexOf('?');
                if (queryStart >= 0) sourcePath = sourcePath.substring(0, queryStart);
                File source = new File(sourcePath);
                if (target.getCanonicalFile().equals(source.getCanonicalFile())) {
                    DbCommandSupport.error(id, "Backup path must not be the database file itself", "db_backup");
                    return;
                }
                if (target.getParentFile() != null) {
                    target.getParentFile().mkdirs();
                }
                Files.deleteIfExists(target.toPath());

                String escaped = target.getAbsolutePath().replace("'", "''");
                String sql = "VACUUM INTO '" + escaped + "'";
                try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                     Statement st = conn.createStatement()) {
                    st.execute(sql);
                }

                org.bukkit.Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbBackedUpEvent.instance.fireFor(id, path)
                );
            } catch (Exception e) {
                e.printStackTrace();
                DbCommandSupport.error(id, e, "db_backup");
            }
        });
    }
}
