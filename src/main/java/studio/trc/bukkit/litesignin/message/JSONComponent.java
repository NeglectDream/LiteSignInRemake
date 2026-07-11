package studio.trc.bukkit.litesignin.message;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import studio.trc.bukkit.litesignin.util.AdventureUtils;
import studio.trc.bukkit.litesignin.configuration.ConfigurationType;

public class JSONComponent
{
    @Getter
    private final String text;
    @Getter
    private final String clickAction;
    @Getter
    private final String clickContent;
    @Getter
    private final List<String> hoverContent;

    private Component adventureComponent = null;

    public JSONComponent(String text, List<String> hoverContent, String clickAction, String clickContent) {
        this.text = text;
        this.hoverContent = hoverContent;
        this.clickAction = clickAction;
        this.clickContent = clickContent;
    }

    public Component getAdventureComponent() {
        if (adventureComponent == null) {
            adventureComponent = buildComponent(MessageUtil.getDefaultPlaceholders(), false);
        }
        return adventureComponent;
    }

    public Component getAdventureComponent(Map<String, String> placeholders) {
        return buildComponent(placeholders, true);
    }

    private Component buildComponent(Map<String, String> placeholders, boolean replacePlaceholders) {
        try {
            HoverEvent<?> hoverEvent = null;
            ClickEvent clickEvent = null;
            if (!hoverContent.isEmpty()) {
                String hoverText = String.join("\n", hoverContent.stream()
                        .map(hover -> replacePlaceholders ? MessageUtil.replacePlaceholders(hover, placeholders) : MessageUtil.doBasicProcessing(hover))
                        .collect(Collectors.toList()));
                hoverEvent = AdventureUtils.showText(hoverText);
            }
            if (clickAction != null) {
                String clickText = replacePlaceholders ? MessageUtil.replacePlaceholders(clickContent, placeholders) : MessageUtil.doBasicProcessing(clickContent);
                clickEvent = AdventureUtils.getClickEvent(clickAction, clickText);
            }
            String displayText = replacePlaceholders ? MessageUtil.replacePlaceholders(text, placeholders) : MessageUtil.doBasicProcessing(text);
            Component component = AdventureUtils.serializeText(displayText);
            if (hoverEvent != null) component = AdventureUtils.setHoverEvent(component, hoverEvent);
            if (clickEvent != null) component = AdventureUtils.setClickEvent(component, clickEvent);
            return component;
        } catch (Exception ex) {
            placeholders.put("{exception}", ex.getLocalizedMessage() != null ? ex.getLocalizedMessage() : "null");
            MessageUtil.sendConsoleMessage("Console-Messages.Loading-JSON-Component-Failed", ConfigurationType.MESSAGES, placeholders);
            ex.printStackTrace();
        }
        return Component.empty();
    }
}
