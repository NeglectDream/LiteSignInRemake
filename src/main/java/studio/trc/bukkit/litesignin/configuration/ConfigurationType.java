package studio.trc.bukkit.litesignin.configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import lombok.Getter;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import studio.trc.bukkit.litesignin.Main;
import studio.trc.bukkit.litesignin.message.MessageUtil;

public enum ConfigurationType
{
    /**
     * Config.yml
     */
    CONFIG("Config.yml", new YamlConfiguration()),

    /**
     * Messages.yml
     */
    MESSAGES("Messages.yml", new YamlConfiguration()),

    /**
     * GUISettings.yml
     */
    GUI_SETTINGS("GUISettings.yml", new YamlConfiguration()),

    /**
     * RewardSettings.yml
     */
    REWARD_SETTINGS("RewardSettings.yml", new YamlConfiguration());

    private static final Logger LOGGER = Logger.getLogger(ConfigurationType.class.getName());

    @Getter
    private final String fileName;
    @Getter
    private final YamlConfiguration config;

    private ConfigurationType(String fileName, YamlConfiguration config) {
        this.fileName = fileName;
        this.config = config;
    }

    public void saveResource() {
        File dataFolder = new File("plugins/LiteSignIn/");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File configFile = new File(dataFolder, fileName);
        if (configFile.exists()) {
            return;
        }
        try (InputStream input = Main.class.getResourceAsStream("/" + getLocalFilePath())) {
            if (input == null) {
                throw new IOException("Default configuration not found: " + getLocalFilePath());
            }
            try (OutputStream output = new FileOutputStream(configFile)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) != -1) {
                    output.write(buffer, 0, length);
                }
            }
        } catch (IOException ex) {
            logFailure("Failed to save default configuration " + fileName, ex);
        }
    }

    public void saveConfig() {
        try {
            config.save("plugins/LiteSignIn/" + fileName);
        } catch (IOException ex) {
            logFailure("Failed to save configuration " + fileName, ex);
        }
    }

    public boolean reloadConfig() {
        try (InputStreamReader configFile = new InputStreamReader(new FileInputStream("plugins/LiteSignIn/" + fileName), StandardCharsets.UTF_8)) {
            config.load(configFile);
            return true;
        } catch (IOException | InvalidConfigurationException ex) {
            File oldFile = new File("plugins/LiteSignIn/" + fileName + ".old");
            File file = new File("plugins/LiteSignIn/" + fileName);
            Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
            placeholders.put("{file}", fileName);
            MessageUtil.sendConsoleMessage("Console-Messages.Configuration-Loading-Error", ConfigurationType.MESSAGES, placeholders);
            if (oldFile.exists()) {
                oldFile.delete();
            }
            file.renameTo(oldFile);
            saveResource();
            try (InputStreamReader newConfig = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                config.load(newConfig);
                MessageUtil.sendConsoleMessage("Console-Messages.Configuration-Repair", ConfigurationType.MESSAGES, MessageUtil.getDefaultPlaceholders());
            } catch (IOException | InvalidConfigurationException ex1) {
                logFailure("Failed to repair configuration " + fileName, ex1);
            }
        }
        return false;
    }

    private static void logFailure(String message, Throwable error) {
        Main plugin = Main.getInstance();
        if (plugin != null) {
            plugin.getLogger().log(Level.SEVERE, message, error);
        } else {
            LOGGER.log(Level.SEVERE, message, error);
        }
    }

    public String getLocalFilePath() {
        return fileName;
    }

    public RobustConfiguration getRobustConfig() {
        return ConfigurationUtil.getConfig(this);
    }
}
