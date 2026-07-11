package studio.trc.bukkit.litesignin.configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Map;

import lombok.Getter;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import studio.trc.bukkit.litesignin.Main;
import studio.trc.bukkit.litesignin.message.MessageUtil;
import studio.trc.bukkit.litesignin.util.LiteSignInProperties;

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
    REWARD_SETTINGS("RewardSettings.yml", new YamlConfiguration()),

    /**
     * CustomItems.yml
     */
    CUSTOM_ITEMS("CustomItems.yml", new YamlConfiguration());

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
            ex.printStackTrace();
        }
    }

    public void saveConfig() {
        try {
            config.save("plugins/LiteSignIn/" + fileName);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public boolean reloadConfig() {
        try (InputStreamReader configFile = new InputStreamReader(new FileInputStream("plugins/LiteSignIn/" + fileName), LiteSignInProperties.getMessage("Charset"))) {
            config.load(configFile);
            return true;
        } catch (IOException | InvalidConfigurationException ex) {
            File oldFile = new File("plugins/LiteSignIn/" + fileName + ".old");
            File file = new File("plugins/LiteSignIn/" + fileName);
            Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
            placeholders.put("{file}", fileName);
            LiteSignInProperties.sendOperationMessage("ConfigurationLoadingError", placeholders);
            if (oldFile.exists()) {
                oldFile.delete();
            }
            file.renameTo(oldFile);
            saveResource();
            try (InputStreamReader newConfig = new InputStreamReader(new FileInputStream(file), LiteSignInProperties.getMessage("Charset"))) {
                config.load(newConfig);
                LiteSignInProperties.sendOperationMessage("ConfigurationRepair", MessageUtil.getDefaultPlaceholders());
            } catch (IOException | InvalidConfigurationException ex1) {
                ex1.printStackTrace();
            }
        }
        return false;
    }

    public String getLocalFilePath() {
        return fileName;
    }

    public RobustConfiguration getRobustConfig() {
        return ConfigurationUtil.getConfig(this);
    }
}
