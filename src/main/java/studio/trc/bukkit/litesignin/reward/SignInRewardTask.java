package studio.trc.bukkit.litesignin.reward;

/**
 * Reward dispatch task phases, executed in the order configured by
 * {@code Reward-Task-Sequence} in Config.yml.
 *
 * <p>{@code ITEMS_REWARD} has been removed: item rewards are now handled by
 * the server's unified item manager. Only non-item phases remain.</p>
 */
public enum SignInRewardTask {

    COMMANDS_EXECUTION,

    BROADCAST_MESSAGES_SENDING,

    PLAYSOUNDS,

    MESSAGES_SENDING;
}
