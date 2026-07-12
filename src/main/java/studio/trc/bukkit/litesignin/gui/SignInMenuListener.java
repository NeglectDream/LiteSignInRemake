package studio.trc.bukkit.litesignin.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import studio.trc.bukkit.litesignin.event.Menu;
import studio.trc.bukkit.litesignin.event.custom.SignInGUICloseEvent;
import studio.trc.bukkit.litesignin.packet.SignInMenuOverlay;

/**
 * Bukkit listener backing the sign-in menu.
 * <p>
 * Replaces the former {@code PacketSignInPacketListener}. Click and drag
 * permission plus the close lifecycle run through the standard Bukkit event
 * pipeline on the main thread — this serialises interaction (no main-thread
 * packet amplification) and uses the holder reference itself as the session
 * token (no window-id ABA), while other plugins can still hook these events.
 */
public final class SignInMenuListener
    implements Listener
{
    private static final int TOP_SLOTS = 54;

    /**
     * Cancels every click inside a sign-in menu and routes top-container
     * clicks to {@link Menu#handleWindowClick}.
     *
     * <p>Player-inventory area clicks (raw slot {@code >= 54}) are cancelled
     * to prevent item relocation but are not forwarded: the sign-in menu only
     * exposes buttons in the top 54 slots.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof SignInMenuHolder)) {
            return;
        }
        // The sign-in menu never accepts item manipulation.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        // Only top-container clicks map to buttons; the player's own inventory
        // area is just shielded from interaction.
        if (event.getRawSlot() >= TOP_SLOTS || event.getRawSlot() < 0) {
            return;
        }

        boolean sessionChanged = Menu.handleWindowClick(
                player, event.getSlot(), event.getClick());
        if (!sessionChanged) {
            SignInMenuService.resync(player);
        }
    }

    /**
     * Cancels any drag touching the sign-in top container.
     *
     * <p>Drags into the top 54 slots would otherwise move real items into the
     * (server-side empty) menu inventory; since the overlay rewrites outbound
     * packets, such items would be invisible to the client yet present on the
     * server — an item-loss vector. Cancelling everything is the safe default
     * and matches the {@code onInventoryClick} "no manipulation" contract.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof SignInMenuHolder)) {
            return;
        }
        // Any drag slot inside the top container (raw slot < 54) is forbidden.
        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot < TOP_SLOTS) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Clears the session when the player closes the sign-in menu, unless the
     * holder is flagged {@code replacing} (internal page switch).
     *
     * <p>{@link SignInMenuService#removeIfCurrent} guards against a stale close
     * event from a superseded holder tearing down a freshly opened session.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof SignInMenuHolder)) {
            return;
        }
        SignInMenuHolder holder = (SignInMenuHolder) inv.getHolder();
        if (holder.isReplacing()) {
            return;
        }
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        if (SignInMenuService.removeIfCurrent(player, holder)) {
            // Drop the packet overlay last so a late outbound packet for the
            // closing window is not left rewriting a fresh session.
            SignInMenuOverlay.clear(player, inv);
            org.bukkit.Bukkit.getPluginManager().callEvent(new SignInGUICloseEvent(player));
        }
    }
}
