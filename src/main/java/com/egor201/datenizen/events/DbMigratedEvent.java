package com.egor201.datenizen.events;

import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;

public class DbMigratedEvent extends ScriptEvent {

    // <--[event]
    // @Events
    // db migrated
    //
    // @Group Datenizen
    //
    // @Cancellable false
    //
    // @Switch id:<id> to only fire for a specific database id.
    //
    // @Triggers when db_migrate completes successfully.
    //
    // @Context
    // <context.id> returns the database ID.
    // <context.count> returns how many migration files were applied.
    // <context.path> returns the folder path scanned for migrations.
    // -->

    public static DbMigratedEvent instance;
    private ElementTag id;
    private ElementTag count;
    private ElementTag path;

    public DbMigratedEvent() {
        instance = this;
        registerCouldMatcher("db migrated");
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
            case "id"    -> id;
            case "count" -> count;
            case "path"  -> path;
            default      -> super.getContext(name);
        };
    }

    public void fireFor(String id, int count, String path) {
        this.id    = new ElementTag(id);
        this.count = new ElementTag(count);
        this.path  = new ElementTag(path);
        fire();
    }
}
