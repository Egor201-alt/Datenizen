package com.egor201.datenizen.util;

import java.util.regex.Pattern;

/**
 * Single source of truth for SQL identifier validation.
 *
 * Every command used to declare its own copy of this pattern; they all now
 * point here so the rule can never drift between commands.
 */
public final class Identifiers {

    /** Table, column, index and savepoint names must match this. */
    public static final Pattern PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private Identifiers() {}

    public static boolean isValid(String name) {
        return name != null && PATTERN.matcher(name).matches();
    }

    /** Returns the first whitespace-delimited token, or "" for blank input. */
    public static String firstWord(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        int end = 0;
        while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))) {
            end++;
        }
        return trimmed.substring(0, end);
    }
}
