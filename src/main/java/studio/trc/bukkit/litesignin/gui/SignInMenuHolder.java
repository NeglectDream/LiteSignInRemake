package studio.trc.bukkit.litesignin.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Bukkit {@link InventoryHolder} backing the sign-in menu.
 * <p>
 * Replaces the former packet-only session. The holder is the single source of
 * truth for an open sign-in menu: it carries the immutable {@link SignInInventory}
 * snapshot and the transient {@code replacing} flag used to distinguish a real
 * close (player/client initiated) from an internal page switch.
 * <p>
 * Identity is by reference: the service maps a player UUID to a holder instance,
 * so there is no window-id reuse / ABA class of bugs.
 */
public final class SignInMenuHolder
    implements InventoryHolder
{
    private final Player player;
    private final SignInInventory snapshot;
    private volatile boolean replacing;

    public SignInMenuHolder(Player player, SignInInventory snapshot) {
        this.player = player;
        this.snapshot = snapshot;
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * The immutable menu snapshot rendered into the real inventory.
     */
    public SignInInventory getSnapshot() {
        return snapshot;
    }

    /**
     * While {@code true}, the next {@link org.bukkit.event.inventory.InventoryCloseEvent}
     * is part of an internal page switch and must NOT fire {@code SignInGUICloseEvent}
     * nor clear the session.
     */
    public boolean isReplacing() {
        return replacing;
    }

    public void setReplacing(boolean replacing) {
        this.replacing = replacing;
    }

    /**
     * The live Bukkit inventory; assigned by the service when the inventory is
     * created. Kept here so listeners can resync contents without a second map.
     */
    private Inventory inventory;

    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
