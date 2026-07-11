package studio.trc.bukkit.litesignin.packet;

import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow;

import studio.trc.bukkit.litesignin.event.Menu;
import studio.trc.bukkit.litesignin.util.BukkitSchedulerManager;

/**
 * Intercepts all packet-driven sign-in menu interactions.
 */
public class PacketSignInPacketListener extends PacketListenerAbstract
{
    public PacketSignInPacketListener() {
        super(PacketListenerPriority.HIGHEST);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        PacketSignInSession session = PacketSignInMenuService.getSession(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            WrapperPlayClientClickWindow wrapper = new WrapperPlayClientClickWindow(event);
            int windowId = wrapper.getWindowId();
            if (windowId != session.getWindowId()) {
                return;
            }
            int slot = wrapper.getSlot();
            int button = wrapper.getButton();
            WrapperPlayClientClickWindow.WindowClickType clickType = wrapper.getWindowClickType();
            event.setCancelled(true);
            BukkitSchedulerManager.runBukkitTask(() -> {
                PacketSignInSession current = PacketSignInMenuService.getSession(player.getUniqueId());
                if (current == null || current.getWindowId() != windowId) {
                    return;
                }
                boolean sessionChanged = Menu.handleWindowClick(player, slot, button, clickType);
                if (!sessionChanged) {
                    PacketSignInMenuService.resync(player);
                }
            }, 0);
            return;
        }
        if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            WrapperPlayClientCloseWindow wrapper = new WrapperPlayClientCloseWindow(event);
            int windowId = wrapper.getWindowId();
            if (windowId != session.getWindowId()) {
                return;
            }
            event.setCancelled(true);
            BukkitSchedulerManager.runBukkitTask(() -> PacketSignInMenuService.handleClientClose(player, windowId), 0);
        }
    }
}
