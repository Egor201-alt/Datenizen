package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.objects.core.ListTag;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.Settings;
import com.egor201.datenizen.events.DbErrorEvent;
import com.egor201.datenizen.events.DbExecutedEvent;
import com.egor201.datenizen.events.DbQueriedEvent;
import com.egor201.datenizen.util.SqlUtils;
import org.bukkit.Bukkit;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Small shared helpers for the newer commands: error reporting, event firing,
 * timeout/fetch-size application and the destructive-statement guard.
 */
public final class DbCommandSupport {

    private DbCommandSupport() {}

    public static void error(String id, String message, String source) {
        error(id, message, null, source);
    }

    public static void error(String id, String message, String sqlState, String source) {
        Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
            DbErrorEvent.instance.fireFor(id, message, sqlState, source)
        );
    }

    public static void error(String id, Throwable error, String sql) {
        String state = error instanceof SQLException ? ((SQLException) error).getSQLState() : null;
        Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
            DbErrorEvent.instance.fireFor(id, error != null ? error.getMessage() : "Unknown error", state, sql)
        );
    }

    public static void executed(String id, String label, int affectedRows, String generatedKey, long durationMs) {
        Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
            DbExecutedEvent.instance.fireFor(id, label, affectedRows, generatedKey, durationMs)
        );
    }

    public static void queried(String id, String label, ListTag rows, long durationMs) {
        Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
            DbQueriedEvent.instance.fireFor(id, label, rows, durationMs)
        );
    }

    /** Returns false and reports an error when the statement is blocked by the safety guard. */
    public static boolean allowUnsafe(String id, String sql, boolean allowUnsafe, String source) {
        if (Settings.allowUnsafeQueries || allowUnsafe) return true;
        if (!SqlUtils.isUnsafeQuery(sql)) return true;
        error(id, "Blocked unsafe SQL statement. Add allow_unsafe:true or set defaults.allow_unsafe_queries: true in config.yml.", source);
        return false;
    }

    /** Applies the per-call timeout, the configured default timeout and fetch size. */
    public static void applySettings(PreparedStatement statement, long timeoutMs) throws SQLException {
        if (timeoutMs > 0) {
            statement.setQueryTimeout((int) Math.max(1L, (timeoutMs + 999L) / 1000L));
        } else if (Settings.queryTimeoutSeconds() > 0) {
            statement.setQueryTimeout(Settings.queryTimeoutSeconds());
        }
        if (Settings.fetchSize > 0) {
            statement.setFetchSize(Settings.fetchSize);
        }
    }

    public static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    public static void log(String sql) {
        if (Settings.logQueries && Datenizen.getInstance() != null) {
            Datenizen.getInstance().getLogger().info("[SQL] " + sql);
        }
    }
}
