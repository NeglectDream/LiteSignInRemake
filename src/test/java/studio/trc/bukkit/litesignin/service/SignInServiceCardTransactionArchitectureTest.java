package studio.trc.bukkit.litesignin.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Protects the ordering contract between Bukkit inventory state and the
 * database-backed sign-in transaction.
 */
class SignInServiceCardTransactionArchitectureTest {
    @Test
    void physicalCardsAreReservedBeforeTheDatabaseCommit() throws Exception {
        String source = readSource();
        int reserve = source.indexOf("reservePhysicalRetroactiveCards(cardCost, date)");
        int commit = source.indexOf("sqliteStorage.commitSignIn", reserve);

        assertTrue(reserve >= 0, "physical sign-ins must reserve cards before persistence");
        assertTrue(commit > reserve,
                "the database commit must happen after the physical-card reservation");
    }

    @Test
    void failedCommitsRestoreThePhysicalReservation() throws Exception {
        String source = readSource();
        int failedResult = source.indexOf("if (!result.isSuccess())");
        int restoreAfterResult = source.indexOf(
                "restoreCardReservation(sqliteStorage, cardReservation, null)", failedResult);
        int restoreAfterException = source.indexOf(
                "restoreCardReservation(sqliteStorage, cardReservation, error)");

        assertTrue(restoreAfterException >= 0,
                "unexpected commit failures must restore reserved physical cards");
        assertTrue(failedResult >= 0 && restoreAfterResult > failedResult,
                "non-success commit results must restore reserved physical cards");
    }

    @Test
    void successfulCommitDoesNotCallTheLegacyCardRemovalPath() throws Exception {
        String source = readSource();
        assertFalse(source.contains("takeRetroactiveCard(cardCost)"),
                "successful sign-ins must not silently perform a second card removal");
        assertTrue(source.contains("Statistics.recordRetroactiveSignIn(storage.getUserUUID())"),
                "cooldown recording must remain after the successful commit boundary");
    }

    @Test
    void virtualCardsRemainInsideTheDatabaseCommit() throws Exception {
        String source = readSource();
        assertTrue(source.contains("!physicalCard ? cardCost : 0"),
                "virtual card costs must still be passed to the atomic database commit");
    }

    @Test
    void thirdPartyStorageFallsBackToInterfaceDefaultForNonPhysicalRetroactive() throws Exception {
        String source = readSource();

        assertTrue(source.contains("!(storage instanceof SQLiteStorage)"),
                "third-party storage implementations must be detected before the transaction path");
        assertTrue(source.contains("storage.trySignIn(normalized)"),
                "non-physical retroactive sign-ins must delegate to the interface default for third-party storage");
    }

    private static String readSource() throws Exception {
        return Files.readString(Path.of(System.getProperty("user.dir"),
                "src/main/java/studio/trc/bukkit/litesignin/service/SignInService.java"));
    }
}
