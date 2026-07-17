package studio.trc.bukkit.litesignin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Protects the durable physical-card recovery protocol. */
class PhysicalCardRecoveryArchitectureTest {
    @Test
    void databaseDefinesASeparatePhysicalCardJournal() throws Exception {
        String source = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/database/DatabaseTable.java");

        assertTrue(source.contains("PHYSICAL_CARD_OPERATION"));
        assertTrue(source.contains("OperationId"));
        assertTrue(source.contains("BeforeCount"));
    }

    @Test
    void inventoryMutationIsSplitAroundDurableJournalCreation() throws Exception {
        String source = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/database/storage/SQLiteStorage.java");

        assertTrue(source.contains("planned.serializeSnapshot()"));
        assertTrue(source.contains("OperationState.PREPARED"));
        assertTrue(source.contains("retroactiveCardService.apply(journaled)"));
        assertTrue(source.contains("markRemoved(operationId)"));
    }

    @Test
    void signInTransactionCommitsThePhysicalCardJournal() throws Exception {
        String source = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/database/repository/PlayerDataRepository.java");

        assertTrue(source.contains("physicalCardOperationId"));
        assertTrue(source.contains("State = ? WHERE OperationId = ?"));
        assertTrue(source.contains("COMMITTED"));
    }

    @Test
    void recoveryRunsOnTheBukkitJoinBoundary() throws Exception {
        String joinSource = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/event/Join.java");
        String mainSource = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/Main.java");
        String recoverySource = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/service/PhysicalCardRecoveryService.java");

        assertTrue(joinSource.contains("PhysicalCardRecoveryService.recover"));
        assertTrue(mainSource.contains("PhysicalCardRecoveryService.recoverOnlinePlayers"));
        assertTrue(recoverySource.contains("OperationState.COMMITTED"));
        assertTrue(recoverySource.contains("restoreMissingPhysicalCards"));
    }

    @Test
    void physicalCardServiceExposesPlanApplyAndRestoreBoundaries() throws Exception {
        String source = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/service/RetroactiveCardService.java");

        assertTrue(source.contains("CardReservation plan(int amount)"));
        assertTrue(source.contains("boolean apply(CardReservation reservation)"));
        assertTrue(source.contains("boolean restore(CardReservation reservation)"));
        assertTrue(source.contains("BukkitObjectOutputStream"));
        assertTrue(source.contains("deserializeItems(byte[] snapshot)"));
    }

    @Test
    void backupPersistsThePhysicalCardOperationJournal() throws Exception {
        String source = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/database/repository/PlayerDataRepository.java");

        assertTrue(source.contains("PHYSICAL_CARD_OPERATION"),
                "backup must query the physical-card operation journal");
        assertTrue(source.contains("OperationBackupRow"),
                "backup must carry a dedicated operation row type");
        assertTrue(source.contains("PhysicalCardOperation(OperationId, UUID, SignDate, State, Snapshot, CardCount, BeforeCount, CreatedAt)"),
                "backup must persist every operation journal column");
        assertTrue(source.contains("DatabaseTable.PHYSICAL_CARD_OPERATION.getDefaultCreateTableSyntax()"),
                "backup target must recreate the operation journal table");
    }

    private static String readSource(String relativePath) throws Exception {
        return Files.readString(Path.of(System.getProperty("user.dir"), relativePath));
    }
}
