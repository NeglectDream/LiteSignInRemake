package studio.trc.bukkit.litesignin.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Folds the lifecycle ordering invariants of {@link SignInMenuService} into
 * repeatable source contracts. These checks prevent regressions that would
 * re-introduce the PacketEvents/Bukkit authority drift the rewrite fixed.
 */
class SignInMenuServiceLifecycleArchitectureTest
{
    @Test
    void closeRevokesPacketAuthorityBeforeTouchingBukkitInventory() throws Exception {
        String source = readSource();
        int closeRemove = source.indexOf("SignInMenuSession session = SESSIONS.remove(player.getUniqueId());");
        int bukkitClose = source.indexOf("player.closeInventory();", closeRemove);

        assertTrue(closeRemove >= 0,
                "close() must atomically remove the session before any Bukkit inventory access");
        assertTrue(bukkitClose > closeRemove,
                "close() must revoke packet authority (SESSIONS.remove) before player.closeInventory()");
    }

    @Test
    void handleInventoryCloseIsIdempotentViaConcurrentMapRemove() throws Exception {
        String source = readSource();
        assertTrue(source.contains("if (!SESSIONS.remove(player.getUniqueId(), session))"),
                "handleInventoryClose must use the CAS-style remove so a Bukkit InventoryCloseEvent "
                        + "re-entry after close() does not fire SignInGUICloseEvent twice");
        assertTrue(source.contains("session.getState() == SignInMenuSession.State.REPLACING"),
                "handleInventoryClose must ignore close events triggered by session replacement");
    }

    @Test
    void openMarksPreviousSessionAsReplacingBeforePublishingTheReplacement() throws Exception {
        String source = readSource();
        int beginReplacing = source.indexOf("previous.beginReplacing()");
        int publish = source.indexOf("SESSIONS.put(uuid, replacement);");

        assertTrue(beginReplacing >= 0 && publish > beginReplacing,
                "open() must mark the previous session as REPLACING before publishing the replacement, "
                        + "so PacketEvents cannot observe two live sessions for one player");
    }

    @Test
    void openRollsBackToThePreviousSessionOnBukkitRejection() throws Exception {
        String source = readSource();
        assertTrue(source.contains("rollbackOpen(player, replacement, previous, previousState, error)"),
                "open() must roll back to the previous session when Bukkit rejects the inventory");
        assertTrue(source.contains("previous.restoreAfterReplacement(previousState)"),
                "rollback must restore the previous session state");
        assertTrue(source.contains("previous.prepareForReopen()"),
                "rollback must re-arm the previous session for re-opening");
    }

    @Test
    void singleSessionRegistryRemainsTheOnlyAuthority() throws Exception {
        String source = readSource();
        assertFalse(source.contains("new HashMap<"),
                "SignInMenuService must not keep a second HashMap alongside the ConcurrentHashMap registry");
        assertTrue(source.contains("new ConcurrentHashMap<>()"));
    }

    @Test
    void shutdownClosesBothOnlineAndOfflineSessions() throws Exception {
        String source = readSource();
        assertTrue(source.contains("SESSIONS.remove(session.getPlayerId(), session)"),
                "shutdown() must use CAS removal so concurrent close paths do not double-process a session");
    }

    private static String readSource() throws Exception {
        return Files.readString(Path.of(System.getProperty("user.dir"),
                "src/main/java/studio/trc/bukkit/litesignin/gui/SignInMenuService.java"));
    }
}
