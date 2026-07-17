package studio.trc.bukkit.litesignin.reward;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Protects the boundary between persistence state and Bukkit reward dispatch. */
class SignInRewardDispatcherArchitectureTest {
    @Test
    void dispatcherDependsOnStorageAndOwnsRewardEvents() throws Exception {
        String source = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/reward/SignInRewardDispatcher.java");

        assertTrue(source.contains("private final Storage storage"),
                "reward dispatch must depend on the Storage abstraction");
        assertFalse(source.contains("SQLiteStorage"),
                "reward dispatch must not depend on the SQLiteStorage implementation");
        assertTrue(source.contains("new SignInRewardEvent"),
                "the dispatcher must preserve the cancellable reward event");
        assertTrue(source.contains("if (!event.isCancelled())"),
                "cancelled reward events must still prevent schedule execution");
    }

    @Test
    void sqliteStorageDelegatesRewardsWithoutBuildingSchedules() throws Exception {
        String source = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/database/storage/SQLiteStorage.java");
        int methodStart = source.indexOf("public void giveReward(SignInDate retroactiveDate)");
        int methodEnd = source.indexOf("\n    }", methodStart);
        String method = source.substring(methodStart, methodEnd);

        assertTrue(method.contains("rewardDispatcher.dispatch(retroactiveDate)"),
                "SQLiteStorage must delegate reward dispatch to the collaborator");
        assertFalse(source.contains("SignInRewardSchedule"),
                "SQLiteStorage must not retain reward schedule construction");
        assertFalse(source.contains("SignInRewardEvent"),
                "SQLiteStorage must not retain reward event construction");
    }

    @Test
    void rewardScheduleRecordsPerModuleFailuresWithAuditSummary() throws Exception {
        String source = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/reward/SignInRewardSchedule.java");

        assertTrue(source.contains("dispatchWithAudit"),
                "reward dispatch must be wrapped in an audit boundary");
        assertTrue(source.contains("failedModules.add(reward.getModule().name())"),
                "per-reward failures must record the module name");
        assertTrue(source.contains("logAuditSummary"),
                "a single audit summary must be logged when any reward fails");
    }

    private static String readSource(String relativePath) throws Exception {
        return Files.readString(Path.of(System.getProperty("user.dir"), relativePath));
    }
}
