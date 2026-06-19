package com.egor201.datenizen.events;

import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;

public class DbQueriedEvent extends ScriptEvent {

    // <--[event]
    // @Events
    // db queried
    //
    // @Group Datenizen
    //
    // @Cancellable false
    //
    // @Switch id:<id> to only fire for a specific database id.
    // @Switch label:<label> to only fire for a specific label.
    //
    // @Triggers when db_query_async or db_run (SELECT) completes successfully.
    //
    // @Context
    // <context.id> returns the database ID.
    // <context.label> returns the label passed to the command, or empty if not set.
    // <context.rows> returns a ListTag of MapTags with the query results.
    // -->

    public static DbQueriedEvent instance;
    private ElementTag id;
    private ElementTag label;
    private ListTag rows;

    public DbQueriedEvent() {
        instance = this;
        registerCouldMatcher("db queried");
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
            case "id"    -> id;
            case "label" -> label;
            case "rows"  -> rows;
            default      -> super.getContext(name);
        };
    }

    public void fireFor(String id, String label, ListTag rows) {
        this.id    = new ElementTag(id);
        this.label = new ElementTag(label != null ? label : "");
        this.rows  = rows != null ? rows : new ListTag();
        fire();
    }
}
