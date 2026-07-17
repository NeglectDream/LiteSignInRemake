package studio.trc.bukkit.litesignin.packet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SignInMenuOverlayArchitectureTest
{
    @Test
    void overlayOnlyRewritesAuthoritativeServerPackets() throws Exception {
        String source = readMainSource("packet/SignInMenuOverlay.java");

        assertFalse(source.contains(".sendPacket("),
                "The overlay must not synthesize SET_SLOT/WINDOW_ITEMS packets");
        assertTrue(source.contains("new WrapperPlayServerWindowItems(event)"));
        assertTrue(source.contains("new WrapperPlayServerSetSlot(event)"));
        assertTrue(source.contains("unregisterListener(listener)"),
                "PacketEvents listener must be removed during disable/reload");
    }

    @Test
    void packetSendHookGuardsBukkitInventoryAccessByThread() throws Exception {
        String source = readMainSource("packet/SignInMenuOverlay.java");
        int threadGuard = source.indexOf("Bukkit.isPrimaryThread()");
        int inventoryRead = source.indexOf("player.getOpenInventory()");

        assertTrue(threadGuard >= 0 && inventoryRead > threadGuard,
                "PacketSendEvent must not unconditionally access Bukkit inventory state");
    }

    @Test
    void clickListenerUsesHighestPriorityAndPreservesEarlierCancellation() throws Exception {
        String source = readMainSource("gui/SignInMenuListener.java");

        assertTrue(source.contains("EventPriority.HIGHEST"));
        assertTrue(source.contains("boolean previouslyCancelled = event.isCancelled()"));
        assertTrue(source.contains("SignInMenuService.isCurrent(player, session, inventory)"));
    }

    private static String readMainSource(String relativePath) throws Exception {
        Path path = Path.of(System.getProperty("user.dir"), "src/main/java/studio/trc/bukkit/litesignin", relativePath);
        return Files.readString(path);
    }
}
