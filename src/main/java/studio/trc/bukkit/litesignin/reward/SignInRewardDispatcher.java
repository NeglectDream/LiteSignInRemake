package studio.trc.bukkit.litesignin.reward;

import java.util.Date;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import studio.trc.bukkit.litesignin.api.Storage;
import studio.trc.bukkit.litesignin.configuration.ConfigurationType;
import studio.trc.bukkit.litesignin.configuration.ConfigurationUtil;
import studio.trc.bukkit.litesignin.event.custom.SignInRewardEvent;
import studio.trc.bukkit.litesignin.reward.type.SignInNormalReward;
import studio.trc.bukkit.litesignin.reward.type.SignInRetroactiveTimeReward;
import studio.trc.bukkit.litesignin.reward.type.SignInSpecialDateReward;
import studio.trc.bukkit.litesignin.reward.type.SignInSpecialTimeCycleReward;
import studio.trc.bukkit.litesignin.reward.type.SignInSpecialTimeOfMonthReward;
import studio.trc.bukkit.litesignin.reward.type.SignInSpecialTimePeriodReward;
import studio.trc.bukkit.litesignin.reward.type.SignInSpecialTimeReward;
import studio.trc.bukkit.litesignin.reward.type.SignInSpecialWeekReward;
import studio.trc.bukkit.litesignin.reward.type.SignInStatisticsTimeCycleReward;
import studio.trc.bukkit.litesignin.reward.type.SignInStatisticsTimeOfMonthReward;
import studio.trc.bukkit.litesignin.reward.type.SignInStatisticsTimeReward;
import studio.trc.bukkit.litesignin.reward.util.SignInGroup;
import studio.trc.bukkit.litesignin.util.SignInDate;

/**
 * Builds and dispatches rewards after a sign-in transaction has committed.
 *
 * <p>This class owns Bukkit reward events and reward ordering. It depends on
 * the {@link Storage} contract so persistence implementations do not need to
 * know how reward schedules are assembled.</p>
 */
public final class SignInRewardDispatcher {
    private final Storage storage;

    public SignInRewardDispatcher(Storage storage) {
        this.storage = storage;
    }

    /** Dispatches ordinary or retroactive rewards for the bound player. */
    public void dispatch(SignInDate retroactiveDate) {
        Player player = storage.getPlayer();
        if (player == null) {
            return;
        }
        if (ConfigurationUtil.getConfig(ConfigurationType.CONFIG)
                .getBoolean("Enable-Multi-Group-Reward")) {
            dispatchForAllGroups(player, retroactiveDate);
            return;
        }
        dispatchForGroup(player, storage.getGroup(), retroactiveDate);
    }

    private void dispatchForAllGroups(Player player, SignInDate retroactiveDate) {
        for (SignInGroup group : storage.getAllGroup()) {
            dispatchForGroup(player, group, retroactiveDate);
        }
    }

    private void dispatchForGroup(Player player, SignInGroup group, SignInDate retroactiveDate) {
        if (group == null) {
            return;
        }
        int continuousSignIn = storage.getContinuousSignIn();
        int totalNumber = storage.getCumulativeNumber();
        SignInRewardSchedule rewardQueue = new SignInRewardSchedule(storage);
        rewardQueue.addReward(new SignInStatisticsTimeReward(group, totalNumber));
        rewardQueue.addReward(new SignInStatisticsTimeCycleReward(group, totalNumber));
        if (retroactiveDate != null) {
            addRetroactiveRewards(rewardQueue, group, retroactiveDate);
        } else {
            addOrdinaryRewards(rewardQueue, group, continuousSignIn);
        }

        SignInRewardEvent event = new SignInRewardEvent(player, rewardQueue);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            rewardQueue.run(retroactiveDate != null);
        }
    }

    private void addRetroactiveRewards(SignInRewardSchedule rewardQueue,
                                       SignInGroup group, SignInDate date) {
        int week = date.getWeek();
        int month = date.getMonth();
        rewardQueue.addReward(new SignInSpecialWeekReward(group, week));
        rewardQueue.addReward(new SignInStatisticsTimeOfMonthReward(
                group, month, storage.getCumulativeNumberOfMonth(date.getYear(), month)));
        rewardQueue.addReward(new SignInSpecialDateReward(group, date));
        rewardQueue.addReward(new SignInRetroactiveTimeReward(group));
    }

    private void addOrdinaryRewards(SignInRewardSchedule rewardQueue,
                                    SignInGroup group, int continuousSignIn) {
        SignInDate today = SignInDate.getInstance(new Date());
        int week = today.getWeek();
        int month = today.getMonth();
        rewardQueue.addReward(new SignInSpecialWeekReward(group, week));
        rewardQueue.addReward(new SignInStatisticsTimeOfMonthReward(
                group, month, storage.getCumulativeNumberOfMonth(today.getYear(), month)));
        rewardQueue.addReward(new SignInSpecialDateReward(group, today));
        rewardQueue.addReward(new SignInSpecialTimeReward(group, continuousSignIn));
        rewardQueue.addReward(new SignInSpecialTimeCycleReward(group, continuousSignIn));
        rewardQueue.addReward(new SignInSpecialTimeOfMonthReward(
                group, month, storage.getContinuousSignInOfMonth()));
        rewardQueue.addReward(new SignInSpecialTimePeriodReward(group, today));
        rewardQueue.addReward(new SignInNormalReward(group));
    }
}
