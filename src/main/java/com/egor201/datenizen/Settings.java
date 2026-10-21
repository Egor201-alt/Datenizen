package com.egor201.datenizen;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * Central plugin settings loaded from config.yml.
 *
 * All values have safe defaults so the plugin still works with an old or empty config.
 */
public final class Settings {

    public static int poolMax = 10;
    public static int poolMin = 2;
    public static long connectionTimeoutMs = 10_000L;
    public static long queryTimeoutMs = 0L;
    public static long slowQueryMs = 500L;
    public static boolean allowUnsafeQueries = true;
    public static boolean sandboxPaths = false;
    public static boolean cacheInvalidateOnWrite = true;
    public static boolean logQueries = false;
    public static boolean migrationChecksums = true;
    public static int fetchSize = 0;

    private Settings() {}

    public static void load() {
        Datenizen plugin = Datenizen.getInstance();
        if (plugin == null) return;

        FileConfiguration c = plugin.getConfig();
        poolMax = Math.max(1, c.getInt("defaults.pool_max", 10));
        poolMin = Math.max(0, Math.min(poolMax, c.getInt("defaults.pool_min", 2)));
        connectionTimeoutMs = Math.max(1_000L, c.getLong("defaults.connection_timeout_ms", 10_000L));
        queryTimeoutMs = Math.max(0L, c.getLong("defaults.query_timeout_ms", 0L));
        slowQueryMs = Math.max(0L, c.getLong("defaults.slow_query_ms", 500L));
        allowUnsafeQueries = c.getBoolean("defaults.allow_unsafe_queries", true);
        sandboxPaths = c.getBoolean("defaults.sandbox_paths", false);
        cacheInvalidateOnWrite = c.getBoolean("defaults.cache_invalidate_on_write", true);
        logQueries = c.getBoolean("defaults.log_queries", false);
        migrationChecksums = c.getBoolean("defaults.migration_checksums", true);
        fetchSize = Math.max(0, c.getInt("defaults.fetch_size", 0));
    }

    /** JDBC query timeout is expressed in whole seconds. 0 means "no timeout". */
    public static int queryTimeoutSeconds() {
        if (queryTimeoutMs <= 0L) return 0;
        return (int) Math.max(1L, (queryTimeoutMs + 999L) / 1000L);
    }

    /**
     * Resolves a user supplied path.
     *
     * When sandbox_paths is disabled the path is used as-is (old behaviour).
     * When enabled, relative paths are resolved inside the plugin data folder and
     * absolute paths outside of it are rejected by falling back to a safe filename.
     */
    public static File resolvePath(String path) {
        File raw = new File(path);
        if (!sandboxPaths) return raw;

        Datenizen plugin = Datenizen.getInstance();
        File base = plugin != null ? plugin.getDataFolder() : new File("plugins/Datenizen");

        if (!raw.isAbsolute()) {
            File resolved = new File(base, path);
            if (isInside(base, resolved)) return resolved;
            return new File(base, raw.getName());
        }

        if (isInside(base, raw)) return raw;
        return new File(base, raw.getName());
    }

    private static boolean isInside(File base, File file) {
        try {
            String basePath = base.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            return filePath.equals(basePath) || filePath.startsWith(basePath + File.separator);
        } catch (IOException e) {
            return false;
        }
    }
}
