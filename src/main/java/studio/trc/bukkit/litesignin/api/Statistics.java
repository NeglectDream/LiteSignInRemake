package studio.trc.bukkit.litesignin.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import studio.trc.bukkit.litesignin.util.SignInDate;
import studio.trc.bukkit.litesignin.util.PluginControl;

/**
 * Statistics on Users
 * @author Dean
 */
public interface Statistics
{
    Map<UUID, Long> lastSignInTime = new ConcurrentHashMap<>();

    static void recordRetroactiveSignIn(UUID uuid) {
        if (uuid != null) {
            lastSignInTime.put(uuid, System.currentTimeMillis());
        }
    }

    static void clearRetroactiveCooldown(UUID uuid) {
        if (uuid != null) {
            lastSignInTime.remove(uuid);
        }
    }

    static void clearRetroactiveCooldowns() {
        lastSignInTime.clear();
    }

    default boolean isRetroactiveCardCooldown() {
        Long lastSignIn = lastSignInTime.get(getUserUUID());
        if (lastSignIn == null) {
            return false;
        }
        boolean coolingDown = System.currentTimeMillis() - lastSignIn
                <= PluginControl.getRetroactiveCardIntervals() * 1000D;
        if (!coolingDown) {
            lastSignInTime.remove(getUserUUID(), lastSignIn);
        }
        return coolingDown;
    }

    default double getRetroactiveCardCooldown() {
        Long lastSignIn = lastSignInTime.get(getUserUUID());
        if (lastSignIn == null) {
            return 0D;
        }
        double remaining = PluginControl.getRetroactiveCardIntervals()
                - (System.currentTimeMillis() - lastSignIn) / 1000D;
        if (remaining <= 0D) {
            lastSignInTime.remove(getUserUUID(), lastSignIn);
            return 0D;
        }
        return Math.round(remaining * 10D) / 10D;
    }
    
    public UUID getUserUUID();
    
    /**
     * Check whether players sign in continuously.
     */
    public void checkContinuousSignIn();
    
    /**
     * Check whether users sign in on that day.
     * @return 
     */
    public boolean alreadySignIn();
    
    /**
     * Check whether the user is signed in on the day of user history.
     * @param date
     * @return 
     */
    public boolean alreadySignIn(SignInDate date);
    
    /**
     * Get the cumulative numbers of user sign in.
     * @return 
     */
    public int getCumulativeNumber();
    
    /**
     * Get the cumulative numbers of this month by user sign-in.
     * @param year
     * @param month
     * @return 
     */
    public int getCumulativeNumberOfMonth(int year, int month);
    
    /**
     * Clean up duplicate sign in records.
     * @param history
     * @return 
     */
    public List<SignInDate> clearUselessData(List<SignInDate> history);
}
