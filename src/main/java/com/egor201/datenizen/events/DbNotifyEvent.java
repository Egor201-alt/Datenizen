package com.egor201.datenizen.events;

import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;

public class DbNotifyEvent extends ScriptEvent {
    // <--[event]
    // @Events
    // db notify
    // @Group Datenizen
    // @Switch channel:<channel> to only fire for a specific PostgreSQL NOTIFY channel.
    // @Context
    // <context.id> returns the database ID.
    // <context.channel> returns the channel name.
    // <context.payload> returns the notification payload string.
    // -->
    public static DbNotifyEvent instance;
    private ElementTag id, channel, payload;

    public DbNotifyEvent() { instance = this; registerCouldMatcher("db notify"); registerSwitches("channel"); }

    @Override
    public boolean matches(ScriptPath path) {
        if (!runGenericSwitchCheck(path, "channel", channel.asString())) return false;
        return super.matches(path);
    }

    @Override
    public ObjectTag getContext(String name) {
        return switch (name) {
            case "id"      -> id;
            case "channel" -> channel;
            case "payload" -> payload;
            default        -> super.getContext(name);
        };
    }

    public void fireFor(String id, String channel, String payload) {
        this.id      = new ElementTag(id);
        this.channel = new ElementTag(channel);
        this.payload = new ElementTag(payload != null ? payload : "");
        fire();
    }
}
