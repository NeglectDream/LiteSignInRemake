package studio.trc.bukkit.litesignin.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import studio.trc.bukkit.litesignin.Main;
import studio.trc.bukkit.litesignin.configuration.RobustConfiguration;
import studio.trc.bukkit.litesignin.configuration.ConfigurationUtil;
import studio.trc.bukkit.litesignin.configuration.ConfigurationType;
import studio.trc.bukkit.litesignin.database.storage.SQLiteStorage;
import studio.trc.bukkit.litesignin.database.engine.SQLiteEngine;
import studio.trc.bukkit.litesignin.message.MessageUtil;
import studio.trc.bukkit.litesignin.message.color.ColorUtils;
import studio.trc.bukkit.litesignin.event.Menu;
import studio.trc.bukkit.litesignin.packet.PacketSignInMenuService;
import studio.trc.bukkit.litesignin.thread.LiteSignInThread;
import studio.trc.bukkit.litesignin.queue.SignInQueue;

public class PluginControl
{
    public static void reload() {
        ConfigurationUtil.reloadConfig();
        MessageUtil.loadPlaceholders();
        SQLiteStorage.cache.clear();
        reloadSQLite();
        SignInQueue.getInstance().loadQueue();
        try {
            if (ConfigurationType.CONFIG.getRobustConfig().getBoolean("PlaceholderAPI.Enabled")) {
                if (!PlaceholderAPIImpl.getInstance().isRegistered()) {
                    PlaceholderAPIImpl.getInstance().register();
                }
                MessageUtil.setEnabledPAPI(true);
                MessageUtil.sendConsoleMessage("Console-Messages.Find-The-PlaceholderAPI", ConfigurationType.MESSAGES, MessageUtil.getDefaultPlaceholders());
            }
        } catch (Error ex) {
            MessageUtil.setEnabledPAPI(false);
            MessageUtil.sendConsoleMessage("Console-Messages.PlaceholderAPI-Not-Found", ConfigurationType.MESSAGES, MessageUtil.getDefaultPlaceholders());
        }
        Bukkit.getOnlinePlayers().stream().filter(ps -> PacketSignInMenuService.isOpening(ps.getUniqueId())).forEachOrdered(Menu::closeGUI);
        LiteSignInThread.initialize();
    }

    public static void reloadSQLite() {
        RobustConfiguration config = ConfigurationUtil.getConfig(ConfigurationType.CONFIG);
        if (SQLiteEngine.getInstance() != null) {
            SQLiteEngine.getInstance().disconnect();
        }
        SQLiteEngine.setInstance(new SQLiteEngine(config.getString("SQLite-Storage.Database-Path"), config.getString("SQLite-Storage.Database-File")));
        SQLiteEngine.getInstance().connect();
    }

    public static void savePlayersData() {
        SQLiteStorage.cache.values().stream().forEach(SQLiteStorage::saveData);
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
        LiteSignInThread.runTask(() -> SkullManager.refreshTexture(uuid, owner.getName() != null ? owner.getName() : name));
    }
    
    public static int getRetroactiveCardQuantityRequired() {
        return ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getInt("Retroactive-Card.Quantity-Required");
    }
    
    public static int getGUILimitedDateYear() {
        return ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getInt("GUI-Settings.Limit-Date.Minimum-Year");
    }
    
    public static int getGUILimitedDateMonth() {
        return ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getInt("GUI-Settings.Limit-Date.Minimum-Month");
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

    public static boolean enableSignInRanking() {
        return ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getBoolean("Enable-Sign-In-Ranking");
    }

    public static boolean enableSignInGUI() {
        return ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getBoolean("GUI-Settings.Enabled");
    }

    public static boolean enableGUILimitDate() {
        return ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getBoolean("GUI-Settings.Limit-Date.Enabled");
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
    
    public static SignInDate getGUILimitedDate() {
        return SignInDate.getInstance(getGUILimitedDateYear(), getGUILimitedDateMonth(), 1);
    }
    
    public static String getPrefix() {
        return ColorUtils.toColor(ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getString("Prefix"));
    }
    
    public static ItemStack getRetroactiveCardRequiredItem(Player player) {
        String itemName = ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getString("Retroactive-Card.Required-Item.CustomItem");
        if (ConfigurationUtil.getConfig(ConfigurationType.CUSTOM_ITEMS).get("Manual-Settings." + itemName + ".Item") != null) {
            ItemStack is;
            try {
                is = new ItemStack(Material.valueOf(ConfigurationUtil.getConfig(ConfigurationType.CUSTOM_ITEMS).getString("Manual-Settings." + itemName + ".Item").toUpperCase()), 1);
            } catch (IllegalArgumentException ex2) {
                return null;
            }
            if (ConfigurationUtil.getConfig(ConfigurationType.CUSTOM_ITEMS).get("Manual-Settings." + itemName + ".Head-Owner") != null) {
                Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
                placeholders.put("{player}", player.getName());
                PluginControl.setHead(is, MessageUtil.replacePlaceholders(player, ConfigurationUtil.getConfig(ConfigurationType.CUSTOM_ITEMS).getString("Manual-Settings." + itemName + ".Head-Owner"), placeholders));
            }
            ItemMeta im = is.getItemMeta();
            if (ConfigurationUtil.getConfig(ConfigurationType.CUSTOM_ITEMS).get("Manual-Settings." + itemName + ".Lore") != null) {
                List<String> lore = new ArrayList<>();
                ConfigurationUtil.getConfig(ConfigurationType.CUSTOM_ITEMS).getStringList("Manual-Settings." + itemName + ".Lore").stream().forEach(lores -> lore.add(MessageUtil.toPlaceholderAPIResult(player, ColorUtils.toColor(lores))));
                im.setLore(lore);
            }
            if (ConfigurationUtil.getConfig(ConfigurationType.CUSTOM_ITEMS).get("Manual-Settings." + itemName + ".Enchantment") != null) {
                for (String name : ConfigurationUtil.getConfig(ConfigurationType.CUSTOM_ITEMS).getStringList("Manual-Settings." + itemName + ".Enchantment")) {
                    String[] data = name.split(":");
                    for (Enchantment enchant : Enchantment.values()) {
                        if (enchant.getKey().getKey().equalsIgnoreCase(data[0]) || enchant.getKey().toString().equalsIgnoreCase(data[0])) {
                            try {
                                im.addEnchant(enchant, Integer.valueOf(data[1]), true);
                            } catch (NumberFormatException ex) {}
                        }
                    }
                }
            }
            if (ConfigurationUtil.getConfig(ConfigurationType.CUSTOM_ITEMS).get("Manual-Settings." + itemName + ".Hide-Enchants") != null) PluginControl.hideEnchants(im);
            if (ConfigurationUtil.getConfig(ConfigurationType.CUSTOM_ITEMS).get("Manual-Settings." + itemName + ".Display-Name") != null) im.setDisplayName(ColorUtils.toColor(MessageUtil.toPlaceholderAPIResult(player, ConfigurationUtil.getConfig(ConfigurationType.CUSTOM_ITEMS).getString("Manual-Settings." + itemName + ".Display-Name"))));
            is.setItemMeta(im);
            return is;
        } else if (ConfigurationUtil.getConfig(ConfigurationType.CUSTOM_ITEMS).get("Item-Collection." + itemName) != null) {
            ItemStack is = ConfigurationUtil.getConfig(ConfigurationType.CUSTOM_ITEMS).getItemStack("Item-Collection." + itemName);
            if (is != null) {
                return is;    
            }
        }
        return null;
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
