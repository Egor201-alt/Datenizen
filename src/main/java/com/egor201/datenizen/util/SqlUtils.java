package com.egor201.datenizen.util;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SqlUtils {

    private static final Pattern SAFE_ORDER = Pattern.compile(
        "^\\s*[a-zA-Z0-9_]+(?:\\s+(?:ASC|DESC))?(?:\\s*,\\s*[a-zA-Z0-9_]+(?:\\s+(?:ASC|DESC))?)*\\s*$",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DURATION = Pattern.compile(
        "^(\\d+)\\s*(ms|s|m|h|d)?$",
        Pattern.CASE_INSENSITIVE
    );

    private SqlUtils() {}

    /**
     * Parses a ListTag of MapTags (or map-form strings) into MapTags.
     * Returns null when any entry is not a valid map.
     */
    public static List<MapTag> parseMapRows(ListTag rows) {
        if (rows == null) return null;
        List<MapTag> parsed = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ObjectTag objectForm = (rows.objectForms != null && i < rows.objectForms.size())
                ? rows.objectForms.get(i)
                : null;
            MapTag map = objectForm instanceof MapTag
                ? (MapTag) objectForm
                : MapTag.valueOf(rows.get(i), null);
            if (map == null) return null;
            parsed.add(map);
        }
        return parsed;
    }

    /**
     * Builds a SELECT statement from already validated parts.
     * where/order are raw fragments supplied by a trusted script; identifiers
     * passed as lists are validated by the caller.
     */
    public static String buildSelect(String table, ListTag columns, String where,
                                     String order, Integer limit, Integer offset) {
        StringBuilder sql = new StringBuilder("SELECT ");
        if (columns == null || columns.size() == 0) {
            sql.append("*");
        } else {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append(columns.get(i));
            }
        }
        sql.append(" FROM ").append(table);
        if (where != null && !where.isBlank()) {
            sql.append(" WHERE ").append(where);
        }
        if (order != null && !order.isBlank()) {
            sql.append(" ORDER BY ").append(order);
        }
        if (limit != null && limit >= 0) {
            sql.append(" LIMIT ").append(limit);
        }
        if (offset != null && offset > 0) {
            sql.append(" OFFSET ").append(offset);
        }
        return sql.toString();
    }

    /** Allows "col", "col ASC", "col DESC, other ASC" and nothing else. */
    public static boolean isSafeOrder(String order) {
        return order != null && SAFE_ORDER.matcher(order).matches();
    }

    /**
     * Conservative guard for destructive statements.
     * Enabled only when defaults.allow_unsafe_queries is false.
     */
    public static boolean isUnsafeQuery(String sql) {
        if (sql == null) return false;
        String normalized = stripLeadingComments(sql).trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) return false;

        if (normalized.startsWith("DROP") || normalized.startsWith("TRUNCATE")) {
            return true;
        }
        if (normalized.startsWith("DELETE") && !normalized.contains(" WHERE ")) {
            return true;
        }
        if (normalized.startsWith("UPDATE") && !normalized.contains(" WHERE ")) {
            return true;
        }
        return splitStatements(sql).size() > 1;
    }

    private static String stripLeadingComments(String sql) {
        String text = sql;
        while (true) {
            text = text.stripLeading();
            if (text.startsWith("--")) {
                int newline = text.indexOf('\n');
                if (newline < 0) return "";
                text = text.substring(newline + 1);
                continue;
            }
            if (text.startsWith("/*")) {
                int end = text.indexOf("*/", 2);
                if (end < 0) return "";
                text = text.substring(end + 2);
                continue;
            }
            return text;
        }
    }

    /** SHA-256 checksum used for migration files. */
    public static String checksum(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b & 0xFF));
            }
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Parses durations such as 500ms, 30s, 15m, 2h, 7d.
     * A bare number is interpreted as milliseconds.
     * Returns -1 for invalid input.
     */
    public static long parseDurationMillis(String input) {
        if (input == null) return -1;
        Matcher matcher = DURATION.matcher(input.trim());
        if (!matcher.matches()) return -1;
        try {
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            if (unit == null) return value;
            return switch (unit.toLowerCase(Locale.ROOT)) {
                case "ms" -> value;
                case "s" -> value * 1000L;
                case "m" -> value * 60_000L;
                case "h" -> value * 3_600_000L;
                case "d" -> value * 86_400_000L;
                default -> -1;
            };
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean wasQuoted = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                    wasQuoted = true;
                } else if (c == ',') {
                    fields.add(wasQuoted ? current.toString() : current.toString().trim());
                    current.setLength(0);
                    wasQuoted = false;
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(wasQuoted ? current.toString() : current.toString().trim());
        return fields;
    }

    public static List<String> splitStatements(String content) {
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
}
