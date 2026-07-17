package studio.trc.bukkit.litesignin.api;

/**
 * Result of one sign-in attempt.
 *
 * <p>Callers must only send success feedback or run configured success actions
 * when {@link #isSuccess()} returns {@code true}.</p>
 */
public enum SignInResult {
    SUCCESS,
    ALREADY_SIGNED_IN,
    IN_PROGRESS,
    EVENT_CANCELLED,
    DISABLED_WORLD,
    INVALID_DATE,
    BEFORE_MINIMUM_DATE,
    COOLDOWN,
    INSUFFICIENT_CARDS,
    PLAYER_UNAVAILABLE,
    STORAGE_FAILURE;

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
