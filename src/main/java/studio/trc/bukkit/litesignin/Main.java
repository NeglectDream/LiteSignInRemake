package studio.trc.bukkit.litesignin;

import studio.trc.bukkit.litesignin.command.SignInCommand;
import studio.trc.bukkit.litesignin.command.SignInSubCommandType;
import studio.trc.bukkit.litesignin.thread.LiteSignInThread;
import studio.trc.bukkit.litesignin.message.MessageUtil;
import studio.trc.bukkit.litesignin.database.storage.SQLiteStorage;
import studio.trc.bukkit.litesignin.database.engine.SQLiteEngine;
import studio.trc.bukkit.litesignin.event.Quit;
import studio.trc.bukkit.litesignin.event.Join;
import studio.trc.bukkit.litesignin.packet.PacketSignInMenuService;
import studio.trc.bukkit.litesignin.packet.PacketSignInPacketListener;
import studio.trc.bukkit.litesignin.util.PluginControl;
import studio.trc.bukkit.litesignin.util.LiteSignInProperties;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.retrooper.packetevents.PacketEvents;

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
    private PacketSignInPacketListener packetSignInPacketListener;
    
    @Override
    public void onEnable() {
        main = this;

        LiteSignInProperties.reloadProperties();

        registerCommandExecutor();
        registerEvent();
        registerPacketListener();
        PluginControl.reload();
        LiteSignInProperties.sendOperationMessage("PluginEnabledSuccessfully", MessageUtil.getDefaultPlaceholders());

    }

    @Override
    public void onDisable() {
        if (packetSignInPacketListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetSignInPacketListener);
            packetSignInPacketListener = null;
        }
        PacketSignInMenuService.shutdown();
        LiteSignInThread.getTaskThread().setRunning(false);
        LiteSignInProperties.sendOperationMessage("AsyncThreadStopped", MessageUtil.getDefaultPlaceholders());
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
        LiteSignInProperties.sendOperationMessage("PluginListenerRegistered");
    }
    
    private void registerCommandExecutor() {
        PluginCommand command = getCommand("signin");
        SignInCommand commandExecutor = new SignInCommand();
        command.setExecutor(commandExecutor);
        command.setTabCompleter(commandExecutor);
        for (SignInSubCommandType subCommandType : SignInSubCommandType.values()) {
            SignInCommand.getSubCommands().put(subCommandType.getSubCommandName(), subCommandType.getSubCommand());
        }
        LiteSignInProperties.sendOperationMessage("PluginCommandRegistered");
    }

    private void registerPacketListener() {
        packetSignInPacketListener = new PacketSignInPacketListener();
        PacketEvents.getAPI().getEventManager().registerListener(packetSignInPacketListener);
    }
}
