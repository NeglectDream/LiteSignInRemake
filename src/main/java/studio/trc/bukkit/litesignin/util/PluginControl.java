package studio.trc.bukkit.litesignin.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import studio.trc.bukkit.litesignin.Main;
import studio.trc.bukkit.litesignin.configuration.RobustConfiguration;
import studio.trc.bukkit.litesignin.configuration.ConfigurationUtil;
import studio.trc.bukkit.litesignin.configuration.ConfigurationType;
import studio.trc.bukkit.litesignin.database.DatabaseException;
import studio.trc.bukkit.litesignin.database.storage.SQLiteStorage;
import studio.trc.bukkit.litesignin.database.engine.SQLiteEngine;
import studio.trc.bukkit.litesignin.message.MessageUtil;
import studio.trc.bukkit.litesignin.message.color.ColorUtils;
import studio.trc.bukkit.litesignin.event.Menu;
import studio.trc.bukkit.litesignin.gui.SignInMenuService;
import studio.trc.bukkit.litesignin.thread.LiteSignInThread;

public class PluginControl {
    private static final long EXECUTOR_SHUTDOWN_SECONDS = 10L;

    private PluginControl() {}

    /** Initializes configuration, database, PAPI and the asynchronous executor. */
    public static boolean initialize() {
        try {
            ConfigurationUtil.reloadConfig();
            MessageUtil.loadPlaceholders();
            SQLiteEngine candidate = createConnectedEngine();
            SQLiteEngine.setInstance(candidate);
            configurePlaceholderAPI();
            LiteSignInThread.initialize();
            return true;
        } catch (RuntimeException error) {
            logLifecycleFailure("LiteSignIn initialization failed", error);
            SQLiteEngine failed = SQLiteEngine.getInstance();
            if (failed != null) {
                failed.disconnect();
                SQLiteEngine.setInstance(null);
            }
            return false;
        }
    }

    /**
     * Reloads runtime resources without closing the active database until the
     * replacement connection has been validated.
     */
    public static boolean reload() {
        SQLiteEngine oldEngine = SQLiteEngine.getInstance();
        RobustConfiguration oldConfig = ConfigurationUtil.getConfig(ConfigurationType.CONFIG);
        String oldPath = oldConfig.getString("SQLite-Storage.Database-Path");
        String oldFile = oldConfig.getString("SQLite-Storage.Database-File");
        String oldTable = oldConfig.getString("SQLite-Storage.Table-Name");

        closeOpenMenus();
        LiteSignInThread.shutdownAndAwait(EXECUTOR_SHUTDOWN_SECONDS, TimeUnit.SECONDS);
        try {
            SQLiteStorage.flushAll();
            ConfigurationUtil.reloadConfig();
            MessageUtil.loadPlaceholders();

            SQLiteEngine candidate = createConnectedEngine();
            SQLiteEngine.setInstance(candidate);
            if (oldEngine != null) {
                oldEngine.disconnect();
            }
            SQLiteStorage.clearCache();
            configurePlaceholderAPI();
            LiteSignInThread.initialize();
            return true;
        } catch (RuntimeException error) {
            // Restore the old database settings in memory so the still-open
            // engine keeps using its original table and file contract.
            RobustConfiguration current = ConfigurationUtil.getConfig(ConfigurationType.CONFIG);
            current.set("SQLite-Storage.Database-Path", oldPath);
            current.set("SQLite-Storage.Database-File", oldFile);
            current.set("SQLite-Storage.Table-Name", oldTable);
            SQLiteEngine candidate = SQLiteEngine.getInstance();
            if (candidate != null && candidate != oldEngine) {
                candidate.disconnect();
            }
            SQLiteEngine.setInstance(oldEngine);
            configurePlaceholderAPI();
            LiteSignInThread.initialize();
            logLifecycleFailure("LiteSignIn reload failed; the previous database remains active", error);
            return false;
        }
    }

    public static boolean shutdownAsyncTasks() {
        return LiteSignInThread.shutdownAndAwait(EXECUTOR_SHUTDOWN_SECONDS, TimeUnit.SECONDS);
    }

    public static void reloadSQLite() {
        SQLiteEngine replacement = createConnectedEngine();
        SQLiteEngine previous = SQLiteEngine.getInstance();
        SQLiteEngine.setInstance(replacement);
        if (previous != null) {
            previous.disconnect();
        }
    }

    public static void savePlayersData() {
        SQLiteStorage.flushAll();
    }

    private static SQLiteEngine createConnectedEngine() {
        RobustConfiguration config = ConfigurationUtil.getConfig(ConfigurationType.CONFIG);
        SQLiteEngine candidate = new SQLiteEngine(config.getString("SQLite-Storage.Database-Path"),
                config.getString("SQLite-Storage.Database-File"));
        candidate.connect();
        return candidate;
    }

