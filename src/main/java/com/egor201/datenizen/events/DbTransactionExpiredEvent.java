package com.egor201.datenizen.events;

import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;

public class DbTransactionExpiredEvent extends ScriptEvent {
    
    // <--[event]
    // @Events
    // db transaction expired
    // @Group Datenizen
    // @Context
    // <context.tx> returns the transaction ID.
    // <context.id> returns the database ID.
    // -->
    public static DbTransactionExpiredEvent instance;
    private ElementTag tx;
    private ElementTag id;

    public DbTransactionExpiredEvent() { instance = this; registerCouldMatcher("db transaction expired"); }
    @Override public ObjectTag getContext(String name) { return name.equals("tx") ? tx : (name.equals("id") ? id : super.getContext(name)); }
    public void fireFor(String txId, String dbId) { this.tx = new ElementTag(txId); this.id = new ElementTag(dbId != null ? dbId : ""); fire(); }
}