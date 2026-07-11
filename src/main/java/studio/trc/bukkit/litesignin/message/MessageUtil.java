package studio.trc.bukkit.litesignin.message;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;

import me.clip.placeholderapi.PlaceholderAPI;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import studio.trc.bukkit.litesignin.Main;
import studio.trc.bukkit.litesignin.configuration.ConfigurationType;
import studio.trc.bukkit.litesignin.configuration.RobustConfiguration;
import studio.trc.bukkit.litesignin.message.color.ColorUtils;
import studio.trc.bukkit.litesignin.util.AdventureUtils;

public class MessageUtil
{
    private static final Map<String, String> defaultPlaceholders = new HashMap<>();

    @Getter
    @Setter
    private static boolean enabledPAPI = false;

    public static void loadPlaceholders() {
        defaultPlaceholders.clear();
        defaultPlaceholders.put("{plugin_version}", Main.getInstance().getDescription().getVersion());
        defaultPlaceholders.put("{prefix}", getPrefix());
    }

    public static void sendMessage(CommandSender sender, String message) {
        sendMessage(sender, message, defaultPlaceholders);
    }

    public static void sendMessage(CommandSender sender, String message, Map<String, String> placeholders) {
        sendAdventureMessage(sender, message, placeholders, null);
    }

    public static void sendMixedMessage(CommandSender sender, String message, Map<String, String> placeholders,
                                        Map<String, JSONComponent> additionalComponents, Map<String, String> additionalPlaceholders) {
        if (sender == null) return;
        String sample = replacePlaceholders(sender, message, placeholders);
        Map<String, Component> components = additionalComponents.entrySet()
            .stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getAdventureComponent(additionalPlaceholders)));
        if (!components.isEmpty()) {
            sendAdventureJSONMessage(sender, MessageEditor.createAdventureJSONMessage(sender, sample, components));
        } else {
            sendAdventureJSONMessage(sender, AdventureUtils.serializeText(sample));
        }
    }

    public static void sendMessageWithItem(CommandSender sender, String message, Map<String, String> placeholders, ItemStack item) {
        Map<String, Component> json = new HashMap<>();
        json.put("%item%", ItemHoverComponentFactory.getAdventureJSONItemStack(item));
        sendAdventureMessage(sender, message, placeholders, json);
    }

    public static void sendMessageWithJSONComponent(CommandSender sender, String message, Map<String, String> placeholders,
                                                    String componentKey, JSONComponent jsonComponent) {
        Map<String, Component> json = new HashMap<>();
        json.put(componentKey, jsonComponent.getAdventureComponent());
        sendAdventureMessage(sender, message, placeholders, json);
    }

    public static void sendMessage(CommandSender sender, List<String> messages) {
        messages.forEach(rawMessage -> sendMessage(sender, rawMessage));
    }

    public static void sendMessage(CommandSender sender, List<String> messages, Map<String, String> placeholders) {
        messages.forEach(rawMessage -> sendMessage(sender, rawMessage, placeholders));
    }

    public static void sendAdventureMessage(CommandSender sender, String message, Map<String, String> placeholders,
                                            Map<String, Component> additionalComponents) {
        if (sender == null) return;
        String sample = replacePlaceholders(sender, message, placeholders);
        if (additionalComponents != null && !additionalComponents.isEmpty()) {
            sendAdventureJSONMessage(sender, MessageEditor.createAdventureJSONMessage(sender, sample, additionalComponents));
        } else {
            sendAdventureJSONMessage(sender, AdventureUtils.serializeText(sample));
        }
    }

    public static void sendAdventureMessage(CommandSender sender, List<String> messages, Map<String, String> placeholders,
                                            Map<String, Component> jsonComponents) {
        messages.forEach(rawMessage -> sendAdventureMessage(sender, rawMessage, placeholders, jsonComponents));
    }

    public static void sendMessage(CommandSender sender, RobustConfiguration configuration, String configPath) {
        sendMessage(sender, configuration, configPath, defaultPlaceholders);
    }

    public static void sendMessage(CommandSender sender, RobustConfiguration configuration, String configPath, Map<String, String> placeholders) {
        sendAdventureMessage(sender, configuration, configPath, placeholders, null);
    }

    public static void sendAdventureMessage(CommandSender sender, RobustConfiguration configuration, String configPath,
                                            Map<String, String> placeholders, Map<String, Component> jsonComponents) {
        List<String> messages = configuration.getStringList(configPath);
        String message = configuration.getString(configPath);
        if (messages.isEmpty() && !"[]".equals(message)) {
            sendAdventureMessage(sender, message, placeholders, jsonComponents);
        } else {
            sendAdventureMessage(sender, messages, placeholders, jsonComponents);
        }
    }

    public static void sendAdventureJSONMessage(CommandSender sender, Component component) {
        if (sender != null && component != null) {
            ((Audience) sender).sendMessage(component);
        }
    }

    public static void sendConsoleMessage(String configPath, ConfigurationType type) {
        sendMessage(Bukkit.getConsoleSender(), type.getRobustConfig(), configPath, defaultPlaceholders);
    }

    public static void sendConsoleMessage(String configPath, ConfigurationType type, Map<String, String> placeholders) {
        sendMessage(Bukkit.getConsoleSender(), type.getRobustConfig(), configPath, placeholders);
    }

    public static void sendCommandMessage(CommandSender sender, String configPath) {
        sendMessage(sender, ConfigurationType.MESSAGES.getRobustConfig(), "Command-Messages." + configPath, defaultPlaceholders);
    }

    public static void sendCommandMessage(CommandSender sender, String configPath, Map<String, String> placeholders) {
        sendMessage(sender, ConfigurationType.MESSAGES.getRobustConfig(), "Command-Messages." + configPath, placeholders);
    }

    public static void sendCommandAdventureMessage(CommandSender sender, String configPath, Map<String, String> placeholders,
                                                   Map<String, Component> jsonComponents) {
        sendAdventureMessage(sender, ConfigurationType.MESSAGES.getRobustConfig(), "Command-Messages." + configPath, placeholders, jsonComponents);
    }

    public static void sendCommandMessageWithItem(CommandSender sender, String configPath, Map<String, String> placeholders, ItemStack item) {
        Map<String, Component> json = new HashMap<>();
        json.put("%item%", ItemHoverComponentFactory.getAdventureJSONItemStack(item));
        sendCommandAdventureMessage(sender, configPath, placeholders, json);
    }

    public static void sendCommandMessageWithJSONComponent(CommandSender sender, String configPath, Map<String, String> placeholders,
                                                           String componentKey, JSONComponent jsonComponent) {
        Map<String, Component> json = new HashMap<>();
        json.put(componentKey, jsonComponent.getAdventureComponent());
        sendCommandAdventureMessage(sender, configPath, placeholders, json);
    }

    public static String replacePlaceholders(String message, String key, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return replacePlaceholders(message, map, true);
    }

    public static String replacePlaceholders(String message, Map<String, String> placeholders) {
        return replacePlaceholders(message, placeholders, true);
    }

    public static String replacePlaceholders(String message, Map<String, String> placeholders, boolean toColor) {
        if (message == null || placeholders.isEmpty()) return ColorUtils.toColor(message);
        StringBuilder builder = new StringBuilder();
        Map<String, String> caseInsensitivePlaceholders = getCaseInsensitivePlaceholders(placeholders);
        try {
            List<MessageSection> sections = MessageEditor.parse(message, placeholders);
            sections.forEach(section -> {
                if (section.isPlaceholder()) {
                    String value = placeholders.get(section.getPlaceholder());
                    builder.append(value != null ? value : caseInsensitivePlaceholders.get(section.getPlaceholder().toLowerCase(Locale.ROOT)));
                } else {
                    builder.append(section.getText().replace("/n", "\n"));
                }
            });
            message = builder.toString();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return toColor ? ColorUtils.toColor(message) : message;
    }

    public static String replacePlaceholders(CommandSender sender, String message, Map<String, String> placeholders) {
        if (message == null || placeholders.isEmpty()) return ColorUtils.toColor(message);
        StringBuilder builder = new StringBuilder();
        Map<String, String> caseInsensitivePlaceholders = getCaseInsensitivePlaceholders(placeholders);
        try {
            List<MessageSection> sections = MessageEditor.parse(message, placeholders);
            sections.forEach(section -> {
                if (section.isPlaceholder()) {
                    String value = placeholders.get(section.getPlaceholder());
                    builder.append(value != null ? value : caseInsensitivePlaceholders.get(section.getPlaceholder().toLowerCase(Locale.ROOT)));
                } else {
                    builder.append(section.getText().replace("/n", "\n"));
                }
            });
            message = builder.toString();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return ColorUtils.toColor(toPlaceholderAPIResult(sender, message));
    }

    public static String toPlaceholderAPIResult(CommandSender sender, String text) {
        return text != null && isEnabledPAPI() && sender instanceof Player ? PlaceholderAPI.setPlaceholders((Player) sender, text) : text;
    }

    public static String getMessage(String configPath) {
        return ConfigurationType.MESSAGES.getRobustConfig().getString(configPath);
    }

    public static String getMessage(ConfigurationType configType, String configPath) {
        return configType.getRobustConfig().getString(configPath);
    }

    public static String getMessage(YamlConfiguration config, String configPath) {
        return config.getString(configPath);
    }

    public static List<String> getMessageList(String path) {
        return getMessageList(ConfigurationType.MESSAGES, path);
    }

    public static List<String> getMessageList(ConfigurationType configType, String configPath) {
        List<String> messages = configType.getRobustConfig().getStringList(configPath);
        String message = configType.getRobustConfig().getString(configPath);
        if (messages.isEmpty() && !"[]".equals(message)) {
            messages.add(message);
        }
        return messages;
    }

    public static List<String> getMessageList(YamlConfiguration config, String configPath) {
        List<String> messages = config.getStringList(configPath);
        if (config.contains(configPath)) {
            String message = config.getString(configPath);
            if (messages.isEmpty() && !"[]".equals(message)) {
                messages.add(message);
            }
        }
        return messages;
    }

    public static String doBasicProcessing(String text) {
        return replacePlaceholders(text, defaultPlaceholders);
    }

    public static String getPrefix() {
        return ConfigurationType.CONFIG.getRobustConfig().getString("Prefix");
    }

    public static Map<String, String> getDefaultPlaceholders() {
        return new HashMap<>(defaultPlaceholders);
    }

    private static Map<String, String> getCaseInsensitivePlaceholders(Map<String, String> placeholders) {
        Map<String, String> result = new HashMap<>();
        placeholders.forEach((key, value) -> {
            if (key != null) {
                result.put(key.toLowerCase(Locale.ROOT), value);
            }
        });
        return result;
    }

}
