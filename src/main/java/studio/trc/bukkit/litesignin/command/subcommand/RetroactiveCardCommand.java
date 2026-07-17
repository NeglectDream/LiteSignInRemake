package studio.trc.bukkit.litesignin.command.subcommand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import studio.trc.bukkit.litesignin.api.Storage;
import studio.trc.bukkit.litesignin.command.SignInSubCommand;
import studio.trc.bukkit.litesignin.command.SignInSubCommandType;
import studio.trc.bukkit.litesignin.message.MessageUtil;
import studio.trc.bukkit.litesignin.util.PluginControl;
import studio.trc.bukkit.litesignin.util.LiteSignInUtils;

public class RetroactiveCardCommand
    implements SignInSubCommand
{
    @Override
    public void execute(CommandSender sender, String subCommand, String... args) {
        if (!PluginControl.enableRetroactiveCard()) {
            MessageUtil.sendCommandMessage(sender, "RetroactiveCard.Unavailable-Feature");
        }
        if (args.length < 3) {
            MessageUtil.sendCommandMessage(sender, "RetroactiveCard.Help");
            return;
        }
        String subCommandType = args[1];
        if (subCommandType.equalsIgnoreCase("help")) {
            MessageUtil.sendCommandMessage(sender, "RetroactiveCard.Help");
            return;
        }
        // Physical-card mode derives card counts from the player's inventory and
        // matches items by display name, so give/set/take cannot mutate the pool;
        // these subcommands are only available in virtual-counter mode.
        if (PluginControl.enableRetroactiveCardRequiredItem()) {
            MessageUtil.sendCommandMessage(sender, "RetroactiveCard.Physical-Mode-Unsupported");
            return;
        }
        Player player;
        if (args.length == 3) {
            if (LiteSignInUtils.isPlayer(sender, true)) {
                player = (Player) sender;
            } else {
                return;
            }
        } else {
            player = Bukkit.getPlayer(args[3]);
        }
        if (subCommandType.equalsIgnoreCase("give")) {
            command_give(sender, args, player);
        } else if (subCommandType.equalsIgnoreCase("set")) {
            command_set(sender, args, player);
        } else if (subCommandType.equalsIgnoreCase("take")) {
            command_take(sender, args, player);
        } else {
            MessageUtil.sendCommandMessage(sender, "RetroactiveCard.Help");
        }
    }

    @Override
    public String getName() {
        return "retroactivecard";
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String subCommand, String... args) {
        String subCommandType = args.length > 1 ? args[1] : "";
        if (args.length <= 2) {
            List<String> commands = Arrays.stream(SubCommandType.values())
                    .filter(type -> LiteSignInUtils.hasCommandPermission(sender, type.getCommandPermissionPath(), false))
                    .map(SubCommandType::getCommandName)
                    .filter(command -> command.toLowerCase().startsWith(subCommandType.toLowerCase()))
                    // Physical-card mode only exposes help; give/set/take are disabled.
                    .filter(command -> !PluginControl.enableRetroactiveCardRequiredItem()
                            || command.equalsIgnoreCase("help"))
                    .collect(Collectors.toList());
            return commands;
        } else {
            if (args.length == 4) {
                return tabGetPlayersName(args, 4);
            }
        }
        return new ArrayList<>();
    }

    @Override
    public SignInSubCommandType getCommandType() {
        return SignInSubCommandType.RETROACTIVE_CARD;
    }

    private void command_give(CommandSender sender, String[] args, Player player) {
        if (!LiteSignInUtils.hasCommandPermission(sender, SubCommandType.GIVE.commandPermissionPath, true)) {
            return;
        }
        Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
        try {
            int number = Integer.valueOf(args[2]);
            Storage data = player != null
                    ? Storage.getPlayer(player)
                    : offlineStorage(args, placeholders, sender);
            if (data == null) {
                return;
            }
            data.giveRetroactiveCard(number);
            placeholders.put("{player}", player != null ? player.getName() : args[3]);
            placeholders.put("{amount}", String.valueOf(number));
            MessageUtil.sendCommandMessage(sender, "RetroactiveCard.Give", placeholders);
        } catch (NumberFormatException ex) {
            placeholders.put("{number}", args[2]);
            MessageUtil.sendCommandMessage(sender, "RetroactiveCard.Invalid-Number", placeholders);
        }
    }

    private void command_set(CommandSender sender, String[] args, Player player) {
        if (!LiteSignInUtils.hasCommandPermission(sender, SubCommandType.SET.commandPermissionPath, true)) {
            return;
        }
        Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
        try {
            int number = Integer.valueOf(args[2]);
            Storage data = player != null
                    ? Storage.getPlayer(player)
                    : offlineStorage(args, placeholders, sender);
            if (data == null) {
                return;
            }
            data.setRetroactiveCard(number, true);
            placeholders.put("{player}", player != null ? player.getName() : args[3]);
            placeholders.put("{amount}", String.valueOf(number));
            MessageUtil.sendCommandMessage(sender, "RetroactiveCard.Set", placeholders);
        } catch (NumberFormatException ex) {
            placeholders.put("{number}", args[2]);
            MessageUtil.sendCommandMessage(sender, "RetroactiveCard.Invalid-Number", placeholders);
        }
    }

    private void command_take(CommandSender sender, String[] args, Player player) {
        if (!LiteSignInUtils.hasCommandPermission(sender, SubCommandType.TAKE.commandPermissionPath, true)) {
            return;
        }
        Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
        try {
            int number = Integer.valueOf(args[2]);
            Storage data = player != null
                    ? Storage.getPlayer(player)
                    : offlineStorage(args, placeholders, sender);
            if (data == null) {
                return;
            }
            data.takeRetroactiveCard(number);
            placeholders.put("{player}", player != null ? player.getName() : args[3]);
            placeholders.put("{amount}", String.valueOf(number));
            MessageUtil.sendCommandMessage(sender, "RetroactiveCard.Take", placeholders);
        } catch (NumberFormatException ex) {
            placeholders.put("{number}", args[2]);
            MessageUtil.sendCommandMessage(sender, "RetroactiveCard.Invalid-Number", placeholders);
        }
    }

    /**
     * Resolves storage for an offline target named by {@code args[3]}.
     *
     * <p>Only invoked in virtual-counter mode, where the physical-card restriction
     * has already been enforced by {@link #execute}.
     */
    private Storage offlineStorage(String[] args, Map<String, String> placeholders, CommandSender sender) {
        if (args[3].isEmpty()) {
            placeholders.put("{player}", args[3]);
            MessageUtil.sendCommandMessage(sender, "Click.Player-Not-Exist", placeholders);
            return null;
        }
        OfflinePlayer offlineplayer = Bukkit.getOfflinePlayer(args[3]);
        if (offlineplayer == null) {
            placeholders.put("{player}", args[3]);
            MessageUtil.sendCommandMessage(sender, "RetroactiveCard.Player-Not-Exist", placeholders);
            return null;
        }
        return Storage.getPlayer(offlineplayer.getUniqueId());
    }
    
    public enum SubCommandType {
        /**
         * /signin retroactivecard give
         */
        GIVE("give", "RetroactiveCard.Give"),
        
        /**
         * /signin retroactivecard set
         */
        SET("set", "RetroactiveCard.Set"),
        
        /**
         * /signin retroactivecard set
         */
        TAKE("take", "RetroactiveCard.Take");
        
        @Getter
        private final String commandName;
        @Getter
        private final String commandPermissionPath;
        
        private SubCommandType(String commandName, String commandPermissionPath) {
            this.commandName = commandName;
            this.commandPermissionPath = commandPermissionPath;
        }
    }
}
