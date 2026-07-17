package studio.trc.bukkit.litesignin.api;

import java.util.List;
import java.util.UUID;

import studio.trc.bukkit.litesignin.database.storage.SQLiteStorage;
import studio.trc.bukkit.litesignin.database.engine.SQLiteEngine;
import studio.trc.bukkit.litesignin.database.DatabaseTable;
import studio.trc.bukkit.litesignin.util.SignInDate;
import studio.trc.bukkit.litesignin.reward.util.SignInGroup;

import org.bukkit.entity.Player;

/**
 * Data Storage for Users
 * @author Dean
 */
public interface Storage
    extends Statistics
{
    /**
     * Give user rewards.
     * @param retroactiveDate Date of wanting to retroactively sign-in, if not, it's null.
     */
    public void giveReward(SignInDate retroactiveDate);
    
    /**
     * Get the year of last sign in.
     * @return 
     */
    public int getYear();
    
    /**
     * Get the month of last sign in.
     * @return 
     */
    public int getMonth();
    
    /**
     * Get the date of last sign in.
     * @return 
     */
    public int getDay();
    
    /**
     * Get the hour of last sign in.
     * @return 
     */
    public int getHour();
    
    /**
     * Get the minute of last sign in.
     * @return 
     */
    public int getMinute();
    
    /**
     * Get the second of last sign in.
     * @return 
     */
    public int getSecond();
    
    /**
     * Get the number of consecutive sign in for users.
     * @return 
     */
    public int getContinuousSignIn();
    
    /**
     * Get the number of consecutive sign in of this month for users.
     * @return 
     */
    public int getContinuousSignInOfMonth();
    
    /**
     * Get the number of retroactive cards.
     * @return 
     */
    public int getRetroactiveCard();
    
    /**
     * Get the Player's instance.
     * @return 
     */
    public Player getPlayer();
    
    /**
     * Get the player's name in database.
     * @return 
     */
    public String getName();
    
    /**
     * Get the player's group.
     * @return 
     */
    public SignInGroup getGroup();
    
    /**
     * Get the player's all matched groups.
     * @return 
     */
    public List<SignInGroup> getAllGroup();
    
    /**
     * Obtaining historical records.
     * @return 
     */
    public List<SignInDate> getHistory();
    
    /**
     * Setting the user's sign in history.
     * @param history 
     * @param saveData 
     */
    public void setHistory(List<SignInDate> history, boolean saveData);
    
    /**
     * Set the specified time to the user's sign in time.
     */
    public void signIn();
    
    /**
     * Set the current time to the user's sign as historicalDate.
     * @param historicalDate 
     */
    public void signIn(SignInDate historicalDate);

    /**
     * Attempts a normal sign-in and reports the exact outcome.
     *
     * <p>The default implementation preserves compatibility for third-party
     * Storage implementations. Built-in storage overrides this method with an
     * atomic database-backed implementation.</p>
     */
    default SignInResult trySignIn() {
        if (alreadySignIn()) {
            return SignInResult.ALREADY_SIGNED_IN;
        }
        signIn();
        return alreadySignIn() ? SignInResult.SUCCESS : SignInResult.STORAGE_FAILURE;
    }

    /** Attempts an administrator/API historical sign-in without consuming a card. */
    default SignInResult trySignIn(SignInDate historicalDate) {
        if (historicalDate == null) {
            return SignInResult.INVALID_DATE;
        }
        if (alreadySignIn(historicalDate)) {
            return SignInResult.ALREADY_SIGNED_IN;
        }
        signIn(historicalDate);
        return alreadySignIn(historicalDate) ? SignInResult.SUCCESS : SignInResult.STORAGE_FAILURE;
    }
    
    /**
     * Give player a specified number of cards.
     * @param amount 
     */
    public void giveRetroactiveCard(int amount);
    
    /**
     * Remove the specified number of cards from the player.
     * @param amount 
     */
    public void takeRetroactiveCard(int amount);
    
    /**
     * Set the specified number of cards from the player.
     * Only the virtual prop mode is vaild.
     * @param amount 
     * @param saveData 
     */
    public void setRetroactiveCard(int amount, boolean saveData);
    
    /**
     * Set the specified time to the user's sign in time.
     * @param date
     * @param saveData 
     */
    public void setSignInTime(SignInDate date, boolean saveData);
    
    /**
     * Set the number of consecutive sign in.
     * @param number
     * @param saveData 
     */
    public void setContinuousSignIn(int number, boolean saveData);
    
    /**
     * Save user data.
     */
    public void saveData();
    
    public static Storage getPlayer(Player player) {
        return SQLiteStorage.getPlayerData(player);
    }

    public static Storage getPlayer(String playerName) {
        for (SQLiteStorage data : SQLiteStorage.cache.values()) {
            String cachedName = data.getName();
            if (cachedName != null && cachedName.equalsIgnoreCase(playerName)) {
                return data;
            }
        }
        SQLiteEngine sqlite = SQLiteEngine.getInstance();
        UUID uuid = sqlite.query("SELECT UUID FROM "
                        + sqlite.getTableSyntax(DatabaseTable.PLAYER_DATA) + " WHERE Name = ?",
                result -> result.next() ? UUID.fromString(result.getString("UUID")) : null,
                playerName);
        return uuid != null ? SQLiteStorage.getPlayerData(uuid) : null;
    }

    public static Storage getPlayer(UUID uuid) {
        return SQLiteStorage.getPlayerData(uuid);
    }

    /** Loads a player without calling Bukkit APIs from the database thread. */
    public static Storage getPlayer(UUID uuid, String knownName) {
        return SQLiteStorage.getPlayerData(uuid, knownName);
    }
}
