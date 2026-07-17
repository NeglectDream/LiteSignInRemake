package studio.trc.bukkit.litesignin.database.storage;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;

import studio.trc.bukkit.litesignin.api.SignInResult;
import studio.trc.bukkit.litesignin.api.Storage;
import studio.trc.bukkit.litesignin.configuration.ConfigurationUtil;
import studio.trc.bukkit.litesignin.configuration.ConfigurationType;
import studio.trc.bukkit.litesignin.configuration.RobustConfiguration;
import studio.trc.bukkit.litesignin.database.DatabaseException;
import studio.trc.bukkit.litesignin.database.repository.PlayerDataRepository;
import studio.trc.bukkit.litesignin.database.repository.PhysicalCardOperationRepository;
import studio.trc.bukkit.litesignin.util.SignInDate;
import studio.trc.bukkit.litesignin.util.PluginControl;
import studio.trc.bukkit.litesignin.database.engine.SQLiteEngine;
import studio.trc.bukkit.litesignin.reward.SignInRewardDispatcher;
import studio.trc.bukkit.litesignin.reward.util.SignInGroup;
import studio.trc.bukkit.litesignin.service.RetroactiveCardService;
import studio.trc.bukkit.litesignin.service.SignInService;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class SQLiteStorage implements Storage {
    /** Compatibility view; project code must use the controlled cache methods. */
    @Deprecated
    public static final Map<UUID, SQLiteStorage> cache = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> cacheLoadedAt = new ConcurrentHashMap<>();

    @Getter
    private volatile int continuous;
    @Getter
    private volatile int year = 1970;
    @Getter
    private volatile int month = 1;
    @Getter
    private volatile int day = 1;
    @Getter
    private volatile int hour;
    @Getter
    private volatile int minute;
    @Getter
    private volatile int second;
    @Getter
    private volatile String name;
    private volatile List<SignInDate> history = List.of();
    private final UUID uuid;
    private final PlayerDataRepository playerDataRepository;
    private final PhysicalCardOperationRepository physicalCardOperationRepository;
    private final SignInRewardDispatcher rewardDispatcher;
    private final RetroactiveCardService retroactiveCardService;
    private volatile int retroactiveCard;
    private volatile boolean loaded;
    private volatile boolean dirty;

    public SQLiteStorage(Player player) {
        this(player.getUniqueId(), player.getName());
        cache.put(uuid, this);
        cacheLoadedAt.put(uuid, System.currentTimeMillis());
    }

    public SQLiteStorage(UUID uuid) {
        this(uuid, null);
        cache.put(uuid, this);
        cacheLoadedAt.put(uuid, System.currentTimeMillis());
    }

    private SQLiteStorage(UUID uuid, String knownName) {
        this.uuid = uuid;
        this.playerDataRepository = new PlayerDataRepository(requireEngine());
        this.physicalCardOperationRepository = new PhysicalCardOperationRepository(requireEngine());
        this.rewardDispatcher = new SignInRewardDispatcher(this);
        this.retroactiveCardService = new RetroactiveCardService(uuid);
        reloadData(knownName);
    }

    public synchronized void reloadData() {
        reloadData(name);
    }

    private synchronized void reloadData(String knownName) {
        if (dirty) {
            saveData();
        }
        PlayerDataRepository.PlayerDataSnapshot snapshot = playerDataRepository.load(uuid, knownName);
        name = snapshot.name();
        year = snapshot.year();
        month = snapshot.month();
        day = snapshot.day();
        hour = snapshot.hour();
        minute = snapshot.minute();
        second = snapshot.second();
        retroactiveCard = snapshot.retroactiveCard();
        history = immutableHistory(snapshot.history());
        continuous = calculateContinuous(history);
        loaded = true;
        dirty = false;
    }

    @Override
    public synchronized void checkContinuousSignIn() {
        int calculated = calculateContinuous(history);
        if (continuous != calculated) {
            continuous = calculated;
            dirty = true;
            saveData();
        }
    }
    
    @Override
    public void giveReward(SignInDate retroactiveDate) {
        rewardDispatcher.dispatch(retroactiveDate);
    }
    
    @Override
    public SignInGroup getGroup() {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return null;
        SignInGroup group = null;
        RobustConfiguration config = ConfigurationUtil.getConfig(ConfigurationType.REWARD_SETTINGS);
        for (String groups : config.getStringList("Reward-Settings.Groups-Priority")) {
            if (config.get("Reward-Settings.Permission-Groups." + groups + ".Permission") != null) {
                if (player.hasPermission(config.getString("Reward-Settings.Permission-Groups." + groups + ".Permission"))) {
                    group = new SignInGroup(groups);
                    break;
                }
            }
        }
        if (group == null && config.get("Reward-Settings.Permission-Groups.Default") != null) {
            group = new SignInGroup("Default");
        }
        return group;
    }

    @Override
    public List<SignInGroup> getAllGroup() {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return null;
        List<SignInGroup> groups = new ArrayList<>();
        RobustConfiguration config = ConfigurationUtil.getConfig(ConfigurationType.REWARD_SETTINGS);
        config.getStringList("Reward-Settings.Groups-Priority").stream()
            .filter(group -> group.equalsIgnoreCase("Default") || (config.get("Reward-Settings.Permission-Groups." + group + ".Permission") != null && player.hasPermission(config.getString("Reward-Settings.Permission-Groups." + group + ".Permission"))))
            .forEach(group -> groups.add(new SignInGroup(group)));
        if (groups.isEmpty() && config.get("Reward-Settings.Permission-Groups.Default") != null) {
            groups.add(new SignInGroup("Default"));
        }
        return groups;
    }
    
    @Override
    public int getContinuousSignIn() {
        return continuous;
    }
    
    @Override
    public int getCumulativeNumber() {
        return clearUselessData(getHistory()).size();
    }
    
    @Override
    public int getContinuousSignInOfMonth() {
        return SignInDate.getContinuousOfMonth(history);
    }
    
    @Override
    public int getCumulativeNumberOfMonth(int year, int month) {
        return clearUselessData(getHistory()).stream().filter(record -> record.getYear() == year && record.getMonth() == month).toArray().length;
//        return SignInDate.getCumulativeNumberOfMonth(clearUselessData(getHistory()), month);
    }
    
    @Override
    public int getRetroactiveCard() {
        return retroactiveCardService.isEnabled()
                ? retroactiveCardService.count()
                : retroactiveCard;
    }
    
    @Override
    public UUID getUserUUID() {
        return uuid;
    }
    
    @Override
    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }
    
    @Override
    public List<SignInDate> getHistory() {
        return copyHistory(history);
    }

    @Override
    public boolean alreadySignIn() {
        SignInDate today = SignInDate.getInstance(new Date());
        return today != null && alreadySignIn(today);
    }

    @Override
    public boolean alreadySignIn(SignInDate date) {
        if (date == null) {
            return false;
        }
        String target = dateKey(date);
        return history.stream().anyMatch(record -> target.equals(dateKey(record)));
    }

    @Override
    public List<SignInDate> clearUselessData(List<SignInDate> dates) {
        if (dates == null || dates.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, SignInDate> unique = new LinkedHashMap<>();
        for (SignInDate date : dates) {
            if (date != null) {
                unique.putIfAbsent(dateKey(date), date.copy());
            }
        }
        List<SignInDate> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparing(SQLiteStorage::toLocalDate));
        return result;
    }

    @Override
    public synchronized void setHistory(List<SignInDate> history, boolean saveData) {
        this.history = immutableHistory(clearUselessData(history));
        continuous = calculateContinuous(this.history);
        dirty = true;
        if (saveData) {
            // History changes require the full save path that rewrites the
            // canonical history table; saveData() alone only updates scalars.
            playerDataRepository.save(uuid, new PlayerDataRepository.PlayerDataSnapshot(
                    name, year, month, day, hour, minute, second, continuous,
                    retroactiveCard, getHistory()));
            dirty = false;
        }
    }

    /** Compatibility wrapper; new project code must inspect {@link #trySignIn()}. */
    @Override
    public void signIn() {
        trySignIn();
    }

    /** Compatibility wrapper; administrator/API sign-in does not consume a card. */
    @Override
    public void signIn(SignInDate historicalDate) {
        trySignIn(historicalDate);
    }

    @Override
    public SignInResult trySignIn() {
        return SignInService.signIn(this);
    }

    @Override
    public SignInResult trySignIn(SignInDate historicalDate) {
        return SignInService.retroactiveSignIn(this, historicalDate, false);
    }

    /**
     * Commits one canonical sign-in record and aggregate state atomically.
     * This method performs no Bukkit event, inventory or reward operations.
     */
    public synchronized SignInResult commitSignIn(SignInDate requestedDate,
                                                   boolean updateLastSignIn,
                                                   int virtualCardCost) {
        return commitSignIn(requestedDate, updateLastSignIn, virtualCardCost, null);
    }

    public synchronized SignInResult commitSignIn(SignInDate requestedDate,
                                                   boolean updateLastSignIn,
                                                   int virtualCardCost,
                                                   UUID physicalCardOperationId) {
        if (requestedDate == null) {
            return SignInResult.INVALID_DATE;
        }
        SignInDate storedDate = requestedDate.copy();
        if (alreadySignIn(storedDate)) {
            return SignInResult.ALREADY_SIGNED_IN;
        }

        List<SignInDate> nextHistory = getHistory();
        nextHistory.add(storedDate);
        nextHistory = clearUselessData(nextHistory);
        int nextContinuous = calculateContinuous(nextHistory);
        PlayerDataRepository.PlayerDataSnapshot nextState = new PlayerDataRepository.PlayerDataSnapshot(
                name,
                updateLastSignIn ? storedDate.getYear() : year,
                updateLastSignIn ? storedDate.getMonth() : month,
                updateLastSignIn ? storedDate.getDay() : day,
                updateLastSignIn ? storedDate.getHour() : hour,
                updateLastSignIn ? storedDate.getMinute() : minute,
                updateLastSignIn ? storedDate.getSecond() : second,
                nextContinuous,
                retroactiveCard,
                nextHistory);

        PlayerDataRepository.CommitResult committed = playerDataRepository.commitSignIn(
                uuid, storedDate, nextState, virtualCardCost, physicalCardOperationId);
        if (!committed.result().isSuccess()) {
            return committed.result();
        }

        PlayerDataRepository.PlayerDataSnapshot committedState = committed.state();
        history = immutableHistory(committedState.history());
        continuous = committedState.continuous();
        retroactiveCard = committed.remainingCards();
        if (updateLastSignIn) {
            year = committedState.year();
            month = committedState.month();
            day = committedState.day();
            hour = committedState.hour();
            minute = committedState.minute();
            second = committedState.second();
        }
        loaded = true;
        dirty = false;
        return SignInResult.SUCCESS;
    }
    
    @Override
    public synchronized void setSignInTime(SignInDate date, boolean saveData) {
        if (date == null) {
            return;
        }
        year = date.getYear();
        month = date.getMonth();
        day = date.getDay();
        hour = date.getHour();
        minute = date.getMinute();
        second = date.getSecond();
        dirty = true;
        if (saveData) {
            saveData();
        }
    }

    @Override
    public synchronized void setContinuousSignIn(int number, boolean saveData) {
        continuous = Math.max(0, number);
        dirty = true;
        if (saveData) {
            saveData();
        }
    }
    
    @Override
    public void giveRetroactiveCard(int amount) {
        if (amount < 1) {
            return;
        }
        // Physical-card mode matches items by display name; administrators can only
        // hand out cards by placing the configured item directly. Virtual counters
        // are updated only when the Required-Item feature is disabled.
        if (retroactiveCardService.isEnabled()) {
            return;
        }
        setRetroactiveCard(getRetroactiveCard() + amount, true);
    }

    @Override
    public void takeRetroactiveCard(int amount) {
        if (amount < 1) {
            return;
        }
        // Physical-card mode never mutates inventory counters from a command; the
        // journaling reserve flow handles deduction at sign-in time instead.
        if (retroactiveCardService.isEnabled()) {
            return;
        }
        setRetroactiveCard(getRetroactiveCard() - amount, true);
    }

    /** Persists a reservation before changing the Bukkit inventory. */
    public synchronized RetroactiveCardService.CardReservation reservePhysicalRetroactiveCards(
            int amount, SignInDate signDate) {
        RetroactiveCardService.CardReservation planned = retroactiveCardService.plan(amount);
        if (planned == null || signDate == null) {
            return null;
        }
        UUID operationId = UUID.randomUUID();
        physicalCardOperationRepository.create(new PhysicalCardOperationRepository.PendingOperation(
                operationId, uuid, dateKey(signDate),
                PhysicalCardOperationRepository.OperationState.PREPARED,
                planned.serializeSnapshot(), planned.cardCount(), retroactiveCardService.count(),
                System.currentTimeMillis()));
        RetroactiveCardService.CardReservation journaled = planned.withOperationId(operationId);
        if (!retroactiveCardService.apply(journaled)) {
            physicalCardOperationRepository.delete(operationId);
            return null;
        }
        physicalCardOperationRepository.markRemoved(operationId);
        return journaled;
    }

    /** Restores a physical-card reservation after a failed database operation. */
    public synchronized void restorePhysicalRetroactiveCards(
            RetroactiveCardService.CardReservation reservation) {
        boolean restored = retroactiveCardService.restore(reservation);
        if (restored && reservation.operationId() != null) {
            physicalCardOperationRepository.delete(reservation.operationId());
        }
    }

    public List<PhysicalCardOperationRepository.PendingOperation> getPendingPhysicalCardOperations() {
        return physicalCardOperationRepository.findForPlayer(uuid);
    }

    public boolean hasCanonicalSignIn(String signDate) {
        return physicalCardOperationRepository.hasSignIn(uuid, signDate);
    }

    public void deletePhysicalCardOperation(UUID operationId) {
        if (operationId != null) {
            physicalCardOperationRepository.delete(operationId);
        }
    }

    public void restoreMissingPhysicalCards(byte[] snapshot, int cardCount) {
        retroactiveCardService.restoreMissing(snapshot, cardCount);
    }
    
    @Override
    public synchronized void setRetroactiveCard(int amount, boolean saveData) {
        // Physical-card mode derives the count from the player's inventory, so an
        // explicit setter is a no-op; callers adjust the card pool by moving items.
        if (retroactiveCardService.isEnabled()) {
            return;
        }
        int normalized = Math.max(0, amount);
        retroactiveCard = normalized;
        dirty = true;
        if (saveData) {
            saveData();
        }
    }

    public synchronized void updateName(String latestName, boolean saveData) {
        if (latestName == null || latestName.isBlank() || latestName.equals(name)) {
            return;
        }
        name = latestName;
        dirty = true;
        if (saveData) {
            saveData();
        }
    }

    @Override
    public synchronized void saveData() {
        if (!loaded) {
            return;
        }
        playerDataRepository.updateFields(uuid, new PlayerDataRepository.PlayerDataSnapshot(
                name, year, month, day, hour, minute, second, continuous,
                retroactiveCard, getHistory()));
        dirty = false;
    }

    public static SQLiteStorage getPlayerData(Player player) {
        return getPlayerData(player.getUniqueId(), player.getName());
    }

    public static SQLiteStorage getPlayerData(UUID uuid) {
        return getPlayerData(uuid, null);
    }

    public static SQLiteStorage getPlayerData(UUID uuid, String knownName) {
        long now = System.currentTimeMillis();
        SQLiteStorage data = cache.computeIfAbsent(uuid, key -> {
            SQLiteStorage created = new SQLiteStorage(key, knownName);
            cacheLoadedAt.put(key, now);
            return created;
        });
        if (knownName != null) {
            data.updateName(knownName, false);
        }

        double refreshSeconds = PluginControl.getSQLiteRefreshInterval();
        if (refreshSeconds <= 0) {
            return data;
        }
        long loadedAt = cacheLoadedAt.getOrDefault(uuid, 0L);
        if (now - loadedAt < Math.round(refreshSeconds * 1000D)) {
            return data;
        }

        synchronized (data) {
            long currentLoadedAt = cacheLoadedAt.getOrDefault(uuid, 0L);
            if (now - currentLoadedAt >= Math.round(refreshSeconds * 1000D)) {
                data.reloadData(knownName);
                cacheLoadedAt.put(uuid, System.currentTimeMillis());
            }
        }
        return data;
    }

    public static SQLiteStorage getCached(UUID uuid) {
        return uuid != null ? cache.get(uuid) : null;
    }

    /**
     * Saves a cached player and removes it only when the player is still offline.
     *
     * <p>The eviction check must run on the Bukkit thread. That serializes it
     * with join events and prevents a rejoining player from receiving a storage
     * instance that is removed immediately after the join.</p>
     */
    public static void evictIfOffline(UUID uuid, SQLiteStorage expected) {
        if (uuid == null || expected == null || Bukkit.getPlayer(uuid) != null) {
            return;
        }
        if (cache.remove(uuid, expected)) {
            cacheLoadedAt.remove(uuid);
        }
    }

    /** Compatibility wrapper for integrations that still flush on one thread. */
    @Deprecated
    public static void flushAndEvict(UUID uuid, SQLiteStorage expected) {
        if (uuid == null || expected == null || cache.get(uuid) != expected) {
            return;
        }
        expected.saveData();
        if (Bukkit.isPrimaryThread()) {
            evictIfOffline(uuid, expected);
        }
    }

    public static void flushAll() {
        DatabaseException failure = null;
        for (SQLiteStorage data : new ArrayList<>(cache.values())) {
            try {
                data.saveData();
            } catch (DatabaseException ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    public static void clearCache() {
        cache.clear();
        cacheLoadedAt.clear();
    }
    
    /** Back up both aggregate player data and canonical sign-in history. */
    public static void backup(String filePath) throws SQLException {
        new PlayerDataRepository(requireEngine()).backup(filePath);
    }

    private static SQLiteEngine requireEngine() {
        SQLiteEngine sqlite = SQLiteEngine.getInstance();
        if (sqlite == null) {
            throw new DatabaseException("SQLite engine has not been initialized");
        }
        return sqlite;
    }

    private static String dateKey(SignInDate date) {
        return String.format("%04d-%02d-%02d", date.getYear(), date.getMonth(), date.getDay());
    }

    private static LocalDate toLocalDate(SignInDate date) {
        return LocalDate.of(date.getYear(), date.getMonth(), date.getDay());
    }

    private static List<SignInDate> copyHistory(List<SignInDate> source) {
        List<SignInDate> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }
        for (SignInDate date : source) {
            if (date != null) {
                copy.add(date.copy());
            }
        }
        return copy;
    }

    private static List<SignInDate> immutableHistory(List<SignInDate> source) {
        return List.copyOf(copyHistory(source));
    }

    private static int calculateContinuous(List<SignInDate> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        List<LocalDate> dates = records.stream()
                .filter(date -> date != null)
                .map(SQLiteStorage::toLocalDate)
                .distinct()
                .sorted()
                .toList();
        if (dates.isEmpty() || !dates.get(dates.size() - 1).equals(LocalDate.now())) {
            return 0;
        }
        int continuous = 1;
        for (int index = dates.size() - 1; index > 0; index--) {
            if (!dates.get(index - 1).plusDays(1).equals(dates.get(index))) {
                break;
            }
            continuous++;
        }
        return continuous;
    }

}
