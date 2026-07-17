package studio.trc.bukkit.litesignin.configuration;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;

import studio.trc.bukkit.litesignin.Main;

public class DefaultConfigurationFile
{
    private static final Logger LOGGER = Logger.getLogger(DefaultConfigurationFile.class.getName());
    private final static Map<ConfigurationType, YamlConfiguration> cacheDefaultConfig = new HashMap<>();
    private final static Map<ConfigurationType, Boolean> isDefaultConfigLoaded = new HashMap<>();
    
    public static YamlConfiguration getDefaultConfig(ConfigurationType type) {
        if (!isDefaultConfigLoaded.containsKey(type) || !isDefaultConfigLoaded.get(type)) {
            loadDefaultConfigurationFile(type);
            isDefaultConfigLoaded.put(type, true);
        }
        return cacheDefaultConfig.get(type);
    }
    
    public static void loadDefaultConfigurationFile(ConfigurationType fileType) {
        String filePath = getDefaultConfigurationFilePath(fileType);
        try (Reader config = new InputStreamReader(Main.getInstance().getClass().getResource(filePath).openStream(), StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(config);
            cacheDefaultConfig.put(fileType, yaml);
        } catch (Exception ex) {
            logFailure("Failed to load default configuration " + fileType.getFileName(), ex);
        }
    }

    private static void logFailure(String message, Throwable error) {
        Main plugin = Main.getInstance();
        if (plugin != null) {
            plugin.getLogger().log(Level.SEVERE, message, error);
        } else {
            LOGGER.log(Level.SEVERE, message, error);
        }
    }

    public static String getDefaultConfigurationFilePath(ConfigurationType fileType) {
        return "/" + fileType.getLocalFilePath();
    }
}