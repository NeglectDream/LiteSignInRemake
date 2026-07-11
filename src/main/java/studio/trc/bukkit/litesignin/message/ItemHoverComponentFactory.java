package studio.trc.bukkit.litesignin.message;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;

import org.bukkit.inventory.ItemStack;

/**
 * Builds item hover components for Minecraft 1.21.x using Adventure only.
 */
public final class ItemHoverComponentFactory
{
    private ItemHoverComponentFactory() {}

    public static Component getAdventureJSONItemStack(ItemStack item) {
        if (isEmpty(item)) {
            return Component.text("");
        }
        Component component = getAdventureDisplayComponent(item);
        try {
            return component.hoverEvent(HoverEvent.showItem(Key.key(getItemKey(item)), item.getAmount()));
        } catch (RuntimeException ignored) {
            return component.hoverEvent(HoverEvent.showText(component));
        }
    }

    private static Component getAdventureDisplayComponent(ItemStack item) {
        return Component.translatable(item.getTranslationKey());
    }

    private static String getItemKey(ItemStack item) {
        return item.getType().getKey().toString();
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
