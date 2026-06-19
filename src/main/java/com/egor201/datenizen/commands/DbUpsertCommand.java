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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class DbUpsertCommand extends AbstractCommand {

    // <--[command]
    // @Name db_upsert
    // @Syntax db_upsert [id:<id>] [table:<table>] [key_column:<col>] [key_value:<val>] [set:<list>] (label:<label>)
    // @Required 5
    // @Maximum 6
    // @Short Insert or update a row without writing SQL manually.
    // @Group Datenizen
    //
    // @Description
    // The most common game-server pattern: "save this player's data whether they exist or not."
    // Builds INSERT OR REPLACE (SQLite) or INSERT ... ON DUPLICATE KEY UPDATE (MySQL/MariaDB)
    // or INSERT ... ON CONFLICT DO UPDATE SET col=EXCLUDED.col (PostgreSQL).
    // 'set' is a ListTag of "column=value" pairs. The key column must have a UNIQUE or PRIMARY KEY constraint.
    // Column names must be alphanumeric/underscores only. Values are safely parameterized.
    // Fires 'db executed' on success with <context.affected_rows>.
    //
    // @Usage
    // - db_upsert id:main table:players key_column:uuid key_value:<player.uuid> set:<list[name=<player.name>|coins=0]> label:save_player
    // -->

    private static final Pattern SAFE_NAME = Pattern.compile("^[a-zA-Z0-9_]+$");

    public DbUpsertCommand() {
        setName("db_upsert");
        setSyntax("db_upsert [id:<id>] [table:<table>] [key_column:<col>] [key_value:<val>] [set:<list>] (label:<label>)");
        setRequiredArguments(5, 6);
    }

    @Override
    public void parseArgs(ScriptEntry se) throws InvalidArgumentsException {
        for (Argument arg : se) {
            if (!se.hasObject("id")         && arg.matchesPrefix("id"))         se.addObject("id",         arg.asElement());
            else if (!se.hasObject("table")      && arg.matchesPrefix("table"))      se.addObject("table",      arg.asElement());
            else if (!se.hasObject("key_column") && arg.matchesPrefix("key_column")) se.addObject("key_column", arg.asElement());
            else if (!se.hasObject("key_value")  && arg.matchesPrefix("key_value"))  se.addObject("key_value",  arg.asElement());
            else if (!se.hasObject("set")        && arg.matchesPrefix("set"))        se.addObject("set",        arg.asType(ListTag.class));
            else if (!se.hasObject("label")      && arg.matchesPrefix("label"))      se.addObject("label",      arg.asElement());
            else arg.reportUnhandled();
        }
        if (!se.hasObject("id") || !se.hasObject("table") || !se.hasObject("key_column")
                || !se.hasObject("key_value") || !se.hasObject("set")) {
            throw new InvalidArgumentsException("Must specify id, table, key_column, key_value, and set!");
        }
    }

    private record Pair(String col, String val) {}

    @Override
    public void execute(ScriptEntry se) {
        String id       = se.getElement("id").asString();
        String table    = se.getElement("table").asString();
        String keyCol   = se.getElement("key_column").asString();
        String keyVal   = se.getElement("key_value").asString();
        ListTag setList = se.getObjectTag("set");
        String label    = se.hasObject("label") ? se.getElement("label").asString() : null;

        if (!SAFE_NAME.matcher(table).matches() || !SAFE_NAME.matcher(keyCol).matches()) {
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                DbErrorEvent.instance.fireFor(id, "Invalid table or key_column name", null, "db_upsert")
            );
            return;
        }

        List<Pair> pairs = new ArrayList<>();
        for (String entry : setList) {
            int eq = entry.indexOf('=');
            if (eq < 1) {
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, "Invalid set entry: " + entry, null, "db_upsert")
                );
                return;
            }
            String col = entry.substring(0, eq).trim();
            String val = entry.substring(eq + 1);
            if (!SAFE_NAME.matcher(col).matches()) {
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbErrorEvent.instance.fireFor(id, "Invalid column name: " + col, null, "db_upsert")
                );
                return;
            }
            pairs.add(new Pair(col, val));
        }

        if (pairs.isEmpty()) {
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                DbErrorEvent.instance.fireFor(id, "set list must not be empty", null, "db_upsert")
            );
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            String dbType = Datenizen.getInstance().getDatabaseManager().getDatabaseType(id);
            String sql = buildSql(dbType, table, keyCol, pairs);

            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id);
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                int idx = 1;
                ps.setObject(idx++, keyVal);
                for (Pair p : pairs) ps.setObject(idx++, p.val());

                if (dbType.equals("mysql") || dbType.equals("mariadb")) {
                    for (Pair p : pairs) ps.setObject(idx++, p.val());
                }

                int affected = ps.executeUpdate();
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbExecutedEvent.instance.fireFor(id, label, affected)
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

    private String buildSql(String dbType, String table, String keyCol, List<Pair> pairs) {
        StringBuilder cols = new StringBuilder(keyCol);
        StringBuilder placeholders = new StringBuilder("?");
        StringBuilder updatePart = new StringBuilder();

        for (int i = 0; i < pairs.size(); i++) {
            cols.append(",").append(pairs.get(i).col());
            placeholders.append(",?");
            if (i > 0) updatePart.append(",");
            updatePart.append(pairs.get(i).col());
            if (dbType.equals("postgresql")) {
                updatePart.append("=EXCLUDED.").append(pairs.get(i).col());
            } else {
                updatePart.append("=?");
            }
        }

        return switch (dbType) {
            case "sqlite" ->
                "INSERT OR REPLACE INTO " + table + " (" + cols + ") VALUES (" + placeholders + ")";
            case "postgresql" ->
                "INSERT INTO " + table + " (" + cols + ") VALUES (" + placeholders + ")" +
                " ON CONFLICT (" + keyCol + ") DO UPDATE SET " + updatePart;
            default ->
                "INSERT INTO " + table + " (" + cols + ") VALUES (" + placeholders + ")" +
                " ON DUPLICATE KEY UPDATE " + updatePart;
        };
    }
}