package com.egor201.datenizen.events;

import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;

public class DbExecutedEvent extends ScriptEvent {

    // <--[event]
    // @Events
    // db executed
    //
    // @Group Datenizen
    //
    // @Cancellable false
    //
    // @Switch id:<id> to only fire for a specific database id.
    // @Switch label:<label> to only fire for a specific label.
    //
    // @Triggers when an async db_execute or db_table_insert/update/delete completes successfully.
    //
    // @Context
    // <context.id> returns the ElementTag of the database ID.
    // <context.label> returns the ElementTag of the label passed to the command, or empty if not set.
    // <context.affected_rows> returns the ElementTag of how many rows were affected.
    // <context.generated_key> returns the generated key/value when 'returning' was used, or empty.
    // <context.duration_ms> returns how long the statement took in milliseconds.
    //
    // -->
    public static DbExecutedEvent instance;
    private ElementTag id;
    private ElementTag label;
    private ElementTag affectedRows;
    private ElementTag generatedKey;
    private ElementTag durationMs;

    public DbExecutedEvent() {
        instance = this;
        registerCouldMatcher("db executed");
        registerSwitches("id", "label");
    }

    @Override
    public boolean matches(ScriptPath path) {
        if (!runGenericSwitchCheck(path, "id", id.asString())) return false;
        if (!runGenericSwitchCheck(path, "label", label.asString())) return false;
        return super.matches(path);
    }

    @Override
    public ObjectTag getContext(String name) {
        return switch (name) {
            case "id" -> id;
            case "label" -> label;
            case "affected_rows" -> affectedRows;
            case "generated_key" -> generatedKey;
            case "duration_ms" -> durationMs;
            default -> super.getContext(name);
        };
    }

    public void fireFor(String id, String label, int affectedRows) {
        fireFor(id, label, affectedRows, null, 0L);
    }

    public void fireFor(String id, String label, int affectedRows, String generatedKey, long durationMs) {
        this.id = new ElementTag(id);
        this.label = new ElementTag(label != null ? label : "");
        this.affectedRows = new ElementTag(affectedRows);
        this.generatedKey = new ElementTag(generatedKey != null ? generatedKey : "");
        this.durationMs = new ElementTag(durationMs);
        fire();
    }
}