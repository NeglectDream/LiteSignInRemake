package studio.trc.bukkit.litesignin.event;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import studio.trc.bukkit.litesignin.Main;
import studio.trc.bukkit.litesignin.api.SignInResult;
import studio.trc.bukkit.litesignin.api.Storage;
import studio.trc.bukkit.litesignin.configuration.ConfigurationType;
import studio.trc.bukkit.litesignin.configuration.ConfigurationUtil;
import studio.trc.bukkit.litesignin.message.JSONComponent;
import studio.trc.bukkit.litesignin.message.MessageUtil;
import studio.trc.bukkit.litesignin.service.PhysicalCardRecoveryService;
import studio.trc.bukkit.litesignin.thread.LiteSignInThread;
import studio.trc.bukkit.litesignin.util.BukkitSchedulerManager;
import studio.trc.bukkit.litesignin.util.LiteSignInUtils;
import studio.trc.bukkit.litesignin.util.OnlineTimeRecord;
import studio.trc.bukkit.litesignin.util.PluginControl;
import studio.trc.bukkit.litesignin.util.SignInDate;
import studio.trc.bukkit.litesignin.util.SkullManager;

/** Player join pipeline with an IO-only asynchronous phase. */
public class Join implements Listener {
    private static final AtomicLong GENERATION_SEQUENCE = new AtomicLong();
    private static final Map<UUID, Long> LOGIN_GENERATIONS = new ConcurrentHashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();
        long generation = GENERATION_SEQUENCE.incrementAndGet();
        LOGIN_GENERATIONS.put(uuid, generation);
        OnlineTimeRecord.getJoinTimeRecord().put(uuid, System.currentTimeMillis());

        double delay = ConfigurationUtil.getConfig(ConfigurationType.CONFIG)
                .getDouble("Join-Event.Delay");
        long delayTicks = Math.max(0L, Math.round(delay * 20D));
        LiteSignInThread.supplyTask(() -> Storage.getPlayer(uuid, playerName))
                .whenComplete((storage, error) -> BukkitSchedulerManager.runBukkitTask(
                        () -> completeJoin(uuid, playerName, generation, storage, error), delayTicks));
    }

    private void completeJoin(UUID uuid, String playerName, long generation,
                              Storage data, Throwable error) {
        if (!isCurrent(uuid, generation)) {
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        if (error != null || data == null) {
            Main.getInstance().getLogger().log(Level.SEVERE,
                    "Unable to load sign-in data for " + uuid, error);
            return;
        }
        if (LiteSignInUtils.checkInDisabledWorlds(uuid)) {
            return;
        }

        if (data instanceof studio.trc.bukkit.litesignin.database.storage.SQLiteStorage) {
            studio.trc.bukkit.litesignin.database.storage.SQLiteStorage sqliteStorage =
                    (studio.trc.bukkit.litesignin.database.storage.SQLiteStorage) data;
            sqliteStorage.updateName(player.getName(), false);
            PhysicalCardRecoveryService.recover(sqliteStorage);
        }

        boolean unableToHoldCards = !LiteSignInUtils.hasPermission(player, "Retroactive-Card.Hold");
        boolean autoSignIn = PluginControl.enableJoinEvent()
                && !data.alreadySignIn()
                && PluginControl.autoSignIn()
                && LiteSignInUtils.hasPermission(player, "Join-Auto-SignIn");

        if (PluginControl.enableJoinEvent() && !data.alreadySignIn() && !autoSignIn
                && OnlineTimeRecord.getSignInRequirement(player) == -1) {
            LiteSignInThread.runTask(() -> SkullManager.refreshTexture(uuid, playerName));
            sendJoinMessages(player);
        }

        if (unableToHoldCards && data.getRetroactiveCard() > 0) {
            data.takeRetroactiveCard(data.getRetroactiveCard());
            MessageUtil.sendMessage(player, ConfigurationUtil.getConfig(ConfigurationType.MESSAGES),
                    "GUI-SignIn-Messages.Unable-To-Hold");
        }
        if (autoSignIn && OnlineTimeRecord.getSignInRequirement(player) == -1) {
            SignInResult result = data.trySignIn();
            if (!result.isSuccess() && result != SignInResult.ALREADY_SIGNED_IN
                    && Main.getInstance() != null) {
                Main.getInstance().getLogger().warning("Automatic sign-in failed for "
                        + uuid + ": " + result);
            }
        }
    }

    private void sendJoinMessages(Player player) {
        SignInDate date = SignInDate.getInstance(new Date());
        MessageUtil.getMessageList("Join-Event.Messages").forEach(text -> {
            Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
            placeholders.put("{date}", date.getName(ConfigurationUtil
                    .getConfig(ConfigurationType.GUI_SETTINGS)
                    .getString("SignIn-GUI-Settings.Date-Format")));
            if (text.toLowerCase().contains("%opengui%")) {
                JSONComponent jsonComponent = new JSONComponent(
                        MessageUtil.replacePlaceholders(player,
                                MessageUtil.getMessage("Join-Event.Open-GUI"), placeholders),
                        MessageUtil.getMessageList("Join-Event.Hover-Text").stream()
                                .map(line -> MessageUtil.replacePlaceholders(player, line, placeholders))
                                .collect(Collectors.toList()),
                        "RUN_COMMAND",
                        "/litesignin:signin gui");
                MessageUtil.sendMessageWithJSONComponent(player, text, placeholders,
                        "%openGUI%", jsonComponent);
            } else {
                MessageUtil.sendMessage(player, text, placeholders);
            }
        });
    }

    static void invalidateSession(UUID uuid) {
        if (uuid != null) {
            LOGIN_GENERATIONS.remove(uuid);
        }
    }

    private static boolean isCurrent(UUID uuid, long generation) {
        return LOGIN_GENERATIONS.getOrDefault(uuid, -1L) == generation;
    }
}
