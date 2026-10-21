package com.egor201.datenizen.database;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.utilities.text.StringHolder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

/**
 * Converts JDBC results into Denizen tags.
 *
 * Previously every command and tag had its own copy of the same
 * ResultSet -> MapTag / ListTag / JSON loops.
 */
public final class ResultMapper {

    private ResultMapper() {}

    public static void bind(PreparedStatement statement, ListTag args) throws SQLException {
        if (args == null) {
            return;
        }
        for (int i = 0; i < args.size(); i++) {
            statement.setObject(i + 1, args.get(i));
        }
    }

    /** Binds an ordered list of string values (used by named parameters). */
    public static void bindValues(PreparedStatement statement, List<String> values) throws SQLException {
        if (values == null) return;
        for (int i = 0; i < values.size(); i++) {
            statement.setObject(i + 1, values.get(i));
        }
    }

    /** Reads the current row of the ResultSet as a MapTag. */
    public static MapTag row(ResultSet rs) throws SQLException {
        return row(rs, rs.getMetaData());
    }

    private static MapTag row(ResultSet rs, ResultSetMetaData meta) throws SQLException {
        int cols = meta.getColumnCount();
        MapTag row = new MapTag();
        for (int i = 1; i <= cols; i++) {
            Object value = rs.getObject(i);
            row.putObject(meta.getColumnName(i), new ElementTag(value == null ? "null" : value.toString()));
        }
        return row;
    }

    /** Reads every remaining row as a ListTag of MapTags. */
    public static ListTag rows(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        ListTag result = new ListTag();
        while (rs.next()) {
            result.addObject(row(rs, meta));
        }
        return result;
    }

    /** Returns the next row as a MapTag, or null when there are no more rows. */
    public static MapTag firstRow(ResultSet rs) throws SQLException {
        return rs.next() ? row(rs) : null;
    }

    /** Returns the first column of the next row, or null when there are no more rows. */
    public static ElementTag firstValue(ResultSet rs) throws SQLException {
        if (!rs.next()) {
            return null;
        }
        Object value = rs.getObject(1);
        return new ElementTag(value == null ? "null" : value.toString());
    }

    /** Returns true when at least one row is available. */
    public static boolean hasRow(ResultSet rs) throws SQLException {
        return rs.next();
    }

    /** Builds a MapTag keyed by one of the result columns. */
    public static MapTag keyedRows(ResultSet rs, String keyColumn) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        MapTag result = new MapTag();
        while (rs.next()) {
            MapTag row = row(rs, meta);
            ObjectTag key = row.map.get(new StringHolder(keyColumn));
            if (key == null) {
                for (StringHolder holder : row.map.keySet()) {
                    if (holder.low.equalsIgnoreCase(keyColumn)) {
                        key = row.map.get(holder);
                        break;
                    }
                }
            }
            if (key != null) {
                result.putObject(key.toString(), row);
            }
        }
        return result;
    }

    /** Returns every value of the first column as a ListTag. */
    public static ListTag column(ResultSet rs) throws SQLException {
        ListTag result = new ListTag();
        while (rs.next()) {
            Object value = rs.getObject(1);
            result.addObject(new ElementTag(value == null ? "null" : value.toString()));
        }
        return result;
    }

    /** Converts the current row to a JsonObject (used by streaming exports). */
    public static JsonObject jsonObject(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        JsonObject object = new JsonObject();
        for (int i = 1; i <= cols; i++) {
            String column = meta.getColumnName(i);
            String value = rs.getString(i);
            if (value == null) {
                object.add(column, JsonNull.INSTANCE);
            } else {
                object.addProperty(column, value);
            }
        }
        return object;
    }

    /** Builds a MapTag from the first column (key) and second column (value) of every row. */
    public static MapTag keyValueMap(ResultSet rs) throws SQLException {
        MapTag map = new MapTag();
        while (rs.next()) {
            Object key = rs.getObject(1);
            Object value = rs.getObject(2);
            if (key != null) {
                map.putObject(key.toString(), new ElementTag(value == null ? "null" : value.toString()));
            }
        }
        return map;
    }

    /** Builds a JSON array from every remaining row, preserving SQL nulls as JSON null. */
    public static JsonArray json(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        JsonArray array = new JsonArray();
        while (rs.next()) {
            JsonObject object = new JsonObject();
            for (int i = 1; i <= cols; i++) {
                String column = meta.getColumnName(i);
                String value = rs.getString(i);
                if (value == null) {
                    object.add(column, JsonNull.INSTANCE);
                } else {
                    object.addProperty(column, value);
                }
            }
            array.add(object);
        }
        return array;
    }
}
