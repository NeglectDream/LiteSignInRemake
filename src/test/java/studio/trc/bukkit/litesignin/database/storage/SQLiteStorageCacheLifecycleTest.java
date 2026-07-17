package studio.trc.bukkit.litesignin.database.storage;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Protects the cache ownership boundary between asynchronous persistence and
 * Bukkit player lifecycle events.
 */
class SQLiteStorageCacheLifecycleTest {
    @Test
    void quitSavesOffThreadAndEvictsOnlyFromBukkitThread() throws Exception {
        String quitSource = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/event/Quit.java");
        int save = quitSource.indexOf("storage.saveData();");
        int schedule = quitSource.indexOf("BukkitSchedulerManager.runBukkitTask", save);

        assertTrue(save >= 0, "quit must persist the cached storage before eviction");
        assertTrue(schedule > save,
                "the Bukkit eviction callback must be scheduled after asynchronous persistence");
        assertTrue(quitSource.contains("SQLiteStorage.evictIfOffline(uuid, storage)"),
                "quit must use the offline-only eviction boundary");
    }

    @Test
    void evictionChecksOnlineStateBeforeCompareAndRemove() throws Exception {
        String storageSource = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/database/storage/SQLiteStorage.java");
        int onlineCheck = storageSource.indexOf("Bukkit.getPlayer(uuid) != null");
        int remove = storageSource.indexOf("cache.remove(uuid, expected)", onlineCheck);

        assertTrue(onlineCheck >= 0,
                "cache eviction must refuse to remove storage for an online player");
        assertTrue(remove > onlineCheck,
                "the online-player guard must run before the compare-and-remove operation");
    }

    private static String readSource(String relativePath) throws Exception {
        return Files.readString(Path.of(System.getProperty("user.dir"), relativePath));
    }
}
