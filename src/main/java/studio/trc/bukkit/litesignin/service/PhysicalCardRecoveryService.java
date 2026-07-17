package studio.trc.bukkit.litesignin.service;

import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import studio.trc.bukkit.litesignin.Main;
import studio.trc.bukkit.litesignin.database.repository.PhysicalCardOperationRepository;
import studio.trc.bukkit.litesignin.database.storage.SQLiteStorage;

/** Reconciles physical-card journals left by an interrupted server process. */
public final class PhysicalCardRecoveryService {
    private PhysicalCardRecoveryService() {}

    public static void recoverOnlinePlayers() {
        if (!Bukkit.isPrimaryThread()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            recover(SQLiteStorage.getPlayerData(player));
        }
    }

    public static void recover(SQLiteStorage storage) {
        if (storage == null || !Bukkit.isPrimaryThread()) {
            return;
        }
        List<PhysicalCardOperationRepository.PendingOperation> operations =
                storage.getPendingPhysicalCardOperations();
        for (PhysicalCardOperationRepository.PendingOperation operation : operations) {
            try {
                if (operation.state() == PhysicalCardOperationRepository.OperationState.COMMITTED
                        || storage.hasCanonicalSignIn(operation.signDate())) {
                    storage.deletePhysicalCardOperation(operation.operationId());
                    continue;
                }

                if (storage.getRetroactiveCard() < operation.beforeCount()) {
                    storage.restoreMissingPhysicalCards(operation.snapshot(), operation.beforeCount());
                }
                storage.deletePhysicalCardOperation(operation.operationId());
            } catch (RuntimeException error) {
                logFailure("Unable to recover physical retroactive-card operation "
                        + operation.operationId(), error);
            }
        }
    }

    private static void logFailure(String message, Throwable error) {
        Main plugin = Main.getInstance();
        if (plugin != null) {
            plugin.getLogger().log(Level.SEVERE, message, error);
        }
    }
}
