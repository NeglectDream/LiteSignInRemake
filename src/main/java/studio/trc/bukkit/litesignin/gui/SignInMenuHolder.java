package studio.trc.bukkit.litesignin.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Bukkit holder that identifies one prepared {@link SignInMenuSession}.
 *
 * <p>The holder contains no duplicated lifecycle state; every property is read
 * from its session, preventing holder/service/packet state from drifting apart.
 */
public final class SignInMenuHolder
    implements InventoryHolder
{
    private final SignInMenuSession session;

    SignInMenuHolder(SignInMenuSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session is required");
        }
        this.session = session;
    }

    SignInMenuSession getSession() {
        return session;
    }

    public Player getPlayer() {
        return session.getPlayer();
    }

    public SignInInventory getSnapshot() {
        return session.getSnapshot();
    }

    @Override
    public Inventory getInventory() {
        return session.getInventory();
    }
}
