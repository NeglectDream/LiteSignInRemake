package studio.trc.bukkit.litesignin;

import studio.trc.bukkit.litesignin.command.SignInCommand;
import studio.trc.bukkit.litesignin.command.SignInSubCommandType;
import studio.trc.bukkit.litesignin.thread.LiteSignInThread;
import studio.trc.bukkit.litesignin.message.MessageUtil;
import studio.trc.bukkit.litesignin.database.storage.SQLiteStorage;
import studio.trc.bukkit.litesignin.database.engine.SQLiteEngine;
import studio.trc.bukkit.litesignin.event.Quit;
import studio.trc.bukkit.litesignin.event.Join;
import studio.trc.bukkit.litesignin.gui.SignInMenuListener;
import studio.trc.bukkit.litesignin.gui.SignInMenuService;
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

        // 配置优先加载：确保后续控制台消息能从 Messages.yml 正确读取
        PluginControl.reload();

        registerCommandExecutor();
        registerEvent();
        MessageUtil.sendConsoleMessage("Console-Messages.Plugin-Enabled-Successfully", ConfigurationType.MESSAGES, MessageUtil.getDefaultPlaceholders());
    }

    @Override
    public void onDisable() {
        SignInMenuService.shutdown();
        LiteSignInThread.getTaskThread().setRunning(false);
        MessageUtil.sendConsoleMessage("Console-Messages.Async-Thread-Stopped", ConfigurationType.MESSAGES, MessageUtil.getDefaultPlaceholders());
        SQLiteStorage.cache.values().stream().forEach(SQLiteStorage::saveData);
        if (SQLiteEngine.getInstance() != null) {
            SQLiteEngine.getInstance().disconnect();
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
        SignInCommand commandExecutor = new SignInCommand();
        command.setExecutor(commandExecutor);
        command.setTabCompleter(commandExecutor);
        for (SignInSubCommandType subCommandType : SignInSubCommandType.values()) {
            SignInCommand.getSubCommands().put(subCommandType.getSubCommandName(), subCommandType.getSubCommand());
        }
        MessageUtil.sendConsoleMessage("Console-Messages.Plugin-Command-Registered", ConfigurationType.MESSAGES);
    }
}
