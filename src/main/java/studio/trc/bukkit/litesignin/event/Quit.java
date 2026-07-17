package studio.trc.bukkit.litesignin.event;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import studio.trc.bukkit.litesignin.api.Statistics;
import studio.trc.bukkit.litesignin.configuration.ConfigurationType;
import studio.trc.bukkit.litesignin.configuration.ConfigurationUtil;
import studio.trc.bukkit.litesignin.database.storage.SQLiteStorage;
import studio.trc.bukkit.litesignin.gui.SignInMenuService;
import studio.trc.bukkit.litesignin.thread.LiteSignInThread;
import studio.trc.bukkit.litesignin.util.BukkitSchedulerManager;
import studio.trc.bukkit.litesignin.util.OnlineTimeRecord;

/** Captures Bukkit state on the main thread before asynchronous persistence. */
public class Quit implements Listener {
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (ConfigurationUtil.getConfig(ConfigurationType.CONFIG)
                .getBoolean("Online-Duration-Condition.Enabled")) {
            OnlineTimeRecord.savePlayerOnlineTime(player);
        }
        OnlineTimeRecord.getJoinTimeRecord().remove(uuid);
        Join.invalidateSession(uuid);
        Statistics.clearRetroactiveCooldown(uuid);
        SignInMenuService.close(player);

        SQLiteStorage storage = SQLiteStorage.getCached(uuid);
        if (storage == null) {
            return;
        }
        storage.updateName(player.getName(), false);
        // Save off-thread, then perform the cache eviction on Bukkit's thread.
        // The main-thread check is serialized with a possible rejoin event.
        LiteSignInThread.tryRunTask(() -> {
            storage.saveData();
            BukkitSchedulerManager.runBukkitTask(() -> SQLiteStorage.evictIfOffline(uuid, storage), 0);
        });
    }
}
