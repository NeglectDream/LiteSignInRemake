package studio.trc.bukkit.litesignin.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Calendar;

import org.junit.jupiter.api.Test;

class SignInDateTest
{
    @Test
    void copyReturnsEqualButIndependentDateValue() {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        SignInDate original = SignInDate.getInstance(year, 5, 20, 8, 30, 45);
        SignInDate copy = original.copy();

        assertEquals(original, copy);
        assertNotSame(original, copy);
        assertTrue(original.hasTimePeriod());
        assertTrue(copy.hasTimePeriod());
        assertEquals(8, copy.getHour());

        copy.setDay(21);
        assertEquals(20, original.getDay(), "copy() must not share mutable calendar state");
    }

    @Test
    void copyOfDateOnlyValuePreservesEqualityWithoutTimePeriod() {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        SignInDate original = SignInDate.getInstance(year, 5, 20);
        SignInDate copy = original.copy();

        assertEquals(original, copy);
        assertNotSame(original, copy);
        assertTrue(!original.hasTimePeriod());
        assertTrue(!copy.hasTimePeriod());
    }
}
