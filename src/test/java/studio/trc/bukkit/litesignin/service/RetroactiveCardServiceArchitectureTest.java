package studio.trc.bukkit.litesignin.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Protects the separation between persistence state and Bukkit card handling. */
class RetroactiveCardServiceArchitectureTest {
    @Test
    void physicalCardServiceOwnsInventoryOperations() throws Exception {
        String source = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/service/RetroactiveCardService.java");

        assertTrue(source.contains("player.getInventory()"),
                "physical card handling must remain in the dedicated service");
        assertTrue(source.contains("reserve(int amount)"),
                "the service must expose the transaction reservation boundary");
        assertTrue(source.contains("restore(CardReservation reservation)"),
                "the service must expose compensating recovery");
    }

    @Test
    void sqliteStorageDelegatesPhysicalCardsWithoutDirectInventoryAccess() throws Exception {
        String source = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/database/storage/SQLiteStorage.java");

        assertFalse(source.contains("ItemStack"),
                "SQLiteStorage must not directly depend on ItemStack after extraction");
        assertFalse(source.contains("getInventory()"),
                "SQLiteStorage must not directly access Bukkit inventories after extraction");
        assertTrue(source.contains("retroactiveCardService.count()"),
                "card count must delegate to the physical-card service");
        assertTrue(source.contains("retroactiveCardService.plan(amount)"),
                "card reservation must delegate to the physical-card service planning boundary");
    }

    @Test
    void signInServiceUsesTheExtractedReservationType() throws Exception {
        String source = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/service/SignInService.java");

        assertTrue(source.contains("RetroactiveCardService.CardReservation"),
                "the sign-in transaction must use the extracted reservation type");
        assertFalse(source.contains("SQLiteStorage.CardReservation"),
                "the old storage-owned reservation type must not remain in the service");
    }

    private static String readSource(String relativePath) throws Exception {
        return Files.readString(Path.of(System.getProperty("user.dir"), relativePath));
    }
}
