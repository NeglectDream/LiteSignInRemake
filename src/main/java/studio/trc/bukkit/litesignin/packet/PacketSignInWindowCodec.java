package studio.trc.bukkit.litesignin.packet;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import studio.trc.bukkit.litesignin.gui.SignInInventory;
import studio.trc.bukkit.litesignin.util.AdventureUtils;

/**
 * Encodes the packet-only sign-in menu for Minecraft 1.21.x.
 */
public final class PacketSignInWindowCodec
{
    private static final int WINDOW_ROWS = 6;
    private static final int TOP_SLOTS = WINDOW_ROWS * 9;
    private static final int PLAYER_INVENTORY_SLOTS = 36;
    private static final int TOTAL_VISIBLE_SLOTS = TOP_SLOTS + PLAYER_INVENTORY_SLOTS;
    private static final int GENERIC_9X6_TYPE = 5;

    private PacketSignInWindowCodec() {}

    public static void openWindow(Player player, PacketSignInSession session) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, createOpenWindow(session));
        sendFullWindow(player, session);
    }

    public static void sendFullWindow(Player player, PacketSignInSession session) {
        WrapperPlayServerWindowItems wrapper = new WrapperPlayServerWindowItems(
                session.getWindowId(),
                session.nextStateId(),
                buildVisibleWindowItems(player, session.getInventory()),
                toPacketItem(getCursorItem(player)));
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, wrapper);
    }

    public static void closeWindow(Player player, PacketSignInSession session) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerCloseWindow(session.getWindowId()));
    }

    private static WrapperPlayServerOpenWindow createOpenWindow(PacketSignInSession session) {
        Component title = AdventureUtils.serializeText(session.getInventory().getTitle());
        return new WrapperPlayServerOpenWindow(session.getWindowId(), GENERIC_9X6_TYPE, title);
    }

    private static List<ItemStack> buildVisibleWindowItems(Player player, SignInInventory inventory) {
        List<ItemStack> items = new ArrayList<>(TOTAL_VISIBLE_SLOTS);
        org.bukkit.inventory.ItemStack[] topContents = inventory.getContents();
        for (int slot = 0; slot < TOP_SLOTS; slot++) {
            items.add(toPacketItem(slot < topContents.length ? topContents[slot] : null));
        }
        for (int slot = 9; slot <= 35; slot++) {
            items.add(toPacketItem(player.getInventory().getItem(slot)));
        }
        for (int slot = 0; slot <= 8; slot++) {
            items.add(toPacketItem(player.getInventory().getItem(slot)));
        }
        return items;
    }

    private static org.bukkit.inventory.ItemStack getCursorItem(Player player) {
        return player.getItemOnCursor();
    }

    private static ItemStack toPacketItem(org.bukkit.inventory.ItemStack itemStack) {
        if (itemStack == null) {
            return ItemStack.EMPTY;
        }
        return SpigotConversionUtil.fromBukkitItemStack(itemStack);
    }
}
