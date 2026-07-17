package studio.trc.bukkit.litesignin.event;

import java.util.Date;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import studio.trc.bukkit.litesignin.Main;
import studio.trc.bukkit.litesignin.api.SignInResult;
import studio.trc.bukkit.litesignin.api.Storage;
import studio.trc.bukkit.litesignin.configuration.ConfigurationType;
import studio.trc.bukkit.litesignin.configuration.ConfigurationUtil;
import studio.trc.bukkit.litesignin.event.custom.SignInGUIOpenEvent;
import studio.trc.bukkit.litesignin.gui.SignInGUI;
import studio.trc.bukkit.litesignin.gui.SignInGUIColumn;
import studio.trc.bukkit.litesignin.gui.SignInInventory;
import studio.trc.bukkit.litesignin.gui.SignInMenuService;
import studio.trc.bukkit.litesignin.gui.SignInMenuSession;
import studio.trc.bukkit.litesignin.message.MessageUtil;
import studio.trc.bukkit.litesignin.service.SignInService;
import studio.trc.bukkit.litesignin.util.BukkitSchedulerManager;
import studio.trc.bukkit.litesignin.util.LiteSignInUtils;
import studio.trc.bukkit.litesignin.util.OnlineTimeRecord;
import studio.trc.bukkit.litesignin.util.PluginControl;
import studio.trc.bukkit.litesignin.util.SignInDate;

