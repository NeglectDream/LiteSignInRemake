package studio.trc.bukkit.litesignin.event;

import java.util.Date;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow.WindowClickType;

import studio.trc.bukkit.litesignin.Main;
import studio.trc.bukkit.litesignin.api.Storage;
import studio.trc.bukkit.litesignin.configuration.ConfigurationType;
import studio.trc.bukkit.litesignin.configuration.ConfigurationUtil;
import studio.trc.bukkit.litesignin.event.custom.SignInGUIOpenEvent;
import studio.trc.bukkit.litesignin.gui.SignInGUI;
import studio.trc.bukkit.litesignin.gui.SignInGUIColumn;
import studio.trc.bukkit.litesignin.gui.SignInInventory;
import studio.trc.bukkit.litesignin.message.MessageUtil;
import studio.trc.bukkit.litesignin.packet.PacketSignInMenuService;
import studio.trc.bukkit.litesignin.queue.SignInQueue;
import studio.trc.bukkit.litesignin.util.BukkitSchedulerManager;
import studio.trc.bukkit.litesignin.util.LiteSignInUtils;
import studio.trc.bukkit.litesignin.util.OnlineTimeRecord;
import studio.trc.bukkit.litesignin.util.PluginControl;
import studio.trc.bukkit.litesignin.util.SignInDate;

public class Menu
{
    private static final int SIGN_IN_MENU_SIZE = 54;

    public static void openGUI(Player player) {
        if (player == null) {
            return;
        }
        BukkitSchedulerManager.runBukkitTask(() -> {
            if (!player.isOnline()) {
                return;
            }
            SignInInventory inventory = SignInGUI.getGUI(player);
            SignInGUIOpenEvent event = new SignInGUIOpenEvent(player, inventory);
            fireOpenEvent(event, player, inventory);
        }, 0);
    }

    public static void openGUI(Player player, int month) {
        if (player == null) {
            return;
        }
        BukkitSchedulerManager.runBukkitTask(() -> {
            if (!player.isOnline()) {
                return;
            }
            SignInInventory inventory = SignInGUI.getGUI(player, month);
            SignInGUIOpenEvent event = new SignInGUIOpenEvent(player, inventory, month);
            fireOpenEvent(event, player, inventory);
        }, 0);
    }

    public static void openGUI(Player player, int month, int year) {
        if (player == null) {
            return;
        }
        BukkitSchedulerManager.runBukkitTask(() -> {
            if (!player.isOnline()) {
                return;
            }
            SignInInventory inventory = SignInGUI.getGUI(player, month, year);
            SignInGUIOpenEvent event = new SignInGUIOpenEvent(player, inventory, month, year);
            fireOpenEvent(event, player, inventory);
        }, 0);
    }

    public static void callEvent(SignInGUIOpenEvent event, Player player, SignInInventory inventory) {
        BukkitSchedulerManager.runBukkitTask(() -> fireOpenEvent(event, player, inventory), 0);
    }

