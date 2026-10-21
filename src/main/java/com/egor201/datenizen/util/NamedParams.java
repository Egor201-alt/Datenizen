package com.egor201.datenizen.util;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.utilities.text.StringHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Converts named SQL placeholders (:name) into ordinary JDBC placeholders (?).
 *
 * The scanner understands single quotes, double quotes, backticks, line comments
 * and block comments, so a colon inside a string literal or comment is never
 * treated as a parameter. PostgreSQL casts (::type) are preserved.
 */
public final class NamedParams {

    public record Bound(String sql, List<String> values) {}

    private NamedParams() {}

    public static Bound bind(String sql, MapTag namedArgs) {
        if (namedArgs == null || namedArgs.map.isEmpty()) {
            return new Bound(sql, Collections.emptyList());
        }

        StringBuilder out = new StringBuilder(sql.length());
        List<String> values = new ArrayList<>();

        boolean inSingle = false;
        boolean inDouble = false;
        boolean inBacktick = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = (i + 1 < sql.length()) ? sql.charAt(i + 1) : 0;

            if (inLineComment) {
                out.append(c);
                if (c == '\n') inLineComment = false;
                continue;
            }

            if (inBlockComment) {
                out.append(c);
                if (c == '*' && next == '/') {
                    out.append(next);
                    i++;
                    inBlockComment = false;
                }
                continue;
            }

            if (inSingle) {
                out.append(c);
                if (c == '\'') {
                    if (next == '\'') {
                        out.append(next);
                        i++;
                    } else {
                        inSingle = false;
                    }
                }
                continue;
            }

            if (inDouble) {
                out.append(c);
                if (c == '"') {
                    if (next == '"') {
                        out.append(next);
                        i++;
                    } else {
                        inDouble = false;
                    }
                }
                continue;
            }

            if (inBacktick) {
                out.append(c);
                if (c == '`') {
                    if (next == '`') {
                        out.append(next);
                        i++;
                    } else {
                        inBacktick = false;
                    }
                }
                continue;
            }

            if (c == '\'') { inSingle = true; out.append(c); continue; }
            if (c == '"')  { inDouble = true; out.append(c); continue; }
            if (c == '`')  { inBacktick = true; out.append(c); continue; }

            if (c == '-' && next == '-') {
                inLineComment = true;
                out.append(c);
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                out.append(c);
                continue;
            }

            // Keep PostgreSQL casts and the previous colon of a :: pair intact.
            if (c == ':') {
                boolean previousColon = i > 0 && sql.charAt(i - 1) == ':';
                if (next == ':' || previousColon || i + 1 >= sql.length() || !isNameStart(next)) {
                    out.append(c);
                    continue;
                }

                int end = i + 1;
                while (end < sql.length() && isNamePart(sql.charAt(end))) {
                    end++;
                }
                String name = sql.substring(i + 1, end);
                ObjectTag value = namedArgs.map.get(new StringHolder(name));
                if (value == null) {
                    throw new IllegalArgumentException("Missing named argument: :" + name);
                }
                out.append('?');
                values.add(value.toString());
                i = end - 1;
                continue;
            }

            out.append(c);
        }

        return new Bound(out.toString(), values);
    }

    private static boolean isNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
