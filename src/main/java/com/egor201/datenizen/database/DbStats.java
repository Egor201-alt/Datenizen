package com.egor201.datenizen.database;

import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.egor201.datenizen.Datenizen;
import com.egor201.datenizen.events.DbSlowQueryEvent;
import org.bukkit.Bukkit;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-database counters, the most recent error and a bounded slow-query history.
 */
public final class DbStats {

    public record SlowQuery(long at, String sql, long ms) {}
    public record LastError(long at, String message, String sqlState, String sql) {}

    private static final int MAX_SLOW_QUERIES = 20;

    private final String id;
    private final AtomicLong queries = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final AtomicLong totalMs = new AtomicLong();
    private final AtomicLong maxMs = new AtomicLong();
    private final AtomicLong slowQueriesCount = new AtomicLong();
    private final ConcurrentLinkedDeque<SlowQuery> slowQueries = new ConcurrentLinkedDeque<>();
    private volatile LastError lastError;

    public DbStats(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void recordQuery(long ms, boolean success, String sql, long slowThresholdMs) {
        queries.incrementAndGet();
        totalMs.addAndGet(Math.max(0L, ms));
        maxMs.accumulateAndGet(ms, Math::max);
        if (!success) errors.incrementAndGet();

        if (slowThresholdMs > 0 && ms >= slowThresholdMs) {
            slowQueriesCount.incrementAndGet();
            slowQueries.addFirst(new SlowQuery(System.currentTimeMillis(), sql, ms));
            while (slowQueries.size() > MAX_SLOW_QUERIES) {
                slowQueries.removeLast();
            }
            if (Datenizen.getInstance() != null) {
                Bukkit.getScheduler().runTask(Datenizen.getInstance(), () ->
                    DbSlowQueryEvent.instance.fireFor(id, sql, ms)
                );
            }
        }
    }

    public void recordError(Throwable error, String sql) {
        String state = error instanceof java.sql.SQLException
            ? ((java.sql.SQLException) error).getSQLState()
            : null;
        lastError = new LastError(
            System.currentTimeMillis(),
            error != null && error.getMessage() != null ? error.getMessage() : "Unknown error",
            state,
            sql
        );
    }

    public MapTag toMap() {
        MapTag map = new MapTag();
        long count = queries.get();
        map.putObject("id", new ElementTag(id));
        map.putObject("queries", new ElementTag(count));
        map.putObject("errors", new ElementTag(errors.get()));
        map.putObject("avg_ms", new ElementTag(count == 0 ? 0 : totalMs.get() / count));
        map.putObject("max_ms", new ElementTag(maxMs.get()));
        map.putObject("slow_queries", new ElementTag(slowQueriesCount.get()));
        map.putObject("last_error_age_ms", new ElementTag(lastError == null
            ? -1
            : System.currentTimeMillis() - lastError.at()));
        return map;
    }

    public ListTag slowQueries() {
        ListTag list = new ListTag();
        for (SlowQuery query : slowQueries) {
            MapTag map = new MapTag();
            map.putObject("at", new ElementTag(query.at()));
            map.putObject("ms", new ElementTag(query.ms()));
            map.putObject("sql", new ElementTag(query.sql()));
            list.addObject(map);
        }
        return list;
    }

    public MapTag lastError() {
        if (lastError == null) return null;
        MapTag map = new MapTag();
        map.putObject("at", new ElementTag(lastError.at()));
        map.putObject("message", new ElementTag(lastError.message()));
        map.putObject("sql_state", new ElementTag(lastError.sqlState() != null ? lastError.sqlState() : ""));
        map.putObject("sql", new ElementTag(lastError.sql() != null ? lastError.sql() : ""));
        return map;
    }
}
