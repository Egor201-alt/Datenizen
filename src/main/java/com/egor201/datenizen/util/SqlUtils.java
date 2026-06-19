package com.egor201.datenizen.util;

import java.util.ArrayList;
import java.util.List;

public final class SqlUtils {

    private SqlUtils() {}

    public static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

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
                } else if (c == ',') {
                    fields.add(current.toString().trim());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString().trim());
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
