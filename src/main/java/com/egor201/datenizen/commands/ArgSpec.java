package com.egor201.datenizen.commands;

import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;

/**
 * Declarative description of a single db_* command argument.
 *
 * This replaces the prefix/type handling that used to be copy-pasted into
 * every command's parseArgs method.
 */
public final class ArgSpec {

    public enum Kind { ELEMENT, LIST, MAP }

    public final String name;
    public final Kind kind;
    public final boolean required;
    private final boolean looseSql;

    private ArgSpec(String name, Kind kind, boolean required, boolean looseSql) {
        this.name = name;
        this.kind = kind;
        this.required = required;
        this.looseSql = looseSql;
    }

    public static ArgSpec element(String name) {
        return new ArgSpec(name, Kind.ELEMENT, true, false);
    }

    public static ArgSpec optionalElement(String name) {
        return new ArgSpec(name, Kind.ELEMENT, false, false);
    }

    public static ArgSpec list(String name) {
        return new ArgSpec(name, Kind.LIST, true, false);
    }

    public static ArgSpec optionalList(String name) {
        return new ArgSpec(name, Kind.LIST, false, false);
    }

    public static ArgSpec map(String name) {
        return new ArgSpec(name, Kind.MAP, true, false);
    }

    public static ArgSpec optionalMap(String name) {
        return new ArgSpec(name, Kind.MAP, false, false);
    }

    /**
     * A required sql argument that additionally accepts the legacy quoted form
     * "sql:SELECT ..." (the whole argument quoted, so Denizen does not parse it as a prefix).
     */
    public static ArgSpec sql(String name) {
        return new ArgSpec(name, Kind.ELEMENT, true, true);
    }

    boolean matches(Argument arg) {
        if (arg.matchesPrefix(name)) {
            return true;
        }
        return looseSql && !arg.hasPrefix() && arg.getValue().startsWith("sql:");
    }

    ObjectTag parse(Argument arg) {
        if (looseSql && !arg.hasPrefix()) {
            return new ElementTag(arg.getValue().substring(4));
        }
        return switch (kind) {
            case LIST -> arg.asType(ListTag.class);
            case MAP -> arg.asType(MapTag.class);
            default -> arg.asElement();
        };
    }
}
