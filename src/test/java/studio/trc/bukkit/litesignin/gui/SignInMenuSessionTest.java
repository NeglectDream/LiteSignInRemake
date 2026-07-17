package studio.trc.bukkit.litesignin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

class SignInMenuSessionTest
{
    @Test
    void bindsOnlyOneExpectedWindowId() throws Exception {
        SignInMenuSession session = new SignInMenuSession(null, null);
        assertTrue(session.beginOpening());
        assertTrue(session.armOpenExpectation());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> first = () -> {
                start.await();
                return session.bindWindowId(7);
            };
            Callable<Boolean> second = () -> {
                start.await();
                return session.bindWindowId(8);
            };
            List<java.util.concurrent.Future<Boolean>> results =
                    List.of(executor.submit(first), executor.submit(second));
            start.countDown();

            long successes = 0;
            for (java.util.concurrent.Future<Boolean> result : results) {
                if (result.get()) {
                    successes++;
                }
            }
            assertEquals(1, successes);
            assertTrue(session.acceptsWindow(session.getWindowId()));
            assertFalse(session.acceptsWindow(session.getWindowId() + 1));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void invalidatedExpectationRejectsUnrelatedOpenWindow() {
        SignInMenuSession session = new SignInMenuSession(null, null);
        assertTrue(session.beginOpening());
        assertTrue(session.armOpenExpectation());
        session.invalidateOpenExpectation();

        assertFalse(session.bindWindowId(4));
        assertEquals(SignInMenuSession.State.OPENING, session.getState());
        assertEquals(-1, session.getWindowId());
    }

    @Test
    void replacementCanRestoreThePreviousOpenSession() {
        SignInMenuSession session = new SignInMenuSession(null, null);
        assertTrue(session.beginOpening());
        assertTrue(session.armOpenExpectation());
        assertTrue(session.bindWindowId(12));

        SignInMenuSession.State previous = session.beginReplacing();
        assertEquals(SignInMenuSession.State.OPEN, previous);
        assertFalse(session.acceptsWindow(12));

        session.restoreAfterReplacement(previous);
        assertEquals(SignInMenuSession.State.OPEN, session.getState());
        assertTrue(session.acceptsWindow(12));
    }

    @Test
    void sessionDoesNotOwnAProtocolStateIdCounter() {
        assertFalse(Arrays.stream(SignInMenuSession.class.getDeclaredFields())
                .map(Field::getName)
                .anyMatch(name -> name.equalsIgnoreCase("stateId")));
    }

    @Test
    void closingResetsWindowIdAndResyncFlag() {
        SignInMenuSession session = new SignInMenuSession(null, null);
        assertTrue(session.beginOpening());
        assertTrue(session.armOpenExpectation());
        assertTrue(session.bindWindowId(3));
        assertTrue(session.markResyncScheduled());
        assertFalse(session.markResyncScheduled());

        session.beginClosing();
        assertEquals(SignInMenuSession.State.CLOSING, session.getState());
        // Re-arming or binding during CLOSING is forbidden.
        assertFalse(session.armOpenExpectation());
        assertFalse(session.bindWindowId(3));

        session.finishClosing();
        assertEquals(SignInMenuSession.State.CLOSED, session.getState());
        assertEquals(-1, session.getWindowId());
        assertFalse(session.acceptsWindow(3));
        // Resync flag cleared so a future session starts clean.
        assertTrue(session.markResyncScheduled());
    }

    @Test
    void closedSessionRejectsReopeningThroughTheSameInstance() {
        SignInMenuSession session = new SignInMenuSession(null, null);
        session.beginClosing();
        session.finishClosing();

        assertFalse(session.beginOpening(), "A closed session must not transition back to OPENING");
        assertFalse(session.armOpenExpectation());
        assertFalse(session.bindWindowId(1));
        assertFalse(session.acceptsWindow(1));
    }

    @Test
    void resyncRequestsAreCoalescedToOnePerTick() {
        SignInMenuSession session = new SignInMenuSession(null, null);
        // First request wins; subsequent calls within the same tick are no-ops.
        assertTrue(session.markResyncScheduled());
        assertFalse(session.markResyncScheduled());
        assertFalse(session.markResyncScheduled());

        session.clearResyncScheduled();
        assertTrue(session.markResyncScheduled());
    }

    @Test
    void acceptsWindowOnlyMatchesTheOpenStateWindowId() {
        SignInMenuSession session = new SignInMenuSession(null, null);
        // PREPARED: never accepts.
        assertFalse(session.acceptsWindow(0));

        assertTrue(session.beginOpening());
        // OPENING without bound id: never accepts.
        assertFalse(session.acceptsWindow(0));

        assertTrue(session.armOpenExpectation());
        assertTrue(session.bindWindowId(5));
        assertTrue(session.acceptsWindow(5));
        assertFalse(session.acceptsWindow(-1));
        assertFalse(session.acceptsWindow(6));
    }
}
