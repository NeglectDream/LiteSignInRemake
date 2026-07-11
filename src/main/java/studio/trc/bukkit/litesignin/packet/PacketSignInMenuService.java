package studio.trc.bukkit.litesignin.packet;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import studio.trc.bukkit.litesignin.event.custom.SignInGUICloseEvent;
import studio.trc.bukkit.litesignin.gui.SignInInventory;

/**
 * Manages packet-driven sign-in menu sessions.
 */
public final class PacketSignInMenuService
{
    private static final Map<UUID, PacketSignInSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> WINDOW_COUNTERS = new ConcurrentHashMap<>();

    private PacketSignInMenuService() {}

    public static void open(Player player, SignInInventory inventory) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PacketSignInSession current = SESSIONS.get(player.getUniqueId());
        if (current != null) {
            replace(player, current, inventory);
            return;
        }
        openNewSession(player, inventory);
    }

    public static void close(Player player) {
        close(player, true);
    }

    public static void close(Player player, boolean fireCloseEvent) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        PacketSignInSession session = SESSIONS.remove(uuid);
        if (session == null) {
            return;
        }
        boolean replacing = session.isReplacing();
        session.setReplacing(true);
        if (!replacing) {
            WINDOW_COUNTERS.remove(uuid);
        }
        if (player.isOnline()) {
            PacketSignInWindowCodec.closeWindow(player, session);
        }
        if (fireCloseEvent) {
            Bukkit.getPluginManager().callEvent(new SignInGUICloseEvent(player));
        }
    }

    public static boolean handleClientClose(Player player, int windowId) {
        if (player == null) {
            return false;
        }
        PacketSignInSession session = SESSIONS.get(player.getUniqueId());
        if (session == null || session.isReplacing() || session.getWindowId() != windowId) {
            return false;
        }
        SESSIONS.remove(player.getUniqueId());
        WINDOW_COUNTERS.remove(player.getUniqueId());
        Bukkit.getPluginManager().callEvent(new SignInGUICloseEvent(player));
        return true;
    }

    public static void resync(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PacketSignInSession session = SESSIONS.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        PacketSignInWindowCodec.sendFullWindow(player, session);
    }

    public static PacketSignInSession getSession(UUID uuid) {
        return SESSIONS.get(uuid);
    }

    public static boolean isOpening(UUID uuid) {
        return SESSIONS.containsKey(uuid);
    }

    public static SignInInventory getOpeningInventory(UUID uuid) {
        PacketSignInSession session = SESSIONS.get(uuid);
        return session != null ? session.getInventory() : null;
    }

    public static void shutdown() {
        for (PacketSignInSession session : SESSIONS.values()) {
            Player player = Bukkit.getPlayer(session.getPlayerId());
            if (player != null && player.isOnline()) {
                PacketSignInWindowCodec.closeWindow(player, session);
            }
        }
        SESSIONS.clear();
        WINDOW_COUNTERS.clear();
    }

    private static void replace(Player player, PacketSignInSession current, SignInInventory inventory) {
        current.setReplacing(true);
        close(player, true);
        openNewSession(player, inventory);
    }

    private static void openNewSession(Player player, SignInInventory inventory) {
        PacketSignInSession session = new PacketSignInSession(player.getUniqueId(), nextWindowId(player.getUniqueId()), inventory);
        SESSIONS.put(player.getUniqueId(), session);
        PacketSignInWindowCodec.openWindow(player, session);
    }

    private static int nextWindowId(UUID uuid) {
        Integer next = WINDOW_COUNTERS.compute(uuid, (key, current) -> current == null || current >= 100 ? 1 : current + 1);
        return next != null ? next : 1;
    }
}
