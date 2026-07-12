package studio.trc.bukkit.litesignin.packet;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;

/**
 * PacketEvents overlay that fills an otherwise-empty Bukkit inventory with the
 * sign-in menu snapshot on the client side.
 *
 * <p>Architecture (mirrors DreamCore {@code PacketInventoryOverlay}):
 * <ol>
 *   <li>{@link SignInMenuService} creates a <b>real, empty</b> Bukkit
 *       {@link Inventory} and opens it via {@link Player#openInventory}. The
 *       server owns the container, windowId, stateId and click/drag
 *       transactions — other plugins see an empty inventory and can hook
 *       {@code InventoryClickEvent} / {@code InventoryDragEvent} normally.</li>
 *   <li>This class intercepts the server-bound {@code OPEN_WINDOW} packet to
 *       capture the windowId, then rewrites every {@code WINDOW_ITEMS} /
 *       {@code SET_SLOT} packet for that window so the client sees the sign-in
 *       snapshot instead of the empty server inventory.</li>
 *   <li>Clicks are handled by {@code SignInMenuListener} through Bukkit events;
 *       this class only owns the visual overlay and {@link #refresh} resync.</li>
 * </ol>
 *
 * <p>Session identity is by {@link Inventory} reference + holder, so a stale
 * packet from a superseded window can never corrupt a freshly opened one.
 */
public final class SignInMenuOverlay
{
    private static final ItemStack AIR = new ItemStack(Material.AIR);

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static volatile boolean listenerRegistered;

    private SignInMenuOverlay() {}

    /**
     * Registers the overlay for {@code player}'s soon-to-be-opened inventory.
     *
     * @param overlayItems display snapshot indexed by slot; {@code null}/AIR
     *                     slots are left transparent (the server's real —
     *                     empty — slot shows)
     */
    public static void open(Player player, Inventory inventory, ItemStack[] overlayItems) {
        if (player == null || inventory == null) {
            return;
        }
        ensureListener();
        SESSIONS.put(player.getUniqueId(), new Session(inventory, overlayItems));
    }

    /**
     * Removes the overlay session only if it still backs {@code inventory}.
     * Called from {@code InventoryCloseEvent}; a stale close for a previous
     * inventory cannot clear a freshly opened session.
     */
    public static boolean clear(Player player, Inventory inventory) {
        if (player == null) {
            return false;
        }
        Session session = SESSIONS.get(player.getUniqueId());
        if (session == null || session.inventory != inventory) {
            return false;
        }
        return SESSIONS.remove(player.getUniqueId(), session);
    }

    /** Returns the active overlay session for the player, if any. */
    public static Session getSession(UUID uuid) {
        return SESSIONS.get(uuid);
    }

    /**
     * Re-sends every overlay slot to the client via {@code SET_SLOT}.
     *
     * <p>Used after a cancelled click so the client never retains a stale
     * button state. Runs on the main thread (caller's responsibility).
     */
    public static void refresh(Player player) {
        if (player == null) {
            return;
        }
        Session session = SESSIONS.get(player.getUniqueId());
        if (session == null || !session.hasWindowId()) {
            return;
        }
        for (int slot = 0; slot < session.overlayItems.length; slot++) {
            session.resendSlot(player, slot);
        }
    }

    private static void ensureListener() {
        if (listenerRegistered) {
            return;
        }
        synchronized (SignInMenuOverlay.class) {
            if (listenerRegistered) {
                return;
            }
            PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract(PacketListenerPriority.NORMAL) {
                @Override
                public void onPacketSend(PacketSendEvent event) {
                    handlePacketSend(event);
                }
            });
            listenerRegistered = true;
        }
    }

    private static void handlePacketSend(PacketSendEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        Session session = SESSIONS.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        try {
            if (event.getPacketType() == PacketType.Play.Server.OPEN_WINDOW) {
                session.windowId = new WrapperPlayServerOpenWindow(event).getContainerId();
                return;
            }
            if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
                overlayWindowItems(event, session);
                return;
            }
            if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
                overlaySetSlot(event, session);
            }
        } catch (RuntimeException ignored) {
            // Packet shape mismatch: safest is to leave the packet untouched.
        }
    }

    private static void overlayWindowItems(PacketSendEvent event, Session session) {
        if (!session.hasWindowId()) {
            return;
        }
        WrapperPlayServerWindowItems wrapper = new WrapperPlayServerWindowItems(event);
        if (wrapper.getWindowId() != session.windowId) {
            return;
        }
        ArrayList<com.github.retrooper.packetevents.protocol.item.ItemStack> items =
                new ArrayList<>(wrapper.getItems());
        int limit = Math.min(session.overlayItems.length, items.size());
        for (int slot = 0; slot < limit; slot++) {
            ItemStack overlay = session.overlayItems[slot];
            if (overlay != null && overlay.getType() != Material.AIR) {
                items.set(slot, toPacketItem(overlay));
            }
        }
        wrapper.setItems(items);
        event.markForReEncode(true);
    }

    private static void overlaySetSlot(PacketSendEvent event, Session session) {
        if (!session.hasWindowId()) {
            return;
        }
        WrapperPlayServerSetSlot wrapper = new WrapperPlayServerSetSlot(event);
        int slot = wrapper.getSlot();
        if (wrapper.getWindowId() != session.windowId || slot < 0 || slot >= session.overlayItems.length) {
            return;
        }
        ItemStack overlay = session.overlayItems[slot];
        if (overlay == null || overlay.getType() == Material.AIR) {
            return;
        }
        wrapper.setItem(toPacketItem(overlay));
        event.markForReEncode(true);
    }

    private static com.github.retrooper.packetevents.protocol.item.ItemStack toPacketItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return SpigotConversionUtil.fromBukkitItemStack(AIR);
        }
        return SpigotConversionUtil.fromBukkitItemStack(item);
    }

    /**
     * Overlay state for one player's open sign-in menu.
     *
     * <p>{@code windowId} is captured from the first {@code OPEN_WINDOW} packet
     * sent after {@link #open}. Before that it is {@code -1} and overlay
     * rewriting is skipped.
     */
    public static final class Session
    {
        private final Inventory inventory;
        private final ItemStack[] overlayItems;
        private volatile int windowId = -1;
        private int stateId;

        private Session(Inventory inventory, ItemStack[] overlays) {
            this.inventory = inventory;
            this.overlayItems = new ItemStack[inventory.getSize()];
            if (overlays != null) {
                int limit = Math.min(overlays.length, overlayItems.length);
                for (int slot = 0; slot < limit; slot++) {
                    ItemStack item = overlays[slot];
                    if (item != null && item.getType() != Material.AIR) {
                        overlayItems[slot] = item.clone();
                    }
                }
            }
        }

        public Inventory getInventory() {
            return inventory;
        }

        boolean hasWindowId() {
            return windowId >= 0;
        }

        private void resendSlot(Player player, int slot) {
            if (player == null || !player.isOnline() || !hasWindowId()
                    || slot < 0 || slot >= overlayItems.length) {
                return;
            }
            ItemStack overlay = overlayItems[slot];
            if (overlay == null || overlay.getType() == Material.AIR) {
                return;
            }
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerSetSlot(
                    windowId, ++stateId, slot, toPacketItem(overlay)));
        }
    }
}
