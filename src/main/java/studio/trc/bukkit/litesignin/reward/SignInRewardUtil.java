package studio.trc.bukkit.litesignin.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import studio.trc.bukkit.litesignin.api.Storage;
import studio.trc.bukkit.litesignin.configuration.RobustConfiguration;
import studio.trc.bukkit.litesignin.configuration.ConfigurationType;
import studio.trc.bukkit.litesignin.configuration.ConfigurationUtil;
import studio.trc.bukkit.litesignin.message.MessageUtil;
import studio.trc.bukkit.litesignin.Main;
import studio.trc.bukkit.litesignin.reward.command.SignInRewardCommand;
import studio.trc.bukkit.litesignin.reward.command.SignInRewardCommandType;
import studio.trc.bukkit.litesignin.reward.util.SignInSound;
import studio.trc.bukkit.litesignin.util.PluginControl;

/**
 * Shared reward dispatch logic for non-item reward phases.
 *
 * <p>The {@code ITEMS_REWARD} phase and all item-template helpers
 * ({@code getRewardItems}, {@code getItemFromItemData}, {@code setEnchantments})
 * have been removed: item rewards are now handled by the server's unified
 * item manager. Only message, broadcast, command and sound phases remain.</p>
 */
public abstract class SignInRewardUtil
    implements SignInReward
{
    @Override
    public void giveReward(Storage playerData) {
        if (playerData.getPlayer() != null) {
            Player player = playerData.getPlayer();
            for (String taskName : ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getStringList("Reward-Task-Sequence")) {
                try {
                    switch (SignInRewardTask.valueOf(taskName.toUpperCase())) {
                        case COMMANDS_EXECUTION: {
                            getCommands().stream().forEach(commands -> commands.runWithThePlayer(player));
                            break;
                        }
                        case MESSAGES_SENDING: {
                            getMessages().stream().forEach(messages -> {
                                Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
                                placeholders.put("{continuous}", String.valueOf(playerData.getContinuousSignIn()));
                                placeholders.put("{total-number}", String.valueOf(playerData.getCumulativeNumber()));
                                placeholders.put("{player}", player.getName());
                                MessageUtil.sendMessage(player, messages, placeholders);
                            });
                            break;
                        }
                        case BROADCAST_MESSAGES_SENDING: {
                            getBroadcastMessages().stream().forEach(messages -> {
                                Bukkit.getOnlinePlayers().stream().forEach(players -> {
                                    Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
                                    placeholders.put("{continuous}", String.valueOf(playerData.getContinuousSignIn()));
                                    placeholders.put("{total-number}", String.valueOf(playerData.getCumulativeNumber()));
                                    placeholders.put("{player}", player.getName());
                                    MessageUtil.sendMessage(players, messages, placeholders);
                                });
                            });
                            break;
                        }
                        case PLAYSOUNDS: {
                            getSounds().stream().forEach(sounds -> sounds.playSound(player));
                            break;
                        }
                    }
                } catch (Exception ex) {
                    Main plugin = Main.getInstance();
                    String playerName = player.getName();
                    String message = "Failed to execute sign-in reward task '" + taskName
                            + "' for player " + playerName + " (" + player.getUniqueId() + ")";
                    if (plugin != null) {
                        plugin.getLogger().log(Level.SEVERE, message, ex);
                    } else {
                        java.util.logging.Logger.getLogger(SignInRewardUtil.class.getName())
                                .log(Level.SEVERE, message, ex);
                    }
                }
            }
        }
    }

    public List<SignInRewardCommand> getCommands(String configPath) {
        List<SignInRewardCommand> list = new ArrayList<>();
        if (ConfigurationUtil.getConfig(ConfigurationType.REWARD_SETTINGS).contains(configPath)) {
            ConfigurationUtil.getConfig(ConfigurationType.REWARD_SETTINGS).getStringList(configPath).stream().forEach(commands -> {
                if (commands.toLowerCase().startsWith("server:")) {
                    list.add(new SignInRewardCommand(SignInRewardCommandType.SERVER, commands.substring(7)));
                } else if (commands.toLowerCase().startsWith("op:")) {
                    list.add(new SignInRewardCommand(SignInRewardCommandType.OP, commands.substring(3)));
                } else {
                    list.add(new SignInRewardCommand(SignInRewardCommandType.PLAYER, commands));
                }
            });
        }
        return list;
    }

    public List<SignInSound> getSounds(String configPath) {
        List<SignInSound> sounds = new ArrayList<>();
        RobustConfiguration config = ConfigurationUtil.getConfig(ConfigurationType.REWARD_SETTINGS);
        if (config.contains(configPath)) {
            config.getStringList(configPath).stream().forEach((value) -> {
                String[] args = value.split("-");
                try {
                    Sound sound = Sound.valueOf(args[0].toUpperCase());
                    float volume = Float.valueOf(args[1]);
                    float pitch = Float.valueOf(args[2]);
                    boolean broadcast = Boolean.valueOf(args[3]);
                    sounds.add(new SignInSound(sound, volume, pitch, broadcast));
                } catch (IllegalArgumentException ex) {
                    Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
                    placeholders.put("{sound}", args[0]);
                    placeholders.put("{path}", configPath + "." + value);
                    MessageUtil.sendConsoleMessage("Console-Messages.Invalid-Sound", ConfigurationType.MESSAGES, placeholders);
                } catch (Exception ex) {
                    Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
                    placeholders.put("{path}", configPath + "." + value);
                    MessageUtil.sendConsoleMessage("Console-Messages.Invalid-Sound-Setting", ConfigurationType.MESSAGES, placeholders);
                }
            });
        }
        return sounds;
    }
}
