package studio.trc.bukkit.litesignin.util;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import studio.trc.bukkit.litesignin.Main;
import studio.trc.bukkit.litesignin.message.MessageUtil;
import studio.trc.bukkit.litesignin.message.color.ColorUtils;

public class LiteSignInProperties
{
    /**
     * Console operation messages.
     */
    public static Properties propertiesFile = new Properties();
    
    public static void reloadProperties() {
        propertiesFile.clear();
        try (InputStream input = Main.class.getResourceAsStream("/LiteSignIn.properties")) {
            if (input == null) {
                throw new IllegalStateException("Default properties not found: LiteSignIn.properties");
            }
            propertiesFile.load(input);
            sendOperationMessage("PropertiesLoaded");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public static void sendOperationMessage(String path) {
        CommandSender sender = Bukkit.getConsoleSender();
        if (propertiesFile.containsKey(path)) {
            sender.sendMessage(ColorUtils.toColor(propertiesFile.getProperty(path)));
        }
    }
    
    public static void sendOperationMessage(String path, Map<String, String> placeholders) {
        CommandSender sender = Bukkit.getConsoleSender();
        if (propertiesFile.containsKey(path)) {
            String message = propertiesFile.getProperty(path);
            MessageUtil.sendMessage(sender, message, placeholders);
        }
    }
    
    public static String getMessage(String configPath) {
        return propertiesFile.getProperty(configPath);
    }
    
    public static String getMessage(String configPath, Map<String, String> placeholders) {
        return MessageUtil.replacePlaceholders(propertiesFile.getProperty(configPath), placeholders);
    }
}
