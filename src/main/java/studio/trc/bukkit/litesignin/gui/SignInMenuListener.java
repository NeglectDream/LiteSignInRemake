package studio.trc.bukkit.litesignin.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

import studio.trc.bukkit.litesignin.event.Menu;

/**
 * Bukkit authority for sign-in inventory opening, clicks, drags and closing.
 * PacketEvents is visual only; all player interaction is denied or routed here
 * on the main thread after current session identity has been verified.
 */
public final class SignInMenuListener
    implements Listener
{
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        SignInMenuSession session = SignInMenuService.getSession(player.getUniqueId());
        if (session == null) {
            return;
        }
        Inventory inventory = event.getInventory();
        if (!event.isCancelled()
                && session.getState() == SignInMenuSession.State.OPENING
                && SignInMenuService.isCurrent(player, session, inventory)) {
            session.armOpenExpectation();
        } else {
            session.invalidateOpenExpectation();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof SignInMenuHolder)) {
            return;
        }
        boolean previouslyCancelled = event.isCancelled();
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        SignInMenuHolder holder = (SignInMenuHolder) inventory.getHolder();
        SignInMenuSession session = holder.getSession();
        if (!SignInMenuService.isCurrent(player, session, inventory)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= SignInMenuSession.MENU_SIZE) {
            return;
        }
        if (previouslyCancelled) {
            if (session.hasOverlayItem(rawSlot)) {
                SignInMenuService.requestResync(player, session);
            }
            return;
        }

        boolean sessionChanged = Menu.handleWindowClick(player, rawSlot, event.getClick());
        if (!sessionChanged && session.hasOverlayItem(rawSlot)) {
            SignInMenuService.requestResync(player, session);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof SignInMenuHolder)) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < 0 || slot >= SignInMenuSession.MENU_SIZE) {
                continue;
            }
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player) {
                Player player = (Player) event.getWhoClicked();
                SignInMenuSession session = ((SignInMenuHolder) inventory.getHolder()).getSession();
                if (SignInMenuService.isCurrent(player, session, inventory)) {
                    SignInMenuService.requestResync(player, session);
                }
            }
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof SignInMenuHolder)
                || !(event.getPlayer() instanceof Player)) {
            return;
        }
        SignInMenuSession session = ((SignInMenuHolder) inventory.getHolder()).getSession();
        if (session.getState() == SignInMenuSession.State.REPLACING) {
            return;
        }
        SignInMenuService.handleInventoryClose((Player) event.getPlayer(), session);
    }
}
