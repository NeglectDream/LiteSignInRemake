package studio.trc.bukkit.litesignin.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import studio.trc.bukkit.litesignin.Main;
import studio.trc.bukkit.litesignin.api.Storage;
import studio.trc.bukkit.litesignin.reward.type.SignInNormalReward;

/**
 * Sign in reward queue.
 *
 * <p>Reward dispatch is best-effort: the sign-in transaction has already
 * committed before rewards are dispatched, and external operations (giving
 * items, executing console commands) cannot be rolled back. This class
 * records which reward modules succeeded and which failed so administrators
 * have a single audit entry per dispatch rather than scattered log lines.</p>
 *
 * @author Dean
 */
public class SignInRewardSchedule
{
    private final List<SignInReward> queue = new ArrayList<>();
    private final Storage playerData;

    public SignInRewardSchedule(Storage playerData) {
        this.playerData = playerData;
    }

    public List<SignInReward> getRewards() {
        return queue;
    }

    public void addReward(SignInReward reward) {
        queue.add(reward);
    }

    public void clearQueue() {
        queue.clear();
    }

    public void run(boolean retroactive) {
        List<SignInReward> toDispatch = new ArrayList<>();
        if (retroactive) {
            SignInRewardRetroactive retroactiveTime = null;
            for (SignInReward reward : queue) {
                if (reward instanceof SignInRewardRetroactive) {
                    retroactiveTime = (SignInRewardRetroactive) reward;
                    toDispatch.add(reward);
                    break;
                }
            }
            for (SignInReward reward : queue) {
                if (!retroactiveTime.isDisable(reward.getModule()) && !reward.getModule().equals(SignInRewardModule.RETROACTIVE_TIME)) {
                    toDispatch.add(reward);
                }
            }
        } else {
            boolean overrideDefaultReward = false;
            for (SignInReward reward : queue) {
                if (reward instanceof SignInRewardColumn) {
                    if (((SignInRewardColumn) reward).overrideDefaultRewards()) {
                        overrideDefaultReward = true;
                        break;
                    }
                }
            }
            for (SignInReward reward : queue) {
                if (reward instanceof SignInNormalReward) {
                    if (!overrideDefaultReward) {
                        toDispatch.add(reward);
                    }
                } else {
                    toDispatch.add(reward);
                }
            }
        }

        dispatchWithAudit(toDispatch);
    }

    /**
     * Dispatches each reward, catching per-reward failures so one broken
     * module does not prevent the rest, then logs a single audit summary.
     */
    private void dispatchWithAudit(List<SignInReward> rewards) {
        int succeeded = 0;
        List<String> failedModules = new ArrayList<>();
        for (SignInReward reward : rewards) {
            try {
                reward.giveReward(playerData);
                succeeded++;
            } catch (RuntimeException error) {
                failedModules.add(reward.getModule().name());
                logRewardFailure(reward, error);
            }
        }
        if (!failedModules.isEmpty()) {
            logAuditSummary(succeeded, failedModules);
        }
    }

    private void logRewardFailure(SignInReward reward, RuntimeException error) {
        Main plugin = Main.getInstance();
        String playerName = playerData.getName();
        String message = "Reward module '" + reward.getModule() + "' failed for player "
                + playerName + " (" + playerData.getUserUUID() + ")";
        if (plugin != null) {
            plugin.getLogger().log(Level.SEVERE, message, error);
        } else {
            java.util.logging.Logger.getLogger(SignInRewardSchedule.class.getName())
                    .log(Level.SEVERE, message, error);
        }
    }

    private void logAuditSummary(int succeeded, List<String> failedModules) {
        Main plugin = Main.getInstance();
        String playerName = playerData.getName();
        String message = "Reward dispatch completed for " + playerName + " (" + playerData.getUserUUID()
                + "): " + succeeded + " succeeded, " + failedModules.size()
                + " failed modules: " + String.join(", ", failedModules);
        if (plugin != null) {
            plugin.getLogger().warning(message);
        } else {
            java.util.logging.Logger.getLogger(SignInRewardSchedule.class.getName())
                    .warning(message);
        }
    }
}
