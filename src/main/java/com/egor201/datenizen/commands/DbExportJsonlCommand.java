package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.Settings;
import com.egor201.datenizen.database.DatabaseManager;
import com.egor201.datenizen.database.ResultMapper;
import com.egor201.datenizen.events.DbJsonExportedEvent;
import com.egor201.datenizen.util.NamedParams;
import com.egor201.datenizen.util.SqlUtils;
import com.google.gson.JsonObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DbExportJsonlCommand extends AbstractDbCommand {

    // <--[command]
    // @Name db_export_jsonl
    // @Syntax db_export_jsonl [id:<id>] [sql:<query>] [path:<path>] (args:<list>) (named_args:<map>) (timeout:<ms>)
    // @Required 3
    // @Maximum 6
    // @Short Streams a query result to a JSON Lines file.
    // @Group Datenizen
    //
    // @Description
    // Writes one JSON object per line (UTF-8). Memory usage stays flat even for large tables.
    // Fires 'db json exported' on success.
    //
    // @Usage
    // - db_export_jsonl id:main sql:"SELECT * FROM players" path:plugins/Datenizen/exports/players.jsonl
    // -->
    public DbExportJsonlCommand() {
        super("db_export_jsonl",
              "db_export_jsonl [id:<id>] [sql:<query>] [path:<path>] (args:<list>) (named_args:<map>) (timeout:<ms>)",
              3, 6,
              "Must specify id, sql, and path!",
              ArgSpec.element("id"),
              ArgSpec.element("sql"),
              ArgSpec.element("path"),
              ArgSpec.optionalList("args"),
              ArgSpec.optionalMap("named_args"),
              ArgSpec.optionalElement("timeout"));
    }

    @Override
    public void execute(ScriptEntry se) {
        String id = se.getElement("id").asString();
        String sql = se.getElement("sql").asString();
        String path = se.getElement("path").asString();
        ListTag args = se.getObjectTag("args");
        MapTag namedArgs = se.getObjectTag("named_args");
        ElementTag timeoutTag = se.getElement("timeout");

        long timeoutMs = -1;
        if (timeoutTag != null) {
            timeoutMs = SqlUtils.parseDurationMillis(timeoutTag.asString());
            if (timeoutMs < 0) {
                DbCommandSupport.error(id, "Invalid timeout: " + timeoutTag.asString(), "db_export_jsonl");
                return;
            }
        }
        final long finalTimeout = timeoutMs;

        NamedParams.Bound bound;
        try {
            bound = NamedParams.bind(sql, namedArgs);
        } catch (IllegalArgumentException e) {
            DbCommandSupport.error(id, e.getMessage(), "db_export_jsonl");
            return;
        }

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            File outputFile = Settings.resolvePath(path);
            if (outputFile.getParentFile() != null) outputFile.getParentFile().mkdirs();

            DatabaseManager manager = Datenizen.getInstance().getDatabaseManager();
            long start = System.nanoTime();
            try (Connection conn = manager.getConnection(id);
                 PreparedStatement ps = conn.prepareStatement(bound.sql())) {

                DbCommandSupport.applySettings(ps, finalTimeout);
                if (!bound.values().isEmpty()) {
                    ResultMapper.bindValues(ps, bound.values());
                } else {
                    ResultMapper.bind(ps, args);
                }
                DbCommandSupport.log(bound.sql());

                try (ResultSet rs = ps.executeQuery();
                     BufferedWriter writer = new BufferedWriter(
                         new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {

                    while (rs.next()) {
                        JsonObject object = ResultMapper.jsonObject(rs);
                        writer.write(object.toString());
                        writer.newLine();
                    }
                    writer.flush();
                }

                long duration = DbCommandSupport.elapsedMs(start);
                manager.recordQuery(id, duration, true, bound.sql());
                org.bukkit.Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbJsonExportedEvent.instance.fireFor(id, path)
                );
            } catch (Exception e) {
                long duration = DbCommandSupport.elapsedMs(start);
                manager.recordQuery(id, duration, false, bound.sql());
                manager.recordError(id, e, bound.sql());
                DbCommandSupport.error(id, e, bound.sql());
            }
        });
    }
}
