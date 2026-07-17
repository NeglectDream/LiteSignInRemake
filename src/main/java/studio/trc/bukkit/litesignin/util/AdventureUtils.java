package studio.trc.bukkit.litesignin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Adventure component helpers for the Minecraft 1.21.x runtime.
 */
public class AdventureUtils
{
    public static Component setHoverEvent(Component component, HoverEvent event) {
        return component.hoverEvent(event);
    }

    public static Component setClickEvent(Component component, ClickEvent event) {
        return component.clickEvent(event);
    }

    public static Component serializeText(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    public static String deserializeText(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    public static HoverEvent showText(String text) {
        return HoverEvent.showText(AdventureUtils.serializeText(text));
    }

    public static ClickEvent getClickEvent(String action, String text) {
        switch (action.toUpperCase()) {
            case "SUGGEST_COMMAND": {
                return ClickEvent.suggestCommand(text);
            }
            case "RUN_COMMAND": {
                return ClickEvent.runCommand(text);
            }
            case "OPEN_URL": {
                return ClickEvent.openUrl(text);
            }
            case "COPY_TO_CLIPBOARD": {
                return ClickEvent.copyToClipboard(text);
            }
            case "OPEN_FILE": {
                return ClickEvent.openFile(text);
            }
        }
        return null;
    }
}
