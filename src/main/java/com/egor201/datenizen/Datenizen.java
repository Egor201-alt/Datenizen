package com.egor201.datenizen;

import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.events.ScriptEvent;
import com.egor201.datenizen.commands.*;
import com.egor201.datenizen.database.DatabaseManager;
import com.egor201.datenizen.events.*;
import com.egor201.datenizen.tags.DatenizenTags;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Datenizen extends JavaPlugin {

    private static Datenizen instance;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        instance = this;

        if (Bukkit.getPluginManager().getPlugin("Denizen") == null) {
            getLogger().severe("Denizen not found! Disabling Datenizen.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        databaseManager = new DatabaseManager();

        DenizenCore.commandRegistry.registerCommand(DbConnectCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbReconnectCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbDisconnectCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbExecuteCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbExecuteSyncCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbExecuteAsyncListCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbExecuteBatchCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbExecuteScriptCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbTransactionCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbBackupCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbImportCsvCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbExportCsvCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbDropTableCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbTableCreateCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbTableInsertCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbTableUpdateCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbTableDeleteCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbUpsertCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbSetPoolSizeCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbTimeoutCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbCleanPoolCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbAnalyzeCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbQueryAsyncCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbRegisterCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbRunCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbMigrateCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbUpsertBatchCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbConnectFileCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbTableCopyCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbClearCacheCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbAddColumnCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbDropColumnCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbRenameTableCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbCreateIndexCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbDropIndexCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbExecuteIfCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbExportJsonCommand.class);
        DenizenCore.commandRegistry.registerCommand(DbListenCommand.class);

        ScriptEvent.registerScriptEvent(new DbConnectedEvent());
        ScriptEvent.registerScriptEvent(new DbDisconnectedEvent());
        ScriptEvent.registerScriptEvent(new DbErrorEvent());
        ScriptEvent.registerScriptEvent(new DbExecutedEvent());
        ScriptEvent.registerScriptEvent(new DbTransactionExpiredEvent());
        ScriptEvent.registerScriptEvent(new DbConnectionLeakedEvent());
        ScriptEvent.registerScriptEvent(new DbCsvImportedEvent());
        ScriptEvent.registerScriptEvent(new DbCsvExportedEvent());
        ScriptEvent.registerScriptEvent(new DbBackedUpEvent());
        ScriptEvent.registerScriptEvent(new DbQueriedEvent());
        ScriptEvent.registerScriptEvent(new DbMigratedEvent());
        ScriptEvent.registerScriptEvent(new DbJsonExportedEvent());
        ScriptEvent.registerScriptEvent(new DbNotifyEvent());

        DatenizenTags.register();

        getLogger().info("Datenizen enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.closeAllConnections();
        }
        getLogger().info("Datenizen disabled. All connections closed.");
    }

    public static Datenizen getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}