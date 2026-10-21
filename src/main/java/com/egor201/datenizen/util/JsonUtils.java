package com.egor201.datenizen.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * Minimal JSON path reader used by the KV/document tags.
 *
 * Supports dotted object keys and numeric array indexes, for example:
 *   profile.stats.coins
 *   items.0.name
 */
public final class JsonUtils {

    private JsonUtils() {}

    public static String getPath(String json, String path) {
        JsonElement value = getElement(json, path);
        if (value == null || value.isJsonNull()) return null;
        return value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }

    public static JsonElement getElement(String json, String path) {
        if (json == null) return null;
        try {
            JsonElement current = JsonParser.parseString(json);
            if (path == null || path.isBlank()) return current;

            for (String part : path.split("\\.")) {
                if (current == null || current.isJsonNull()) return null;
                if (current.isJsonObject()) {
                    current = current.getAsJsonObject().get(part);
                } else if (current.isJsonArray()) {
                    int index = Integer.parseInt(part);
                    if (index < 0 || index >= current.getAsJsonArray().size()) return null;
                    current = current.getAsJsonArray().get(index);
                } else {
                    return null;
                }
            }
            return current;
        } catch (Exception e) {
            return null;
        }
    }
}
