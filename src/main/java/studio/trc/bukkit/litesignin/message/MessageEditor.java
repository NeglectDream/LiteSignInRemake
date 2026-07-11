package studio.trc.bukkit.litesignin.message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.kyori.adventure.text.Component;

import org.bukkit.command.CommandSender;

import studio.trc.bukkit.litesignin.util.AdventureUtils;

public class MessageEditor
{
    public static Component createAdventureJSONMessage(CommandSender sender, String message, Map<String, Component> components) {
        List<MessageSection> sections = parse(message, components);
        Component component = null;
        for (MessageSection section : sections) {
            if (section.isPlaceholder()) {
                component = component == null ? section.getComponent() : component.append(section.getComponent());
            } else {
                String text = MessageUtil.toPlaceholderAPIResult(sender, section.getText()).replace("/n", "\n");
                Component textComponent = AdventureUtils.serializeText(text);
                component = component == null ? textComponent : component.append(textComponent);
            }
        }
        return component != null ? component : Component.text("");
    }

    public static <T> List<MessageSection> parse(String message, Map<String, T> placeholders) {
        Map<String, T> normalizedMap = new HashMap<>();
        placeholders.forEach((key, value) -> {
            if (key != null) {
                normalizedMap.put(key.toLowerCase(Locale.ROOT), value);
            }
        });

        List<String> sortedKeys = new ArrayList<>(normalizedMap.keySet());
        sortedKeys.sort((s1, s2) -> Integer.compare(s2.length(), s1.length()));

        List<MessageSection> result = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        int index = 0;
        int messageLength = message.length();
        int textStart = 0;

        while (index < messageLength) {
            boolean matched = false;
            for (String keyLower : sortedKeys) {
                int keyLength = keyLower.length();
                int endIndex = index + keyLength;
                if (endIndex > messageLength) continue;
                String paragraph = message.substring(index, endIndex);
                if (paragraph.toLowerCase(Locale.ROOT).equals(keyLower)) {
                    if (currentText.length() > 0) {
                        result.add(new MessageSection(
                            currentText.toString(),
                            null,
                            textStart,
                            textStart + currentText.length()
                        ));
                        currentText.setLength(0);
                    }
                    T replacement = normalizedMap.get(keyLower);
                    if (replacement instanceof Component) {
                        result.add(new MessageSection(
                            (Component) replacement,
                            paragraph,
                            index,
                            index + keyLength
                        ));
                    } else if (replacement instanceof String) {
                        result.add(new MessageSection(
                            replacement.toString(),
                            paragraph,
                            index,
                            index + keyLength
                        ));
                    }
                    index = endIndex;
                    textStart = index;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                if (currentText.length() == 0) {
                    textStart = index;
                }
                currentText.append(message.charAt(index));
                index++;
            }
        }

        if (currentText.length() > 0) {
            result.add(new MessageSection(
                currentText.toString(),
                null,
                textStart,
                textStart + currentText.length()
            ));
        }
        return result;
    }
}
