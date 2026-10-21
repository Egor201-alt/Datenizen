package com.egor201.datenizen.events;

import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;

public class DbSlowQueryEvent extends ScriptEvent {

    // <--[event]
    // @Events
    // db slow query
    //
    // @Group Datenizen
    //
    // @Cancellable false
    //
    // @Switch id:<id> to only fire for a specific database id.
    //
    // @Triggers when a query takes at least defaults.slow_query_ms milliseconds.
    //
    // @Context
    // <context.id> returns the database ID.
    // <context.query> returns the SQL query that was slow.
    // <context.duration_ms> returns the measured duration in milliseconds.
    // -->
    public static DbSlowQueryEvent instance;

    private ElementTag id;
    private ElementTag query;
    private ElementTag durationMs;

    public DbSlowQueryEvent() {
        instance = this;
        registerCouldMatcher("db slow query");
        registerSwitches("id");
    }

    @Override
    public boolean matches(ScriptPath path) {
        if (!runGenericSwitchCheck(path, "id", id.asString())) return false;
        return super.matches(path);
    }

    @Override
    public ObjectTag getContext(String name) {
        return switch (name) {
            case "id" -> id;
            case "query" -> query;
            case "duration_ms" -> durationMs;
            default -> super.getContext(name);
        };
    }

    public void fireFor(String id, String query, long durationMs) {
        this.id = new ElementTag(id);
        this.query = new ElementTag(query != null ? query : "");
        this.durationMs = new ElementTag(durationMs);
        fire();
    }
}