    private static void closeOpenMenus() {
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> SignInMenuService.isOpening(player.getUniqueId()))
                .forEach(player -> SignInMenuService.close(player));
    }

    private static void configurePlaceholderAPI() {
        try {
            boolean enabled = ConfigurationType.CONFIG.getRobustConfig().getBoolean("PlaceholderAPI.Enabled");
            PlaceholderAPIImpl expansion = PlaceholderAPIImpl.getInstance();
            if (enabled) {
                if (!expansion.isRegistered()) {
                    expansion.register();
                }
                MessageUtil.setEnabledPAPI(true);
                MessageUtil.sendConsoleMessage("Console-Messages.Find-The-PlaceholderAPI",
                        ConfigurationType.MESSAGES, MessageUtil.getDefaultPlaceholders());
            } else {
                if (expansion.isRegistered()) {
                    expansion.unregister();
                }
                MessageUtil.setEnabledPAPI(false);
            }
        } catch (NoClassDefFoundError | Exception error) {
            MessageUtil.setEnabledPAPI(false);
            MessageUtil.sendConsoleMessage("Console-Messages.PlaceholderAPI-Not-Found",
                    ConfigurationType.MESSAGES, MessageUtil.getDefaultPlaceholders());
        }
    }

    private static void logLifecycleFailure(String message, Throwable error) {
        Main plugin = Main.getInstance();
        if (plugin != null) {
            plugin.getLogger().log(Level.SEVERE, message, error);
        }
    }
    
    public static void hideEnchants(ItemMeta im) {
        im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }
    
    public static void setHead(ItemStack is, String name) {
        if (name == null || !(is.getItemMeta() instanceof SkullMeta)) return;
        OfflinePlayer owner = Bukkit.getOfflinePlayer(name);
        UUID uuid = owner.getUniqueId();
        String texture = SkullManager.getBase64Meta().get(uuid);
        if (texture != null) {
            is.setItemMeta(SkullManager.getHeadWithTextures(texture).getItemMeta());
            return;
        }
        SkullMeta meta = (SkullMeta) is.getItemMeta();
        meta.setOwningPlayer(owner);
        is.setItemMeta(meta);
        String lookupName = owner.getName() != null ? owner.getName() : name;
        LiteSignInThread.runTask(() -> SkullManager.refreshTexture(uuid, lookupName));
    }
    
    public static int getRetroactiveCardQuantityRequired() {
        return ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getInt("Retroactive-Card.Quantity-Required");
    }
    
    public static double getRetroactiveCardIntervals() {
        return ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getDouble("Retroactive-Card.Intervals");
    }
    
    public static double getSQLiteRefreshInterval() {
        return ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getDouble("SQLite-Storage.Refresh-Interval");
    }

    public static boolean usePlaceholderAPI() {
        return ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getBoolean("PlaceholderAPI.Enabled");
    }

    public static boolean enableSignInGUI() {
        return ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getBoolean("GUI-Settings");
    }

     public static boolean enableRetroactiveCard() {
        return ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getBoolean("Retroactive-Card.Enabled");
    }

     public static boolean enableRetroactiveCardRequiredItem() {
        return ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getBoolean("Retroactive-Card.Required-Item.Enabled");
    }

    public static boolean enableJoinEvent() {
        return ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getBoolean("Join-Event.Enabled");
    }

    public static boolean autoSignIn() {
        return ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getBoolean("Join-Event.Auto-SignIn");
    }

    public static SignInDate getRetroactiveCardMinimumDate() {
        return SignInDate.getInstance(ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getString("Retroactive-Card.Minimum-Date"));
    }
    
    public static String getPrefix() {
        return ColorUtils.toColor(ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getString("Prefix"));
    }
    
    /**
     * Returns the configured display name used to identify a physical retroactive
     * card in a player's inventory.
     *
     * <p>Physical-card matching is now purely DisplayName-based: only an item's
     * {@link ItemMeta#getDisplayName() display name} (with color codes) is compared
     * against this value. Material, lore and enchantments are intentionally ignored
     * so administrators only need to keep one stable field consistent.
     *
     * @return the color-translated display name, or {@code null} when the
     *         Required-Item feature is disabled or the Name node is missing
     */
    public static String getRetroactiveCardRequiredItemName() {
        if (!enableRetroactiveCardRequiredItem()) {
            return null;
        }
        String name = ConfigurationUtil.getConfig(ConfigurationType.CONFIG)
                .getString("Retroactive-Card.Required-Item.Name");
        return name == null ? null : ColorUtils.toColor(name);
    }
    
    public static int getRandom(int number1, int number2) {
        if (number1 == number2) {
            return number1;
        } else if (number1 > number2) {
            return new Random().nextInt(number1 - number2 + 1) + number2;
        } else if (number2 > number1) {
            return new Random().nextInt(number2 - number1 + 1) + number1;
        }
        return 0;
    }
    
    public static int getRandom(String placeholder) {
        String[] random = placeholder.split("-");
        try {
            int number1 = Integer.valueOf(random[0]);
            int number2 = Integer.valueOf(random[1]);
            if (number1 == number2) {
                return number1;
            } else if (number1 > number2) {
                return new Random().nextInt(number1 - number2 + 1) + number2;
            } else if (number2 > number1) {
                return new Random().nextInt(number2 - number1 + 1) + number1;
            }
        } catch (NumberFormatException ex) {}
        return 0;
    }
}
