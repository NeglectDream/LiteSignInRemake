package studio.trc.bukkit.litesignin.configuration;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;

import studio.trc.bukkit.litesignin.Main;

public class DefaultConfigurationFile
{
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
            ex.printStackTrace();
        }
    }
    
    public static String getDefaultConfigurationFilePath(ConfigurationType fileType) {
        return "/" + fileType.getLocalFilePath();
    }
}