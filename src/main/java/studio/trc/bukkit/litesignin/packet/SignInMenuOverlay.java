package studio.trc.bukkit.litesignin.packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import studio.trc.bukkit.litesignin.Main;
import studio.trc.bukkit.litesignin.gui.SignInMenuService;
import studio.trc.bukkit.litesignin.gui.SignInMenuSession;

/**
 * Client-side visual overlay for the real, empty Bukkit sign-in container.
 *
 * <p>This component never creates window or transaction state. It only replaces
 * item payloads inside authoritative Bukkit/NMS packets while preserving their
 * windowId, stateId and carried item.
 */
public final class SignInMenuOverlay
{
    private static final long LOG_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);
    private static final int ITEM_CACHE_LIMIT = 512;
    private static final AtomicLong LAST_FAILURE_LOG = new AtomicLong();
    private static final Map<ItemStack, com.github.retrooper.packetevents.protocol.item.ItemStack> ITEM_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<ItemStack, com.github.retrooper.packetevents.protocol.item.ItemStack>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ItemStack, com.github.retrooper.packetevents.protocol.item.ItemStack> eldest) {
                    return size() > ITEM_CACHE_LIMIT;
                }
            });

    private static PacketListenerCommon listener;

    private SignInMenuOverlay() {}

    /** Registers the single PacketEvents listener for this plugin lifecycle. */
    public static synchronized void initialize() {
        if (listener != null) {
            return;
        }
        listener = PacketEvents.getAPI().getEventManager().registerListener(
                new PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
                    @Override
                    public void onPacketSend(PacketSendEvent event) {
                        handlePacketSend(event);
                    }
                });
    }

    /** Unregisters the listener so hot reload cannot retain the old classloader. */
    public static synchronized void shutdown() {
        if (listener == null) {
            return;
        }
        PacketEvents.getAPI().getEventManager().unregisterListener(listener);
        listener = null;
        ITEM_CACHE.clear();
    }

    private static void handlePacketSend(PacketSendEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        SignInMenuSession session = SignInMenuService.getSession(player.getUniqueId());
        if (session == null || !SignInMenuService.isCurrent(player, session)) {
            return;
        }
        try {
            if (event.getPacketType() == PacketType.Play.Server.OPEN_WINDOW) {
                bindOpenWindow(event, player, session);
            } else if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
                overlayWindowItems(event, session);
            } else if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
                overlaySetSlot(event, session);
            }
        } catch (RuntimeException error) {
            logPacketFailure(player, event, session, error);
        }
    }

    private static void bindOpenWindow(PacketSendEvent event, Player player, SignInMenuSession session) {
        if (session.getState() != SignInMenuSession.State.OPENING) {
            return;
        }
        // PacketEvents can fire this send hook off the main thread; only consult
        // Bukkit inventory state when we know we are on the main thread. The
        // authoritative one-shot expectation was armed by InventoryOpenEvent.
        if (Bukkit.isPrimaryThread()
                && player.getOpenInventory().getTopInventory() != session.getInventory()) {
            session.invalidateOpenExpectation();
            return;
        }
        int windowId = new WrapperPlayServerOpenWindow(event).getContainerId();
        session.bindWindowId(windowId);
    }

    private static void overlayWindowItems(PacketSendEvent event, SignInMenuSession session) {
        WrapperPlayServerWindowItems wrapper = new WrapperPlayServerWindowItems(event);
        if (!session.acceptsWindow(wrapper.getWindowId())) {
            return;
        }
        ArrayList<com.github.retrooper.packetevents.protocol.item.ItemStack> items =
                new ArrayList<>(wrapper.getItems());
        if (items.size() < SignInMenuSession.MENU_SIZE) {
            throw new IllegalStateException("Window item list is smaller than the 54-slot sign-in container");
        }
        for (int slot = 0; slot < SignInMenuSession.MENU_SIZE; slot++) {
            items.set(slot, toPacketItem(session.getOverlayItem(slot)));
        }
        wrapper.setItems(items);
        event.markForReEncode(true);
    }

    private static void overlaySetSlot(PacketSendEvent event, SignInMenuSession session) {
        WrapperPlayServerSetSlot wrapper = new WrapperPlayServerSetSlot(event);
        int slot = wrapper.getSlot();
        if (!session.acceptsWindow(wrapper.getWindowId())
                || slot < 0 || slot >= SignInMenuSession.MENU_SIZE) {
            return;
        }
        wrapper.setItem(toPacketItem(session.getOverlayItem(slot)));
        event.markForReEncode(true);
    }

    private static com.github.retrooper.packetevents.protocol.item.ItemStack toPacketItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY;
        }
        synchronized (ITEM_CACHE) {
            com.github.retrooper.packetevents.protocol.item.ItemStack cached = ITEM_CACHE.get(item);
            if (cached != null) {
                return cached;
            }
            com.github.retrooper.packetevents.protocol.item.ItemStack converted =
                    SpigotConversionUtil.fromBukkitItemStack(item);
            ITEM_CACHE.put(item.clone(), converted);
            return converted;
        }
    }

    private static void logPacketFailure(Player player, PacketSendEvent event,
                                         SignInMenuSession session, RuntimeException error) {
        long now = System.nanoTime();
        long previous = LAST_FAILURE_LOG.get();
        if (now - previous < LOG_INTERVAL_NANOS || !LAST_FAILURE_LOG.compareAndSet(previous, now)) {
            return;
        }
        Main.getInstance().getLogger().log(Level.WARNING,
                "Failed to rewrite sign-in packet " + event.getPacketType()
                        + " for " + player.getUniqueId()
                        + " (window=" + session.getWindowId()
                        + ", state=" + session.getState() + ")",
                error);
    }
}
