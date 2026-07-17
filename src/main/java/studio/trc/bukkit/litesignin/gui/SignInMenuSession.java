package studio.trc.bukkit.litesignin.gui;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Single source of truth for one Bukkit-backed sign-in menu.
 *
 * <p>A session is fully prepared before {@link SignInMenuService} publishes it,
 * so packet listeners and Bukkit events can never observe a half-initialized
 * holder or inventory. Lifecycle mutations occur on the Bukkit main thread;
 * volatile state/window fields and synchronized one-shot window binding provide
 * safe visibility to PacketEvents send threads.
 */
public final class SignInMenuSession
{
    public static final int MENU_SIZE = 54;

    public enum State {
        PREPARED,
        OPENING,
        OPEN,
        REPLACING,
        CLOSING,
        CLOSED
    }

    private final Player player;
    private final UUID playerId;
    private final SignInInventory snapshot;
    private final ItemStack[] overlayItems;
    private final AtomicBoolean resyncScheduled = new AtomicBoolean();

    private SignInMenuHolder holder;
    private Inventory inventory;
    private volatile State state = State.PREPARED;
    private volatile int windowId = -1;
    private boolean openExpectation;

    SignInMenuSession(Player player, SignInInventory snapshot) {
        this.player = player;
        this.playerId = player != null ? player.getUniqueId() : null;
        this.snapshot = snapshot;
        this.overlayItems = copyOverlay(snapshot != null ? snapshot.getContents() : null);
    }

    /**
     * Creates the holder and empty Bukkit inventory locally and returns a fully
     * initialized session. The caller publishes the returned object only after
     * this method succeeds.
     */
    static SignInMenuSession prepare(Player player, SignInInventory snapshot) {
        if (player == null || snapshot == null) {
            throw new IllegalArgumentException("player and snapshot are required");
        }
        SignInMenuSession session = new SignInMenuSession(player, snapshot);
        SignInMenuHolder holder = new SignInMenuHolder(session);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, snapshot.getTitle());
        session.holder = holder;
        session.inventory = inventory;
        return session;
    }

    public Player getPlayer() {
        return player;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public SignInInventory getSnapshot() {
        return snapshot;
    }

    public SignInMenuHolder getHolder() {
        return holder;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public State getState() {
        return state;
    }

    public int getWindowId() {
        return windowId;
    }

    /** Returns a defensive clone of one visual top-container slot. */
    public ItemStack getOverlayItem(int slot) {
        if (slot < 0 || slot >= overlayItems.length) {
            return null;
        }
        ItemStack item = overlayItems[slot];
        return item != null ? item.clone() : null;
    }

    public boolean hasOverlayItem(int slot) {
        return slot >= 0 && slot < overlayItems.length && overlayItems[slot] != null;
    }

    synchronized boolean beginOpening() {
        if (state != State.PREPARED) {
            return false;
        }
        state = State.OPENING;
        windowId = -1;
        openExpectation = false;
        return true;
    }

    synchronized State beginReplacing() {
        if (state == State.CLOSING || state == State.CLOSED || state == State.REPLACING) {
            return null;
        }
        State previous = state;
        state = State.REPLACING;
        openExpectation = false;
        return previous;
    }

    synchronized void restoreAfterReplacement(State previous) {
        if (state != State.REPLACING || previous == null) {
            return;
        }
        state = previous;
        openExpectation = false;
    }

    synchronized void prepareForReopen() {
        if (state == State.CLOSING || state == State.CLOSED) {
            return;
        }
        state = State.OPENING;
        windowId = -1;
        openExpectation = false;
    }

    synchronized void beginClosing() {
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSING;
        openExpectation = false;
    }

    synchronized void finishClosing() {
        state = State.CLOSED;
        openExpectation = false;
        windowId = -1;
        resyncScheduled.set(false);
    }

    /** Arms the one-shot expectation created by the matching InventoryOpenEvent. */
    synchronized boolean armOpenExpectation() {
        if (state != State.OPENING || windowId >= 0) {
            return false;
        }
        openExpectation = true;
        return true;
    }

    /** Invalidates an expectation when Bukkit opens a different inventory. */
    public synchronized void invalidateOpenExpectation() {
        openExpectation = false;
    }

    /**
     * Consumes the expectation and binds the server-assigned window id exactly
     * once. No title or user-controlled packet field is used as authority.
     */
    public synchronized boolean bindWindowId(int id) {
        if (id < 0 || state != State.OPENING || windowId >= 0 || !openExpectation) {
            return false;
        }
        openExpectation = false;
        windowId = id;
        state = State.OPEN;
        return true;
    }

    public boolean acceptsWindow(int id) {
        return state == State.OPEN && id >= 0 && windowId == id;
    }

    boolean markResyncScheduled() {
        return resyncScheduled.compareAndSet(false, true);
    }

    void clearResyncScheduled() {
        resyncScheduled.set(false);
    }

    private static ItemStack[] copyOverlay(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[MENU_SIZE];
        if (source == null) {
            return copy;
        }
        int limit = Math.min(source.length, copy.length);
        for (int slot = 0; slot < limit; slot++) {
            ItemStack item = source[slot];
            copy[slot] = item != null ? item.clone() : null;
        }
        return copy;
    }
}
