package studio.trc.bukkit.litesignin.gui;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import studio.trc.bukkit.litesignin.event.custom.SignInGUICloseEvent;
import studio.trc.bukkit.litesignin.util.BukkitSchedulerManager;

/**
 * Owns the complete lifecycle of Bukkit-backed sign-in menu sessions.
 *
 * <p>This is the only UUID → session registry. PacketEvents reads the current
 * session through this service and never maintains a second map. New sessions
 * are prepared before publication, replacement is rolled back on failure, and
 * close operations remove packet authority before touching Bukkit inventory.
 */
public final class SignInMenuService
{
    private static final Map<UUID, SignInMenuSession> SESSIONS = new ConcurrentHashMap<>();

    private SignInMenuService() {}

    public static void open(Player player, SignInInventory snapshot) {
        if (player == null || snapshot == null) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            BukkitSchedulerManager.runBukkitTask(() -> open(player, snapshot), 0);
            return;
        }
        if (!player.isOnline()) {
            return;
        }

        SignInMenuSession replacement = SignInMenuSession.prepare(player, snapshot);
        if (!replacement.beginOpening()) {
            throw new IllegalStateException("Prepared sign-in session could not enter OPENING state");
        }

        UUID uuid = player.getUniqueId();
        SignInMenuSession previous = SESSIONS.get(uuid);
        SignInMenuSession.State previousState = previous != null ? previous.beginReplacing() : null;
        SESSIONS.put(uuid, replacement);

        try {
            InventoryView opened = player.openInventory(replacement.getInventory());
            if (opened == null || opened.getTopInventory() != replacement.getInventory()) {
                throw new IllegalStateException("Bukkit rejected the prepared sign-in inventory");
            }
        } catch (Throwable error) {
            rollbackOpen(player, replacement, previous, previousState, error);
            throw error;
        }
    }

    public static void close(Player player) {
        close(player, true);
    }

    public static void close(Player player, boolean fireCloseEvent) {
        if (player == null) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            BukkitSchedulerManager.runBukkitTask(() -> close(player, fireCloseEvent), 0);
            return;
        }

        SignInMenuSession session = SESSIONS.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.beginClosing();
        recoverUnexpectedItems(session);
        if (player.isOnline() && isTopInventory(player, session.getInventory())) {
            player.closeInventory();
        }
        session.finishClosing();
        if (fireCloseEvent) {
            Bukkit.getPluginManager().callEvent(new SignInGUICloseEvent(player));
        }
    }

    /** Handles a player/client initiated Bukkit InventoryCloseEvent. */
    static boolean handleInventoryClose(Player player, SignInMenuSession session) {
        if (player == null || session == null || session.getState() == SignInMenuSession.State.REPLACING) {
            return false;
        }
        if (!SESSIONS.remove(player.getUniqueId(), session)) {
            return false;
        }
        session.beginClosing();
        recoverUnexpectedItems(session);
        session.finishClosing();
        Bukkit.getPluginManager().callEvent(new SignInGUICloseEvent(player));
        return true;
    }

    /**
     * Coalesces client correction requests. Bukkit generates the authoritative
     * update packet and state id; PacketEvents only rewrites its visual items.
     */
    static void requestResync(Player player, SignInMenuSession session) {
        if (player == null || session == null || !session.markResyncScheduled()) {
            return;
        }
        BukkitSchedulerManager.runBukkitTask(() -> {
            session.clearResyncScheduled();
            if (!player.isOnline() || !isCurrent(player, session, session.getInventory())
                    || session.getState() != SignInMenuSession.State.OPEN) {
                return;
            }
            player.updateInventory();
        }, 0);
    }

    public static SignInMenuSession getSession(UUID uuid) {
        return uuid != null ? SESSIONS.get(uuid) : null;
    }

    public static SignInMenuHolder getHolder(UUID uuid) {
        SignInMenuSession session = getSession(uuid);
        return session != null ? session.getHolder() : null;
    }

    public static boolean isCurrent(Player player, SignInMenuSession session) {
        return player != null && session != null && SESSIONS.get(player.getUniqueId()) == session;
    }

    static boolean isCurrent(Player player, SignInMenuSession session, Inventory inventory) {
        return isCurrent(player, session)
                && session.getInventory() == inventory
                && session.getHolder() == inventory.getHolder();
    }

    public static boolean isOpening(UUID uuid) {
        return getSession(uuid) != null;
    }

    public static SignInInventory getOpeningInventory(UUID uuid) {
        SignInMenuSession session = getSession(uuid);
        return session != null ? session.getSnapshot() : null;
    }

    /** Clears online and offline sessions during plugin disable. */
    public static void shutdown() {
        for (SignInMenuSession session : new ArrayList<>(SESSIONS.values())) {
            Player player = session.getPlayer();
            if (!SESSIONS.remove(session.getPlayerId(), session)) {
                continue;
            }
            session.beginClosing();
            recoverUnexpectedItems(session);
            if (player != null && player.isOnline() && isTopInventory(player, session.getInventory())) {
                player.closeInventory();
            }
            session.finishClosing();
        }
        SESSIONS.clear();
    }

    private static void rollbackOpen(Player player, SignInMenuSession replacement,
                                     SignInMenuSession previous, SignInMenuSession.State previousState,
                                     Throwable originalError) {
        UUID uuid = player.getUniqueId();
        SESSIONS.remove(uuid, replacement);
        replacement.beginClosing();
        replacement.finishClosing();

        if (previous == null) {
            return;
        }
        previous.restoreAfterReplacement(previousState);
        SESSIONS.put(uuid, previous);

        if (!player.isOnline() || isTopInventory(player, previous.getInventory())) {
            return;
        }
        try {
            previous.prepareForReopen();
            InventoryView restored = player.openInventory(previous.getInventory());
            if (restored == null || restored.getTopInventory() != previous.getInventory()) {
                throw new IllegalStateException("Bukkit rejected the previous sign-in inventory");
            }
        } catch (Throwable restoreError) {
            originalError.addSuppressed(restoreError);
            SESSIONS.remove(uuid, previous);
            previous.beginClosing();
            previous.finishClosing();
        }
    }

    private static boolean isTopInventory(Player player, Inventory inventory) {
        return player != null && inventory != null
                && player.getOpenInventory().getTopInventory() == inventory;
    }

    private static void recoverUnexpectedItems(SignInMenuSession session) {
        Player player = session.getPlayer();
        Inventory inventory = session.getInventory();
        if (player == null || inventory == null) {
            return;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            inventory.setItem(slot, null);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
            overflow.values().forEach(leftover ->
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }
}
