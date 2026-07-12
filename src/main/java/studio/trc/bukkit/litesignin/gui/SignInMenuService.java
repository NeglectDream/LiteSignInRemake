package studio.trc.bukkit.litesignin.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import studio.trc.bukkit.litesignin.event.custom.SignInGUICloseEvent;
import studio.trc.bukkit.litesignin.packet.SignInMenuOverlay;

/**
 * Manages the Bukkit-backed sign-in menu session lifecycle.
 *
 * <p>Architecture (mirrors DreamCore {@code PacketInventoryOverlay}): the
 * server owns a <b>real, empty</b> {@link Inventory} via a
 * {@link SignInMenuHolder} so windowId, stateId and click/drag transactions
 * are handled by Bukkit — other plugins see an empty container and can hook
 * {@code InventoryClickEvent}/{@code InventoryDragEvent} normally. The sign-in
 * snapshot is delivered to the client by {@link SignInMenuOverlay} which
 * rewrites {@code WINDOW_ITEMS}/{@code SET_SLOT} packets; the server inventory
 * never holds any item, which removes the window-id/state-id bookkeeping that
 * caused ABA and session-replay bugs in the former packet-only implementation.
 *
 * <p>Session identity is by holder reference: {@link ConcurrentHashMap#remove(Object, Object)}
 * is used so a stale close event from a previous holder can never clear the
 * current session.
 */
public final class SignInMenuService
{
    private static final int MENU_SIZE = 54;

    private static final Map<UUID, SignInMenuHolder> SESSIONS = new ConcurrentHashMap<>();

    private SignInMenuService() {}

    /**
     * Opens (or replaces) the sign-in menu for {@code player}.
     *
     * <p>If a session already exists, the old holder is flagged
     * {@code replacing} so its pending {@code InventoryCloseEvent} — fired
     * asynchronously by {@link Player#openInventory(Inventory)} closing the
     * previous inventory — will not raise {@link SignInGUICloseEvent}.
     */
    public static void open(Player player, SignInInventory inventory) {
        if (player == null || !player.isOnline()) {
            return;
        }
        SignInMenuHolder old = SESSIONS.get(player.getUniqueId());
        if (old != null) {
            old.setReplacing(true);
        }
        openNewSession(player, inventory);
    }

    /**
     * Closes the sign-in menu and optionally fires {@link SignInGUICloseEvent}.
     *
     * <p>The holder is removed first, so the {@code InventoryCloseEvent}
     * triggered by {@link Player#closeInventory()} becomes a no-op in the
     * listener (no duplicate close event).
     */
    public static void close(Player player, boolean fireCloseEvent) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        SignInMenuHolder holder = SESSIONS.remove(uuid);
        if (holder == null) {
            return;
        }
        holder.setReplacing(false);
        Inventory inv = holder.getInventory();
        if (player.isOnline()) {
            player.closeInventory();
        }
        if (inv != null) {
            SignInMenuOverlay.clear(player, inv);
        }
        if (fireCloseEvent) {
            Bukkit.getPluginManager().callEvent(new SignInGUICloseEvent(player));
        }
    }

    public static void close(Player player) {
        close(player, true);
    }

    /**
     * Re-renders the current snapshot to the client.
     *
     * <p>Used after a cancelled click so the client never retains a stale item
     * state. Delegates to {@link SignInMenuOverlay#refresh} which re-sends every
     * overlay slot via {@code SET_SLOT} — the server inventory is left empty.
     */
    public static void resync(Player player) {
        SignInMenuOverlay.refresh(player);
    }

    /**
     * Removes the session only if {@code holder} is still the active one.
     * Used by {@link SignInMenuListener} so a close event from a superseded
     * holder cannot tear down a freshly opened session.
     *
     * @return {@code true} if the holder was the current one and was removed
     */
    public static boolean removeIfCurrent(Player player, SignInMenuHolder holder) {
        return SESSIONS.remove(player.getUniqueId(), holder);
    }

    public static SignInMenuHolder getHolder(UUID uuid) {
        return SESSIONS.get(uuid);
    }

    public static boolean isOpening(UUID uuid) {
        return SESSIONS.containsKey(uuid);
    }

    public static SignInInventory getOpeningInventory(UUID uuid) {
        SignInMenuHolder holder = SESSIONS.get(uuid);
        return holder != null ? holder.getSnapshot() : null;
    }

    /**
     * Closes every open sign-in menu. Called on plugin disable.
     */
    public static void shutdown() {
        for (SignInMenuHolder holder : SESSIONS.values()) {
            Player player = holder.getPlayer();
            Inventory inv = holder.getInventory();
            if (player != null && player.isOnline()) {
                holder.setReplacing(false);
                player.closeInventory();
                if (inv != null) {
                    SignInMenuOverlay.clear(player, inv);
                }
            }
        }
        SESSIONS.clear();
    }

    private static void openNewSession(Player player, SignInInventory inventory) {
        SignInMenuHolder holder = new SignInMenuHolder(player, inventory);
        Inventory inv = null;
        try {
            // createInventory(size=54) with title; the String overload is used so
            // the plugin compiles against spigot-api (the Paper-only Component
            // overload is unavailable). The real container stays empty — the
            // sign-in snapshot is delivered to the client by the overlay.
            inv = Bukkit.createInventory(holder, MENU_SIZE, inventory.getTitle());
            holder.setInventory(inv);
            ItemStack[] overlayItems = inventory.getContents();
            SESSIONS.put(player.getUniqueId(), holder);
            // Register the overlay BEFORE openInventory so the first OPEN_WINDOW
            // and WINDOW_ITEMS packets — fired synchronously by openInventory —
            // are already rewritten on the client.
            SignInMenuOverlay.open(player, inv, overlayItems);
            player.openInventory(inv);
        } catch (Throwable error) {
            // Roll back: never leave a half-registered session that would
            // cause future clicks/closes to be misrouted.
            SESSIONS.remove(player.getUniqueId(), holder);
            if (inv != null) {
                SignInMenuOverlay.clear(player, inv);
            }
            throw error;
        }
    }
}
