package studio.trc.bukkit.litesignin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Calendar;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import studio.trc.bukkit.litesignin.util.SignInDate;

class SignInGUIColumnTest
{
    @Test
    void keyButtonDistinguishesFromOtherButton() {
        SignInDate date = SignInDate.getInstance(
                Calendar.getInstance().get(Calendar.YEAR), 3, 14);
        SignInGUIColumn key = new SignInGUIColumn(
                new ItemStack(Material.STONE), 7, date, SignInGUIColumn.KeyType.NOTHING_SIGNIN);
        SignInGUIColumn other = new SignInGUIColumn(
                new ItemStack(Material.PAPER), 9, "NextPage");

        assertTrue(key.isKey());
        assertFalse(other.isKey());

        assertEquals(7, key.getKeyPostion());
        assertEquals(9, other.getKeyPostion());

        assertEquals(SignInGUIColumn.KeyType.NOTHING_SIGNIN, key.getKeyType());
        assertNull(other.getKeyType());

        assertEquals(date, key.getDate());
        assertNull(other.getDate());

        assertEquals("NextPage", other.getButtonName());
        assertNull(key.getButtonName());
    }

    @Test
    void itemStackAndDateReturnedAreDefensiveClones() {
        ItemStack original = new ItemStack(Material.STONE, 1);
        SignInDate date = SignInDate.getInstance(
                Calendar.getInstance().get(Calendar.YEAR), 3, 14);
        SignInGUIColumn column = new SignInGUIColumn(
                original, 7, date, SignInGUIColumn.KeyType.NOTHING_SIGNIN);

        ItemStack returned = column.getItemStack();
        assertNotSame(original, returned);
        returned.setAmount(32);
        assertEquals(1, column.getItemStack().getAmount(), "Mutation of returned stack must not leak back");

        SignInDate returnedDate = column.getDate();
        assertNotSame(date, returnedDate);
        returnedDate.setDay(2);
        assertEquals(14, column.getDate().getDay(), "Mutation of returned date must not leak back");
    }

    @Test
    void copyProducesAnIndependentButEqualKeyButton() {
        SignInDate date = SignInDate.getInstance(
                Calendar.getInstance().get(Calendar.YEAR), 3, 14);
        SignInGUIColumn original = new SignInGUIColumn(
                new ItemStack(Material.STONE), 7, date, SignInGUIColumn.KeyType.MISSED_SIGNIN);
        SignInGUIColumn copy = original.copy();

        assertNotSame(original, copy);
        assertEquals(original.getKeyPostion(), copy.getKeyPostion());
        assertEquals(original.getKeyType(), copy.getKeyType());
        assertEquals(original.getDate(), copy.getDate());
        assertTrue(copy.isKey());

        copy.getItemStack().setAmount(64);
        assertEquals(1, original.getItemStack().getAmount(), "copy() must not share mutable ItemStack state");
    }

    @Test
    void nullItemIsRepresentedAsNullClone() {
        SignInGUIColumn column = new SignInGUIColumn(
                null, 0, SignInDate.getInstance(
                        Calendar.getInstance().get(Calendar.YEAR), 1, 1),
                SignInGUIColumn.KeyType.NOTHING_SIGNIN);
        assertNull(column.getItemStack());
    }
}
