package studio.trc.bukkit.litesignin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Protects the command, runtime-state, startup, and logging safety boundaries. */
class CoreSafetyArchitectureTest {
    @Test
    void retroactiveCardHoldPermissionIsCheckedSeparatelyFromUsePermission() throws Exception {
        String source = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/command/subcommand/ClickCommand.java");

        assertTrue(source.contains("Retroactive-Card.Use"),
                "retroactive sign-in must still require use permission");
        assertTrue(source.contains("Retroactive-Card.Hold"),
                "card retention must use the dedicated hold permission");
    }

    @Test
    void onlineTimeStateUsesConcurrentMapsAndAtomicReads() throws Exception {
        String source = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/util/OnlineTimeRecord.java");

        assertTrue(source.contains("ConcurrentHashMap"),
                "online-time state must support concurrent PlaceholderAPI reads");
        assertTrue(source.contains("Long joinTime = joinTimeRecord.get(uuid)"),
                "join time must be read once instead of containsKey followed by get");
        assertFalse(source.contains("new HashMap"),
                "online-time state must not use a non-thread-safe HashMap");
    }

    @Test
    void commandRegistrationFailsWithAnExplicitConfigurationError() throws Exception {
        String source = readSource("src/main/java/studio/trc/bukkit/litesignin/Main.java");

        assertTrue(source.contains("if (command == null)"),
                "missing plugin.yml command must be detected explicitly");
        assertTrue(source.contains("Command 'signin' is not declared in plugin.yml"),
                "the startup error must identify the missing command");
    }

    @Test
    void touchedRuntimeFailuresUseStructuredLogging() throws Exception {
        String rewardSource = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/reward/SignInRewardUtil.java");
        String defaultConfigSource = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/configuration/DefaultConfigurationFile.java");
        String configSource = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/configuration/ConfigurationType.java");

        assertFalse(rewardSource.contains("printStackTrace"),
                "reward failures must use contextual plugin logging");
        assertFalse(defaultConfigSource.contains("printStackTrace"),
                "default configuration failures must use structured logging");
        assertFalse(configSource.contains("printStackTrace"),
                "configuration failures must use structured logging");
    }

    private static String readSource(String relativePath) throws Exception {
        return Files.readString(Path.of(System.getProperty("user.dir"), relativePath));
    }
}
