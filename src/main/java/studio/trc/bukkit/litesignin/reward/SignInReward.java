package studio.trc.bukkit.litesignin.reward;

import java.util.List;

import studio.trc.bukkit.litesignin.api.Storage;
import studio.trc.bukkit.litesignin.reward.command.SignInRewardCommand;
import studio.trc.bukkit.litesignin.reward.util.SignInSound;
import studio.trc.bukkit.litesignin.reward.util.SignInGroup;

public interface SignInReward
{
    /**
     * Give reward.
     * @param playerData
     */
    public void giveReward(Storage playerData);

    /**
     * Get SignInReward permission group.
     * @return
     */
    public SignInGroup getGroup();

    /**
     * Get SignInReward module
     * It is used to indicate the reward form of SignInReward.
     * @return
     */
    public SignInRewardModule getModule();

    /**
     * @return
     */
    public List<String> getMessages();

    /**
     * @return
     */
    public List<String> getBroadcastMessages();

    /**
     * @return
     */
    public List<SignInRewardCommand> getCommands();

    /**
     * @return
     */
    public List<SignInSound> getSounds();
}