    private static void fireOpenEvent(SignInGUIOpenEvent event, Player player, SignInInventory inventory) {
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            PacketSignInMenuService.open(player, inventory);
        }
    }

    public static void closeGUI(Player player) {
        if (player == null) {
            return;
        }
        BukkitSchedulerManager.runBukkitTask(() -> PacketSignInMenuService.close(player), 0);
    }

    public static boolean handleWindowClick(Player player, int slot, int button, WindowClickType clickType) {
        if (player == null || slot < 0 || slot >= SIGN_IN_MENU_SIZE || !isAllowedClick(clickType, button)) {
            return false;
        }
        SignInInventory inv = PacketSignInMenuService.getOpeningInventory(player.getUniqueId());
        if (inv == null) {
            return false;
        }
        YamlConfiguration guiConfig = ConfigurationUtil.getConfig(ConfigurationType.GUI_SETTINGS).getConfig();
        Storage data = Storage.getPlayer(player);
        int nextPageMonth = inv.getNextPageMonth();
        int nextPageYear = inv.getNextPageYear();
        int previousPageMonth = inv.getPreviousPageMonth();
        int previousPageYear = inv.getPreviousPageYear();
        for (SignInGUIColumn columns : inv.getButtons()) {
            if (columns.getKeyPostion() != slot) {
                continue;
            }
            if (columns.isKey()) {
                return handleKeyButton(player, data, columns, guiConfig, nextPageMonth, nextPageYear, previousPageMonth, previousPageYear);
            }
            return handleOtherButton(player, columns, guiConfig, nextPageMonth, nextPageYear, previousPageMonth, previousPageYear);
        }
        return false;
    }

    private static boolean isAllowedClick(WindowClickType clickType, int button) {
        return clickType == WindowClickType.PICKUP && (button == 0 || button == 1);
    }

    private static boolean handleKeyButton(Player player, Storage data, SignInGUIColumn columns, YamlConfiguration guiConfig,
                                           int nextPageMonth, int nextPageYear, int previousPageMonth, int previousPageYear) {
        boolean sessionChanged = false;
        boolean closeAfterClick = guiConfig.getBoolean("SignIn-GUI-Settings.Key." + columns.getKeyType().getSectionName() + ".Close-GUI");
        SignInDate today = SignInDate.getInstance(new Date());
        if (columns.getDate().equals(today) && !data.alreadySignIn()) {
            long requirement = OnlineTimeRecord.getSignInRequirement(player);
            if (requirement == -1) {
                data.signIn();
                Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
                placeholders.put("{nextPageMonth}", String.valueOf(nextPageMonth));
                placeholders.put("{nextPageYear}", String.valueOf(nextPageYear));
                placeholders.put("{previousPageMonth}", String.valueOf(previousPageMonth));
                placeholders.put("{previousPageYear}", String.valueOf(previousPageYear));
                placeholders.put("{continuous}", String.valueOf(data.getContinuousSignIn()));
                placeholders.put("{queue}", String.valueOf(SignInQueue.getInstance().getRank(data.getUserUUID())));
                MessageUtil.sendMessage(player, ConfigurationUtil.getConfig(ConfigurationType.MESSAGES), "GUI-SignIn-Messages.SignIn-Messages", placeholders);
                if (!closeAfterClick) {
                    openGUI(player);
                    sessionChanged = true;
                }
            } else {
                Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
                placeholders.put("{minute}", String.valueOf(requirement / 60000 + 1));
                MessageUtil.sendMessage(player, ConfigurationUtil.getConfig(ConfigurationType.MESSAGES), "Insufficient-Online-Time", placeholders);
            }
        } else if (PluginControl.enableRetroactiveCard()) {
            if (!LiteSignInUtils.hasPermission(player, "Retroactive-Card.Hold") && data.getRetroactiveCard() > 0) {
                data.takeRetroactiveCard(data.getRetroactiveCard());
                MessageUtil.sendMessage(player, ConfigurationUtil.getConfig(ConfigurationType.MESSAGES), "GUI-SignIn-Messages.Unable-To-Hold");
            } else if (!LiteSignInUtils.hasPermission(player, "Retroactive-Card.Use")) {
                MessageUtil.sendMessage(player, ConfigurationUtil.getConfig(ConfigurationType.MESSAGES), "GUI-SignIn-Messages.No-Permission");
            } else if (today.compareTo(columns.getDate()) >= 0 && !data.alreadySignIn(columns.getDate())) {
                if (PluginControl.getRetroactiveCardMinimumDate() != null && columns.getDate().compareTo(PluginControl.getRetroactiveCardMinimumDate()) < 0) {
                    Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
                    placeholders.put("{date}", PluginControl.getRetroactiveCardMinimumDate().getName(guiConfig.getString("SignIn-GUI-Settings.Date-Format")));
                    MessageUtil.sendMessage(player, ConfigurationUtil.getConfig(ConfigurationType.MESSAGES), "GUI-SignIn-Messages.Minimum-Date", placeholders);
                } else if (data.isRetroactiveCardCooldown()) {
                    Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
                    placeholders.put("{second}", String.valueOf(data.getRetroactiveCardCooldown()));
                    MessageUtil.sendMessage(player, ConfigurationUtil.getConfig(ConfigurationType.MESSAGES), "GUI-SignIn-Messages.Retroactive-Card-Cooldown", placeholders);
                } else if (data.getRetroactiveCard() >= PluginControl.getRetroactiveCardQuantityRequired()) {
                    data.takeRetroactiveCard(PluginControl.getRetroactiveCardQuantityRequired());
                    data.signIn(columns.getDate());
                    Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
                    placeholders.put("{date}", columns.getDate().getName(guiConfig.getString("SignIn-GUI-Settings.Date-Format")));
                    placeholders.put("{continuous}", String.valueOf(data.getContinuousSignIn()));
                    placeholders.put("{queue}", String.valueOf(SignInQueue.getInstance().getRank(data.getUserUUID())));
                    MessageUtil.sendMessage(player, ConfigurationUtil.getConfig(ConfigurationType.MESSAGES), "GUI-SignIn-Messages.Retroactive-SignIn-Messages", placeholders);
                    if (!closeAfterClick) {
                        openGUI(player, columns.getDate().getMonth(), columns.getDate().getYear());
                        sessionChanged = true;
                    }
                } else {
                    Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
                    placeholders.put("{cards}", String.valueOf(PluginControl.getRetroactiveCardQuantityRequired() - data.getRetroactiveCard()));
                    MessageUtil.sendMessage(player, ConfigurationUtil.getConfig(ConfigurationType.MESSAGES), "GUI-SignIn-Messages.Need-More-Retroactive-Cards", placeholders);
                }
            }
        } else {
            MessageUtil.sendMessage(player, ConfigurationUtil.getConfig(ConfigurationType.MESSAGES), "Unable-To-Re-SignIn");
        }
        if (closeAfterClick) {
            closeTrackedGUI(player);
            sessionChanged = true;
        }
        Map<String, String> placeholders = getClickPlaceholders(player, columns.getDate().getDataText(false), nextPageMonth, nextPageYear, previousPageMonth, previousPageYear);
        if (guiConfig.contains("SignIn-GUI-Settings.Key." + columns.getKeyType().getSectionName() + ".Commands")) {
            guiConfig.getStringList("SignIn-GUI-Settings.Key." + columns.getKeyType().getSectionName() + ".Commands").forEach(commands -> runCommand(player, commands, placeholders));
        }
        if (guiConfig.contains("SignIn-GUI-Settings.Key." + columns.getKeyType().getSectionName() + ".Messages")) {
            guiConfig.getStringList("SignIn-GUI-Settings.Key." + columns.getKeyType().getSectionName() + ".Messages").forEach(message -> MessageUtil.sendMessage(player, message, placeholders));
        }
        return sessionChanged;
    }

    private static boolean handleOtherButton(Player player, SignInGUIColumn columns, YamlConfiguration guiConfig,
                                             int nextPageMonth, int nextPageYear, int previousPageMonth, int previousPageYear) {
        if (!guiConfig.contains("SignIn-GUI-Settings.Others." + columns.getButtonName())) {
            return false;
        }
        boolean sessionChanged = false;
        if (guiConfig.getBoolean("SignIn-GUI-Settings.Others." + columns.getButtonName() + ".Close-GUI")) {
            closeTrackedGUI(player);
            sessionChanged = true;
        }
        Map<String, String> placeholders = getClickPlaceholders(player, null, nextPageMonth, nextPageYear, previousPageMonth, previousPageYear);
        if (guiConfig.contains("SignIn-GUI-Settings.Others." + columns.getButtonName() + ".Commands")) {
            guiConfig.getStringList("SignIn-GUI-Settings.Others." + columns.getButtonName() + ".Commands").forEach(commands -> runCommand(player, commands, placeholders));
        }
        if (guiConfig.contains("SignIn-GUI-Settings.Others." + columns.getButtonName() + ".Messages")) {
            guiConfig.getStringList("SignIn-GUI-Settings.Others." + columns.getButtonName() + ".Messages").forEach(message -> MessageUtil.sendMessage(player, message, placeholders));
        }
        return sessionChanged;
    }

    private static Map<String, String> getClickPlaceholders(Player player, String dateText, int nextPageMonth, int nextPageYear,
                                                            int previousPageMonth, int previousPageYear) {
        Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
        if (dateText != null) {
            placeholders.put("{dateText}", dateText);
        }
        placeholders.put("{nextPageMonth}", String.valueOf(nextPageMonth));
        placeholders.put("{nextPageYear}", String.valueOf(nextPageYear));
        placeholders.put("{previousPageMonth}", String.valueOf(previousPageMonth));
        placeholders.put("{previousPageYear}", String.valueOf(previousPageYear));
        placeholders.put("{player}", player.getName());
        return placeholders;
    }

    private static void closeTrackedGUI(Player player) {
        PacketSignInMenuService.close(player);
    }
    
    public static void runCommand(Player player, String commands, Map<String, String> placeholders) {
        if (commands.toLowerCase().startsWith("server:")) {
            Main.getInstance().getServer().dispatchCommand(Bukkit.getConsoleSender(), MessageUtil.replacePlaceholders(player, commands.substring(7), placeholders));
        } else if (commands.toLowerCase().startsWith("op:")) {
            String command = MessageUtil.replacePlaceholders(player, commands.substring(3), placeholders);
            if (player.isOp()) {
                player.performCommand(command);
            } else {
                player.setOp(true);
                try {
                    player.performCommand(command);
                } catch (Throwable error) {
                    error.printStackTrace();
                }
                player.setOp(false);
            }
        } else {
            player.performCommand(MessageUtil.replacePlaceholders(player, commands, placeholders));
        }
    }
}
