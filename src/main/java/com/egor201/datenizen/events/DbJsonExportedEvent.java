package com.egor201.datenizen.events;

import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;

public class DbJsonExportedEvent extends ScriptEvent {
    // <--[event]
    // @Events
    // db json exported
    // @Group Datenizen
    // @Context
    // <context.id> returns the database ID.
    // <context.path> returns the path of the saved file.
    // -->
    public static DbJsonExportedEvent instance;
    private ElementTag id, path;

    public DbJsonExportedEvent() { instance = this; registerCouldMatcher("db json exported"); }
    @Override public ObjectTag getContext(String name) { return name.equals("id") ? id : (name.equals("path") ? path : super.getContext(name)); }
    public void fireFor(String id, String path) { this.id = new ElementTag(id); this.path = new ElementTag(path); fire(); }
}
