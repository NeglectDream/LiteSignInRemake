package studio.trc.bukkit.litesignin.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import studio.trc.bukkit.litesignin.message.ItemHoverComponentFactory;

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

    public static Map<String, Component> getItemDisplay(List<CustomItem> itemList) {
        Map<String, Component> json = new HashMap<>();
        Component component = Component.text("");
        for (int i = 0;i < itemList.size();i++) {
            component = component.append(ItemHoverComponentFactory.getAdventureJSONItemStack(itemList.get(i).getItemStack()));
            if (i != itemList.size() - 1) {
                component = component.append(Component.text(", "));
            }
        }
        json.put("%list%", component);
        return json;
    }
}
