package studio.trc.bukkit.litesignin.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Protects the DisplayName-based physical-card matching contract introduced after
 * CustomItems.yml removal. These assertions keep administrators honest: matching
 * must depend on a single configured display name rather than material/meta
 * equality, and the storage layer must not mutate inventory counters while the
 * Required-Item feature is enabled.
 */
class RetroactiveCardDisplayNameContractTest {

    @Test
    void pluginControlExposesConfiguredCardDisplayName() throws Exception {
        String source = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/util/PluginControl.java");

        assertTrue(source.contains("getRetroactiveCardRequiredItemName()"),
                "PluginControl must expose the configured card display name");
        assertTrue(source.contains("Required-Item.Name"),
                "the display name must be read from Retroactive-Card.Required-Item.Name");
        assertTrue(source.contains("ColorUtils.toColor(name)"),
                "color codes must be translated before matching");
        assertFalse(source.contains("getRetroactiveCardRequiredItem(Player"),
                "the legacy ItemStack template accessor must be removed");
    }

    @Test
    void retroactiveCardServiceMatchesByDisplayNameOnly() throws Exception {
        String source = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/service/RetroactiveCardService.java");

        assertTrue(source.contains("getRequiredDisplayName()"),
                "the service must read the configured display name boundary");
        assertTrue(source.contains("isMatchingCard(ItemStack item, String displayName)"),
                "matching must compare an item against a display name, not a template");
        assertTrue(source.contains("getDisplayName()"),
                "matching must rely on ItemMeta#getDisplayName()");
        assertFalse(source.contains("getTemplate"),
                "the ItemStack template accessor must be gone");
        assertFalse(source.contains("item.getType() == template.getType()"),
                "material equality must no longer participate in matching");
        assertFalse(source.contains("Objects.equals(item.getItemMeta(), template.getItemMeta())"),
                "full ItemMeta equality must no longer participate in matching");
    }

    @Test
    void sqliteStorageDoesNotMutateInventoryInPhysicalMode() throws Exception {
        String source = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/database/storage/SQLiteStorage.java");

        assertFalse(source.contains("retroactiveCardService.give("),
                "give must not delegate to inventory mutation in physical mode");
        assertFalse(source.contains("retroactiveCardService.set("),
                "set must not delegate to inventory mutation in physical mode");
        assertFalse(source.contains("retroactiveCardService.take("),
                "take must not delegate to inventory mutation in physical mode");
        assertTrue(source.contains("retroactiveCardService.count()"),
                "the read path must still delegate to the physical-card service");
        assertTrue(source.contains("retroactiveCardService.plan(amount)"),
                "the reservation boundary must remain for the sign-in flow");
    }

    @Test
    void retroactiveCardCommandDisablesMutatingSubcommandsInPhysicalMode() throws Exception {
        String source = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/command/subcommand/RetroactiveCardCommand.java");

        assertTrue(source.contains("enableRetroactiveCardRequiredItem()"),
                "the command must branch on the physical-card mode");
        assertTrue(source.contains("RetroactiveCard.Physical-Mode-Unsupported"),
                "a dedicated unsupported-mode message must be sent");
        assertFalse(source.contains("RetroactiveCard.Player-Offline"),
                "the obsolete Player-Offline branch must be removed");
    }

    private static String readSource(String relativePath) throws Exception {
        return Files.readString(Path.of(System.getProperty("user.dir"), relativePath));
    }
}
