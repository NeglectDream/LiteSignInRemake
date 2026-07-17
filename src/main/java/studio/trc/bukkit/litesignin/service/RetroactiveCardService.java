package studio.trc.bukkit.litesignin.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import studio.trc.bukkit.litesignin.util.PluginControl;

/**
 * Owns Bukkit inventory operations for physical retroactive cards.
 *
 * <p>The operation is intentionally split into planning and applying so the
 * caller can persist a durable journal before changing the inventory.</p>
 */
public final class RetroactiveCardService {
    private final UUID playerId;

    public RetroactiveCardService(UUID playerId) {
        this.playerId = playerId;
    }

    public boolean isEnabled() {
        return PluginControl.enableRetroactiveCardRequiredItem();
    }

    public int count() {
        if (!isEnabled()) {
            return 0;
        }
        Player player = getPlayer();
        String displayName = getRequiredDisplayName();
        if (displayName == null) {
            return 0;
        }
        return countMatching(player, displayName);
    }

    /** Plans a reservation without modifying the player's inventory. */
    public CardReservation plan(int amount) {
        if (amount < 1 || !isEnabled() || !Bukkit.isPrimaryThread()) {
            return null;
        }
        Player player = getPlayer();
        String displayName = getRequiredDisplayName();
        if (player == null || displayName == null) {
            return null;
        }

        List<CardSlot> changedSlots = new ArrayList<>();
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getSize() && remaining > 0; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            if (!isMatchingCard(current, displayName)) {
                continue;
            }
            ItemStack original = current.clone();
            ItemStack replacement;
            if (current.getAmount() <= remaining) {
                remaining -= current.getAmount();
                replacement = null;
            } else {
                replacement = current.clone();
                replacement.setAmount(current.getAmount() - remaining);
                remaining = 0;
            }
            changedSlots.add(new CardSlot(slot, original, replacement));
        }

        return remaining > 0 ? null : new CardReservation(playerId, amount, changedSlots, null);
    }

    /** Applies a previously persisted reservation after the journal is durable. */
    public boolean apply(CardReservation reservation) {
        if (reservation == null || !reservation.belongsTo(playerId) || !Bukkit.isPrimaryThread()) {
            return false;
        }
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        for (CardSlot changedSlot : reservation.slots()) {
            if (!Objects.equals(player.getInventory().getItem(changedSlot.slot()), changedSlot.original())) {
                return false;
            }
        }
        for (CardSlot changedSlot : reservation.slots()) {
            ItemStack replacement = changedSlot.replacement();
            player.getInventory().setItem(changedSlot.slot(),
                    replacement != null ? replacement.clone() : null);
        }
        return true;
    }

    /** Compatibility operation for callers that do not need journaling. */
    public CardReservation reserve(int amount) {
        CardReservation reservation = plan(amount);
        return reservation != null && apply(reservation) ? reservation : null;
    }

    /** Restores the exact slots changed by a previous reservation. */
    public boolean restore(CardReservation reservation) {
        if (reservation == null || !reservation.belongsTo(playerId) || !Bukkit.isPrimaryThread()) {
            return false;
        }
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        for (CardSlot changedSlot : reservation.slots()) {
            player.getInventory().setItem(changedSlot.slot(), changedSlot.original().clone());
        }
        return true;
    }

    /** Restores only the missing part, never overwriting current inventory items. */
    public void restoreMissing(ItemStack[] originalItems, int expectedCount) {
        if (originalItems == null || expectedCount < 1 || !isEnabled() || !Bukkit.isPrimaryThread()) {
            return;
        }
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        int present = 0;
        for (ItemStack original : originalItems) {
            if (original != null && !original.getType().isAir()) {
                present += original.getAmount();
            }
        }
        int missing = expectedCount - Math.min(present, expectedCount);
        if (missing <= 0) {
            return;
        }
        for (ItemStack original : originalItems) {
            if (original != null && !original.getType().isAir()) {
                giveItem(player, original, missing);
                return;
            }
        }
    }

    /** Restores missing cards from a serialized snapshot, keeping deserialization in the service. */
    public void restoreMissing(byte[] snapshot, int expectedCount) {
        restoreMissing(deserializeItems(snapshot), expectedCount);
    }

    private Player getPlayer() {
        return Bukkit.getPlayer(playerId);
    }

    /**
     * Reads the configured display name that identifies a physical retroactive card.
     *
     * <p>Matching is intentionally based on this single field so administrators only
     * need to keep the item's display name consistent; material, lore, enchantments
     * and NBT are deliberately ignored.
     */
    private static String getRequiredDisplayName() {
        return PluginControl.getRetroactiveCardRequiredItemName();
    }

    private static int countMatching(Player player, String displayName) {
        if (player == null || displayName == null) {
            return 0;
        }
        int amount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isMatchingCard(item, displayName)) {
                amount += item.getAmount();
            }
        }
        return amount;
    }

    private static void giveItem(Player player, ItemStack source, int amount) {
        ItemStack item = source.clone();
        item.setAmount(amount);
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(item);
        } else {
            player.getWorld().dropItem(player.getLocation(), item);
        }
    }

    /**
     * Matches an inventory item against the configured card display name.
     *
     * <p>Only the item's {@link ItemMeta#getDisplayName() display name} (with color
     * codes) is compared; all other properties are ignored on purpose.
     */
    private static boolean isMatchingCard(ItemStack item, String displayName) {
        if (item == null || displayName == null || item.getType().isAir()) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        return displayName.equals(item.getItemMeta().getDisplayName());
    }

    private static byte[] serializeItems(ItemStack[] items) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(items);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Unable to serialize physical-card snapshot", error);
        }
    }

    public static ItemStack[] deserializeItems(byte[] snapshot) {
        if (snapshot == null || snapshot.length == 0) {
            return new ItemStack[0];
        }
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(
                new ByteArrayInputStream(snapshot))) {
            Object value = input.readObject();
            if (!(value instanceof ItemStack[] items)) {
                throw new IllegalStateException("Physical-card snapshot has an invalid type");
            }
            return items;
        } catch (IOException | ClassNotFoundException error) {
            throw new IllegalStateException("Unable to deserialize physical-card snapshot", error);
        }
    }

    /** Immutable description of one physical-card reservation. */
    public static final class CardReservation {
        private final UUID playerId;
        private final int cardCount;
        private final List<CardSlot> slots;
        private final UUID operationId;

        private CardReservation(UUID playerId, int cardCount, List<CardSlot> slots, UUID operationId) {
            this.playerId = playerId;
            this.cardCount = cardCount;
            this.slots = List.copyOf(slots);
            this.operationId = operationId;
        }

        public CardReservation withOperationId(UUID operationId) {
            return new CardReservation(playerId, cardCount, slots, operationId);
        }

        public UUID operationId() {
            return operationId;
        }

        public int cardCount() {
            return cardCount;
        }

        public ItemStack[] snapshotItems() {
            ItemStack[] snapshot = new ItemStack[slots.size()];
            for (int index = 0; index < slots.size(); index++) {
                snapshot[index] = slots.get(index).original().clone();
            }
            return snapshot;
        }

        public byte[] serializeSnapshot() {
            return serializeItems(snapshotItems());
        }

        private boolean belongsTo(UUID uuid) {
            return playerId.equals(uuid);
        }

        private List<CardSlot> slots() {
            return slots;
        }
    }

    private record CardSlot(int slot, ItemStack original, ItemStack replacement) {}
}
