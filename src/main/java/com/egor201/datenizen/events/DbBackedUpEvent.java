package com.egor201.datenizen.events;

import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;

public class DbBackedUpEvent extends ScriptEvent {

    // <--[event]
    // @Events
    // db backed up
    //
    // @Group Datenizen
    //
    // @Cancellable false
    //
    // @Switch id:<id> to only fire for a specific database id.
    //
    // @Triggers when db_backup completes successfully.
    //
    // @Context
    // <context.id> returns the database ID.
    // <context.path> returns the path where the backup was saved.
    //
    // @Usage
    // - on db backed up id:main:
    //   - narrate "Backup saved to <context.path>"
    // -->

    public static DbBackedUpEvent instance;
    private ElementTag id;
    private ElementTag path;

    public DbBackedUpEvent() {
        instance = this;
        registerCouldMatcher("db backed up");
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
            case "id"   -> id;
            case "path" -> path;
            default     -> super.getContext(name);
        };
    }

    public void fireFor(String id, String path) {
        this.id   = new ElementTag(id);
        this.path = new ElementTag(path);
        fire();
    }
}
