package com.egor201.datenizen;

import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.egor201.datenizen.admin.DatenizenAdminCommand;
import com.egor201.datenizen.commands.*;
import com.egor201.datenizen.database.DatabaseManager;
import com.egor201.datenizen.events.*;
import com.egor201.datenizen.tags.DatenizenTags;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

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

        saveDefaultConfig();
        Settings.load();

        databaseManager = new DatabaseManager();

        List<Class<? extends AbstractCommand>> commands = List.of(
            DbConnectCommand.class,
            DbReconnectCommand.class,
            DbDisconnectCommand.class,
            DbExecuteCommand.class,
            DbExecuteSyncCommand.class,
            DbExecuteAsyncListCommand.class,
            DbExecuteBatchCommand.class,
            DbExecuteScriptCommand.class,
            DbTransactionCommand.class,
            DbBackupCommand.class,
            DbImportCsvCommand.class,
            DbExportCsvCommand.class,
            DbDropTableCommand.class,
            DbTableCreateCommand.class,
            DbTableInsertCommand.class,
            DbTableUpdateCommand.class,
            DbTableDeleteCommand.class,
            DbUpsertCommand.class,
            DbSetPoolSizeCommand.class,
            DbTimeoutCommand.class,
            DbCleanPoolCommand.class,
            DbAnalyzeCommand.class,
            DbQueryAsyncCommand.class,
            DbRegisterCommand.class,
            DbRunCommand.class,
            DbMigrateCommand.class,
            DbUpsertBatchCommand.class,
            DbConnectFileCommand.class,
            DbTableCopyCommand.class,
            DbClearCacheCommand.class,
            DbAddColumnCommand.class,
            DbDropColumnCommand.class,
            DbRenameTableCommand.class,
            DbCreateIndexCommand.class,
            DbDropIndexCommand.class,
            DbExecuteIfCommand.class,
            DbExportJsonCommand.class,
            DbListenCommand.class,
            DbTableSelectCommand.class,
            DbTableCountCommand.class,
            DbTableInsertBatchCommand.class,
            DbTruncateCommand.class,
            DbRenameColumnCommand.class,
            DbExportJsonlCommand.class,
            DbImportJsonCommand.class,
            DbKvSetCommand.class,
            DbKvDeleteCommand.class,
            DbCooldownCommand.class
        );
        for (Class<? extends AbstractCommand> command : commands) {
            DenizenCore.commandRegistry.registerCommand(command);
        }

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
        ScriptEvent.registerScriptEvent(new DbSlowQueryEvent());

        DatenizenTags.register();

        if (getCommand("datenizen") != null) {
            DatenizenAdminCommand adminCommand = new DatenizenAdminCommand();
            getCommand("datenizen").setExecutor(adminCommand);
            getCommand("datenizen").setTabCompleter(adminCommand);
        }

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