public class Menu
{

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
            SignInMenuService.open(player, inventory);
        }
    }

    public static void closeGUI(Player player) {
        if (player == null) {
            return;
        }
        BukkitSchedulerManager.runBukkitTask(() -> SignInMenuService.close(player), 0);
    }

    /**
     * Handles a click inside the sign-in menu.
     *
     * @param player the clicking player
     * @param slot the top-container slot that was clicked
     * @param clickType the Bukkit click type; only plain and shift-modified
     *                  left/right clicks are accepted (see {@link #isAllowedClick})
     * @return {@code true} if the click triggered a page switch or close, so
     *         the caller skips resync
     */
    public static boolean handleWindowClick(Player player, int slot, ClickType clickType) {
        if (player == null || slot < 0 || slot >= SignInMenuSession.MENU_SIZE || !isAllowedClick(clickType)) {
            return false;
        }
        SignInInventory inv = SignInMenuService.getOpeningInventory(player.getUniqueId());
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

    /**
     * Accepts plain and shift-modified left/right clicks. The sign-in menu is
     * a read-only button panel, so number-key swaps, double-click collection,
     * drop and {@code MIDDLE} (creative clone) are rejected outright — they
     * have no meaningful sign-in semantics and would only serve to bypass a
     * cancelled click in obscure client-mod scenarios.
     */
    private static boolean isAllowedClick(ClickType clickType) {
        return clickType == ClickType.LEFT
                || clickType == ClickType.RIGHT
                || clickType == ClickType.SHIFT_LEFT
                || clickType == ClickType.SHIFT_RIGHT;
    }

    private static boolean handleKeyButton(Player player, Storage data, SignInGUIColumn columns, YamlConfiguration guiConfig,
                                           int nextPageMonth, int nextPageYear, int previousPageMonth, int previousPageYear) {
        SignInGUIColumn.KeyType keyType = columns.getKeyType();
        SignInDate date = columns.getDate();
        if (keyType == null || date == null) {
            return false;
        }

        String keyTypePath = "SignIn-GUI-Settings.Key." + keyType.getSectionName();
        boolean closeAfterClick = guiConfig.getBoolean(keyTypePath + ".Close-GUI");
        boolean sessionChanged = false;
        boolean signedIn = false;
        SignInDate today = SignInDate.getInstance(new Date());

        if (keyType == SignInGUIColumn.KeyType.NOTHING_SIGNIN
                && date.equals(today) && !data.alreadySignIn()) {
            long requirement = OnlineTimeRecord.getSignInRequirement(player);
            if (requirement == -1) {
                SignInResult result = data.trySignIn();
                if (result.isSuccess()) {
                    signedIn = true;
                    Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
                    placeholders.put("{nextPageMonth}", String.valueOf(nextPageMonth));
                    placeholders.put("{nextPageYear}", String.valueOf(nextPageYear));
                    placeholders.put("{previousPageMonth}", String.valueOf(previousPageMonth));
                    placeholders.put("{previousPageYear}", String.valueOf(previousPageYear));
                    placeholders.put("{continuous}", String.valueOf(data.getContinuousSignIn()));
                    MessageUtil.sendMessage(player, ConfigurationUtil.getConfig(ConfigurationType.MESSAGES),
                            "GUI-SignIn-Messages.SignIn-Messages", placeholders);
                    if (!closeAfterClick) {
                        openGUI(player);
                        sessionChanged = true;
                    }
                } else {
                    sendSignInFailure(player, result);
                }
            } else {
                Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
                placeholders.put("{minute}", String.valueOf(requirement / 60000 + 1));
                MessageUtil.sendMessage(player, ConfigurationUtil.getConfig(ConfigurationType.MESSAGES), "Insufficient-Online-Time", placeholders);
            }
        } else if (keyType == SignInGUIColumn.KeyType.MISSED_SIGNIN) {
            if (!PluginControl.enableRetroactiveCard()) {
                MessageUtil.sendMessage(player, ConfigurationUtil.getConfig(ConfigurationType.MESSAGES), "Unable-To-Re-SignIn");
            } else if (!LiteSignInUtils.hasPermission(player, "Retroactive-Card.Hold") && data.getRetroactiveCard() > 0) {
                data.takeRetroactiveCard(data.getRetroactiveCard());
                MessageUtil.sendMessage(player, ConfigurationUtil.getConfig(ConfigurationType.MESSAGES), "GUI-SignIn-Messages.Unable-To-Hold");
            } else if (!LiteSignInUtils.hasPermission(player, "Retroactive-Card.Use")) {
                MessageUtil.sendMessage(player, ConfigurationUtil.getConfig(ConfigurationType.MESSAGES), "GUI-SignIn-Messages.No-Permission");
            } else if (today.compareTo(date) >= 0 && !data.alreadySignIn(date)) {
                SignInDate minimumDate = PluginControl.getRetroactiveCardMinimumDate();
                if (minimumDate != null && date.compareTo(minimumDate) < 0) {
                    Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
                    placeholders.put("{date}", minimumDate.getName(guiConfig.getString("SignIn-GUI-Settings.Date-Format")));
                    MessageUtil.sendMessage(player, ConfigurationUtil.getConfig(ConfigurationType.MESSAGES), "GUI-SignIn-Messages.Minimum-Date", placeholders);
                } else if (data.isRetroactiveCardCooldown()) {
                    Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
                    placeholders.put("{second}", String.valueOf(data.getRetroactiveCardCooldown()));
                    MessageUtil.sendMessage(player, ConfigurationUtil.getConfig(ConfigurationType.MESSAGES), "GUI-SignIn-Messages.Retroactive-Card-Cooldown", placeholders);
                } else if (data.getRetroactiveCard() >= PluginControl.getRetroactiveCardQuantityRequired()) {
                    SignInResult result = SignInService.retroactiveSignIn(data, date, true);
                    if (result.isSuccess()) {
                        signedIn = true;
                        Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
                        placeholders.put("{date}", date.getName(guiConfig.getString("SignIn-GUI-Settings.Date-Format")));
                        placeholders.put("{continuous}", String.valueOf(data.getContinuousSignIn()));
                        MessageUtil.sendMessage(player, ConfigurationUtil.getConfig(ConfigurationType.MESSAGES),
                                "GUI-SignIn-Messages.Retroactive-SignIn-Messages", placeholders);
                        if (!closeAfterClick) {
                            openGUI(player, date.getMonth(), date.getYear());
                            sessionChanged = true;
                        }
                    } else {
                        sendSignInFailure(player, result);
                    }
                } else {
                    Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
                    placeholders.put("{cards}", String.valueOf(PluginControl.getRetroactiveCardQuantityRequired() - data.getRetroactiveCard()));
                    MessageUtil.sendMessage(player, ConfigurationUtil.getConfig(ConfigurationType.MESSAGES), "GUI-SignIn-Messages.Need-More-Retroactive-Cards", placeholders);
                }
            }
        }

        if (closeAfterClick && (signedIn || keyType == SignInGUIColumn.KeyType.ALREADY_SIGNIN)) {
            closeTrackedGUI(player);
            sessionChanged = true;
        }
        if (shouldRunKeyActions(keyType, signedIn)) {
            runConfiguredKeyActions(player, date, keyTypePath, guiConfig,
                    nextPageMonth, nextPageYear, previousPageMonth, previousPageYear);
        }
        return sessionChanged;
    }

    private static void sendSignInFailure(Player player, SignInResult result) {
        Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
        placeholders.put("{result}", result.name());
        MessageUtil.sendMessage(player, ConfigurationUtil.getConfig(ConfigurationType.MESSAGES),
                "GUI-SignIn-Messages.Operation-Failed", placeholders);
    }

    /** Reward actions require a successful sign-in; already-signed buttons may run ordinary query/navigation actions. */
    static boolean shouldRunKeyActions(SignInGUIColumn.KeyType keyType, boolean signedIn) {
        return signedIn || keyType == SignInGUIColumn.KeyType.ALREADY_SIGNIN;
    }

    private static void runConfiguredKeyActions(Player player, SignInDate date, String keyTypePath,
                                                YamlConfiguration guiConfig, int nextPageMonth, int nextPageYear,
                                                int previousPageMonth, int previousPageYear) {
        Map<String, String> placeholders = getClickPlaceholders(player, date.getDataText(false),
                nextPageMonth, nextPageYear, previousPageMonth, previousPageYear);
        if (guiConfig.contains(keyTypePath + ".Commands")) {
            guiConfig.getStringList(keyTypePath + ".Commands")
                    .forEach(command -> runCommand(player, command, placeholders));
        }
        if (guiConfig.contains(keyTypePath + ".Messages")) {
            guiConfig.getStringList(keyTypePath + ".Messages")
                    .forEach(message -> MessageUtil.sendMessage(player, message, placeholders));
        }
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
        SignInMenuService.close(player);
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
