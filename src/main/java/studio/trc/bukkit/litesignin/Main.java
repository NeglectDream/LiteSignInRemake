package studio.trc.bukkit.litesignin;

import studio.trc.bukkit.litesignin.api.Statistics;
import studio.trc.bukkit.litesignin.command.SignInCommand;
import studio.trc.bukkit.litesignin.command.SignInSubCommandType;
import studio.trc.bukkit.litesignin.message.MessageUtil;
import studio.trc.bukkit.litesignin.database.storage.SQLiteStorage;
import studio.trc.bukkit.litesignin.database.engine.SQLiteEngine;
import studio.trc.bukkit.litesignin.event.Quit;
import studio.trc.bukkit.litesignin.event.Join;
import studio.trc.bukkit.litesignin.gui.SignInMenuListener;
import studio.trc.bukkit.litesignin.gui.SignInMenuService;
import studio.trc.bukkit.litesignin.packet.SignInMenuOverlay;
import studio.trc.bukkit.litesignin.service.PhysicalCardRecoveryService;
import studio.trc.bukkit.litesignin.util.PluginControl;
import studio.trc.bukkit.litesignin.configuration.ConfigurationType;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Do not resell the source code of this plug-in.
 * @author TRCStudioDean
 */
public class Main
    extends JavaPlugin
{
    /**
     * Main instance
     */
    private static Main main;

    @Override
    public void onEnable() {
        main = this;
        if (!PluginControl.initialize()) {
            getLogger().severe("LiteSignIn could not initialize its database and will be disabled.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        try {
            PhysicalCardRecoveryService.recoverOnlinePlayers();
            SignInMenuOverlay.initialize();
            registerCommandExecutor();
            registerEvent();
            MessageUtil.sendConsoleMessage("Console-Messages.Plugin-Enabled-Successfully",
                    ConfigurationType.MESSAGES, MessageUtil.getDefaultPlaceholders());
        } catch (RuntimeException error) {
            getLogger().log(java.util.logging.Level.SEVERE, "LiteSignIn startup failed", error);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        runShutdownStep("close sign-in menus", SignInMenuService::shutdown);
        runShutdownStep("unregister PacketEvents overlay", SignInMenuOverlay::shutdown);
        runShutdownStep("stop asynchronous tasks", () -> {
            PluginControl.shutdownAsyncTasks();
            MessageUtil.sendConsoleMessage("Console-Messages.Async-Thread-Stopped",
                    ConfigurationType.MESSAGES, MessageUtil.getDefaultPlaceholders());
        });
        runShutdownStep("save cached player data", SQLiteStorage::flushAll);
        SQLiteStorage.clearCache();
        Statistics.clearRetroactiveCooldowns();
        runShutdownStep("disconnect SQLite", () -> {
            SQLiteEngine engine = SQLiteEngine.getInstance();
            if (engine != null) {
                engine.disconnect();
                SQLiteEngine.setInstance(null);
            }
        });
    }

    private void runShutdownStep(String description, Runnable step) {
        try {
            step.run();
        } catch (Throwable error) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    "Failed to " + description + " during LiteSignIn shutdown", error);
        }
    }

    public static Main getInstance() {
        return main;
    }

    private void registerEvent() {
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new Join(), Main.getInstance());
        pm.registerEvents(new Quit(), Main.getInstance());
        pm.registerEvents(new SignInMenuListener(), Main.getInstance());
        MessageUtil.sendConsoleMessage("Console-Messages.Plugin-Listener-Registered", ConfigurationType.MESSAGES);
    }

    private void registerCommandExecutor() {
        PluginCommand command = getCommand("signin");
        if (command == null) {
            throw new IllegalStateException("Command 'signin' is not declared in plugin.yml");
        }
        SignInCommand commandExecutor = new SignInCommand();
        command.setExecutor(commandExecutor);
        command.setTabCompleter(commandExecutor);
        for (SignInSubCommandType subCommandType : SignInSubCommandType.values()) {
            SignInCommand.getSubCommands().put(subCommandType.getSubCommandName(), subCommandType.getSubCommand());
        }
        MessageUtil.sendConsoleMessage("Console-Messages.Plugin-Command-Registered", ConfigurationType.MESSAGES);
    }
}
