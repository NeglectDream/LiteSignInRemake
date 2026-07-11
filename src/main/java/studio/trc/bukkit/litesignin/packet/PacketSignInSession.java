package studio.trc.bukkit.litesignin.packet;

import java.util.UUID;

import studio.trc.bukkit.litesignin.gui.SignInInventory;

/**
 * Runtime packet-driven sign-in menu session.
 */
public class PacketSignInSession
{
    private final UUID playerId;
    private final int windowId;
    private final SignInInventory inventory;
    private int stateId = 0;
    private boolean replacing;

    public PacketSignInSession(UUID playerId, int windowId, SignInInventory inventory) {
        this.playerId = playerId;
        this.windowId = windowId;
        this.inventory = inventory;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getWindowId() {
        return windowId;
    }

    public SignInInventory getInventory() {
        return inventory;
    }

    public int nextStateId() {
        stateId++;
        return stateId;
    }

    public int getStateId() {
        return stateId;
    }

    public boolean isReplacing() {
        return replacing;
    }

    public void setReplacing(boolean replacing) {
        this.replacing = replacing;
    }
}
