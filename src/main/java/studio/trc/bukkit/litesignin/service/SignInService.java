package studio.trc.bukkit.litesignin.service;

import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;

import studio.trc.bukkit.litesignin.Main;
import studio.trc.bukkit.litesignin.api.SignInResult;
import studio.trc.bukkit.litesignin.api.Statistics;
import studio.trc.bukkit.litesignin.api.Storage;
import studio.trc.bukkit.litesignin.database.storage.SQLiteStorage;
import studio.trc.bukkit.litesignin.event.custom.PlayerSignInEvent;
import studio.trc.bukkit.litesignin.util.LiteSignInUtils;
import studio.trc.bukkit.litesignin.util.PluginControl;
import studio.trc.bukkit.litesignin.util.SignInDate;

/**
 * Coordinates one complete sign-in use case.
 *
 * <p>This service is the single ordering boundary for validation, cancellable
 * Bukkit events, persistence, retroactive-card consumption, cooldown updates
 * and reward dispatch. Persistence-specific mutations remain inside the
 * storage implementation.</p>
 */
public final class SignInService {
    private static final Set<SignInKey> IN_PROGRESS = ConcurrentHashMap.newKeySet();

    private SignInService() {}

    public static SignInResult signIn(Storage storage) {
        SignInDate today = SignInDate.getInstance(new Date());
        return execute(storage, today, false, false);
    }

    public static SignInResult retroactiveSignIn(Storage storage, SignInDate date, boolean consumeCard) {
        if (date == null) {
            return SignInResult.INVALID_DATE;
        }
        SignInDate normalized = SignInDate.getInstance(date.getYear(), date.getMonth(), date.getDay());
        if (normalized == null) {
            return SignInResult.INVALID_DATE;
        }
        SignInDate minimumDate = PluginControl.getRetroactiveCardMinimumDate();
        if (minimumDate != null && normalized.compareTo(minimumDate) < 0) {
            return SignInResult.BEFORE_MINIMUM_DATE;
        }
        if (consumeCard && storage != null && storage.isRetroactiveCardCooldown()) {
            return SignInResult.COOLDOWN;
        }
        // Third-party Storage implementations do not support the journaling
        // transaction path. When no physical card is required, delegate to the
        // interface default so they can still perform retroactive sign-ins.
        if (storage != null && !(storage instanceof SQLiteStorage)) {
            if (consumeCard && PluginControl.enableRetroactiveCardRequiredItem()) {
                return SignInResult.STORAGE_FAILURE;
            }
            return storage.trySignIn(normalized);
        }
        return execute(storage, normalized, true, consumeCard);
    }

    private static SignInResult execute(Storage storage, SignInDate date,
                                        boolean retroactive, boolean consumeCard) {
        if (storage == null || date == null) {
            return SignInResult.INVALID_DATE;
        }
        if (!(storage instanceof SQLiteStorage)) {
            return SignInResult.STORAGE_FAILURE;
        }
        if (!Bukkit.isPrimaryThread()) {
            logFailure("Rejected an asynchronous sign-in attempt for " + storage.getUserUUID(), null);
            return SignInResult.STORAGE_FAILURE;
        }
        if (LiteSignInUtils.checkInDisabledWorlds(storage.getUserUUID())) {
            return SignInResult.DISABLED_WORLD;
        }
        if (storage.alreadySignIn(date)) {
            return SignInResult.ALREADY_SIGNED_IN;
        }

        SignInKey key = new SignInKey(storage.getUserUUID(), date.getDataText(false));
        if (!IN_PROGRESS.add(key)) {
            return SignInResult.IN_PROGRESS;
        }

        try {
            PlayerSignInEvent event = new PlayerSignInEvent(storage.getUserUUID(), date.copy(), retroactive);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return SignInResult.EVENT_CANCELLED;
            }

            int cardCost = 0;
            boolean physicalCard = false;
            SQLiteStorage sqliteStorage = (SQLiteStorage) storage;
            RetroactiveCardService.CardReservation cardReservation = null;
            if (retroactive && consumeCard) {
                cardCost = Math.max(0, PluginControl.getRetroactiveCardQuantityRequired());
                physicalCard = PluginControl.enableRetroactiveCardRequiredItem();
                if (physicalCard && cardCost > 0) {
                    cardReservation = sqliteStorage.reservePhysicalRetroactiveCards(cardCost, date);
                    if (cardReservation == null) {
                        return SignInResult.INSUFFICIENT_CARDS;
                    }
                } else if (!physicalCard && storage.getRetroactiveCard() < cardCost) {
                    return SignInResult.INSUFFICIENT_CARDS;
                }
            }

            SignInResult result;
            try {
                result = sqliteStorage.commitSignIn(date, !retroactive,
                        retroactive && consumeCard && !physicalCard ? cardCost : 0,
                        cardReservation != null ? cardReservation.operationId() : null);
            } catch (RuntimeException error) {
                restoreCardReservation(sqliteStorage, cardReservation, error);
                throw error;
            }
            if (!result.isSuccess()) {
                restoreCardReservation(sqliteStorage, cardReservation, null);
                return result;
            }

            if (retroactive && consumeCard) {
                Statistics.recordRetroactiveSignIn(storage.getUserUUID());
            }
            if (cardReservation != null) {
                try {
                    sqliteStorage.deletePhysicalCardOperation(cardReservation.operationId());
                } catch (RuntimeException cleanupError) {
                    logFailure("Unable to clean up committed physical-card operation for "
                            + storage.getUserUUID(), cleanupError);
                }
            }

            try {
                storage.giveReward(retroactive ? date : null);
            } catch (RuntimeException error) {
                // The unique sign-in transaction has already committed. Do not
                // roll it back or allow a second sign-in to duplicate rewards.
                logFailure("Reward dispatch failed after sign-in commit for "
                        + storage.getUserUUID(), error);
            }
            return SignInResult.SUCCESS;
        } finally {
            IN_PROGRESS.remove(key);
        }
    }

    private static void restoreCardReservation(SQLiteStorage storage,
                                               RetroactiveCardService.CardReservation reservation,
                                               Throwable originalError) {
        if (reservation == null) {
            return;
        }
        try {
            storage.restorePhysicalRetroactiveCards(reservation);
        } catch (RuntimeException restoreError) {
            if (originalError != null) {
                originalError.addSuppressed(restoreError);
            } else {
                logFailure("Unable to restore physical retroactive cards after a failed sign-in",
                        restoreError);
            }
        }
    }

    private static void logFailure(String message, Throwable error) {
        Main plugin = Main.getInstance();
        if (plugin == null) {
            return;
        }
        if (error == null) {
            plugin.getLogger().warning(message);
        } else {
            plugin.getLogger().log(Level.SEVERE, message, error);
        }
    }

    private record SignInKey(UUID uuid, String date) {}
}
