package com.egor201.datenizen.admin;

import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.utilities.text.StringHolder;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.Settings;
import com.egor201.datenizen.database.DatabaseManager;
import com.egor201.datenizen.database.DbStats;
import com.egor201.datenizen.database.ResultMapper;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DatenizenAdminCommand implements CommandExecutor, TabCompleter {

    private static final Set<String> READ_COMMANDS =
        Set.of("SELECT", "WITH", "SHOW", "DESCRIBE", "DESC", "PRAGMA", "EXPLAIN");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("datenizen.admin")) {
            sender.sendMessage("[Datenizen] You do not have permission to use this command.");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender);
            case "pools" -> pools(sender);
            case "test" -> test(sender, args);
            case "tables" -> tables(sender, args);
            case "query" -> query(sender, args);
            case "reload" -> reload(sender);
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage("[Datenizen] /datenizen status - show databases and counters");
        sender.sendMessage("[Datenizen] /datenizen pools - show pool details");
        sender.sendMessage("[Datenizen] /datenizen test <id> - test a connection");
        sender.sendMessage("[Datenizen] /datenizen tables <id> - list tables");
        sender.sendMessage("[Datenizen] /datenizen query <id> <SELECT ...> - run a read-only query");
        sender.sendMessage("[Datenizen] /datenizen reload - reload config.yml");
    }

    private void status(CommandSender sender) {
        DatabaseManager manager = Datenizen.getInstance().getDatabaseManager();
        if (manager.getActiveIds().isEmpty()) {
            sender.sendMessage("[Datenizen] No active databases.");
            return;
        }
        for (String id : manager.getActiveIds()) {
            HikariDataSource ds = manager.getDataSource(id);
            DbStats stats = manager.getStats(id);
            long queries = 0;
            long errors = 0;
            if (stats != null) {
                MapTag map = stats.toMap();
                queries = Long.parseLong(map.map.get(new StringHolder("queries")).toString());
                errors = Long.parseLong(map.map.get(new StringHolder("errors")).toString());
            }
            sender.sendMessage("[Datenizen] " + id + " | type=" + manager.getDatabaseType(id)
                + " | open=" + (ds != null && !ds.isClosed())
                + " | queries=" + queries + " | errors=" + errors);
        }
    }

    private void pools(CommandSender sender) {
        DatabaseManager manager = Datenizen.getInstance().getDatabaseManager();
        if (manager.getActiveIds().isEmpty()) {
            sender.sendMessage("[Datenizen] No active databases.");
            return;
        }
        for (String id : manager.getActiveIds()) {
            HikariDataSource ds = manager.getDataSource(id);
            if (ds == null) continue;
            HikariPoolMXBean mx = ds.getHikariPoolMXBean();
            if (mx == null) {
                sender.sendMessage("[Datenizen] " + id + " | pool metrics unavailable");
                continue;
            }
            sender.sendMessage("[Datenizen] " + id + " | active=" + mx.getActiveConnections()
                + " idle=" + mx.getIdleConnections()
                + " total=" + mx.getTotalConnections()
                + " waiting=" + mx.getThreadsAwaitingConnection());
        }
    }

    private void test(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("[Datenizen] Usage: /datenizen test <id>");
            return;
        }
        String id = args[1];
        HikariDataSource ds = Datenizen.getInstance().getDatabaseManager().getDataSource(id);
        if (ds == null) {
            sender.sendMessage("[Datenizen] Database '" + id + "' is not connected.");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            boolean ok = false;
            long start = System.nanoTime();
            try (Connection conn = ds.getConnection()) {
                ok = conn.isValid(2);
            } catch (Exception ignored) {}
            long ms = (System.nanoTime() - start) / 1_000_000L;
            final boolean result = ok;
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                sender.sendMessage("[Datenizen] " + id + " -> " + (result ? "OK" : "FAILED") + " (" + ms + " ms)")
            );
        });
    }

    private void tables(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("[Datenizen] Usage: /datenizen tables <id>");
            return;
        }
        String id = args[1];
        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            List<String> tables = new ArrayList<>();
            try (Connection conn = Datenizen.getInstance().getDatabaseManager().getConnection(id)) {
                DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                    while (rs.next()) tables.add(rs.getString("TABLE_NAME"));
                }
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    sender.sendMessage("[Datenizen] Failed to list tables: " + e.getMessage())
                );
                return;
            }
            Bukkit.getScheduler().runTask(Datenizen.getInstance(), () -> {
                sender.sendMessage("[Datenizen] Tables in " + id + ": " + String.join(", ", tables));
            });
        });
    }

    private void query(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("[Datenizen] Usage: /datenizen query <id> <SELECT ...>");
            return;
        }
        String id = args[1];
        String sql = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        String first = sql.trim().split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
        if (!READ_COMMANDS.contains(first)) {
            sender.sendMessage("[Datenizen] Only read-only queries are allowed here.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(Datenizen.getInstance(), () -> {
            DatabaseManager manager = Datenizen.getInstance().getDatabaseManager();
            long start = System.nanoTime();
            try (Connection conn = manager.getConnection(id);
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                ListTag rows = ResultMapper.rows(rs);
                long duration = (System.nanoTime() - start) / 1_000_000L;
                manager.recordQuery(id, duration, true, sql);

                List<String> lines = new ArrayList<>();
                int limit = Math.min(20, rows.size());
                for (int i = 0; i < limit; i++) {
                    MapTag row = MapTag.valueOf(rows.get(i), null);
                    if (row == null) continue;
                    StringBuilder line = new StringBuilder();
                    for (StringHolder holder : row.map.keySet()) {
                        if (line.length() > 0) line.append(", ");
                        line.append(holder.str).append('=').append(row.map.get(holder));
                    }
                    lines.add(line.toString());
                }

                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () -> {
                    sender.sendMessage("[Datenizen] " + rows.size() + " row(s), " + duration + " ms"
                        + (rows.size() > limit ? " (showing first " + limit + ")" : ""));
                    for (String line : lines) sender.sendMessage("  " + line);
                });
            } catch (Exception e) {
                long duration = (System.nanoTime() - start) / 1_000_000L;
                manager.recordQuery(id, duration, false, sql);
                manager.recordError(id, e, sql);
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    sender.sendMessage("[Datenizen] Query failed: " + e.getMessage())
                );
            }
        });
    }

    private void reload(CommandSender sender) {
        Datenizen.getInstance().reloadConfig();
        Settings.load();
        sender.sendMessage("[Datenizen] config.yml reloaded.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("datenizen.admin")) return List.of();
        if (args.length == 1) {
            return filter(List.of("status", "pools", "test", "tables", "query", "reload"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("test")
            || args[0].equalsIgnoreCase("tables")
            || args[0].equalsIgnoreCase("query"))) {
            return filter(new ArrayList<>(Datenizen.getInstance().getDatabaseManager().getActiveIds()), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                result.add(value);
            }
        }
        return result;
    }
}
