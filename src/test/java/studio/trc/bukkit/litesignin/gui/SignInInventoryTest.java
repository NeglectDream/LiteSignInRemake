package studio.trc.bukkit.litesignin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Calendar;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import studio.trc.bukkit.litesignin.util.SignInDate;

class SignInInventoryTest
{
    @Test
    void contentsAndButtonMetadataAreDeeplyImmutable() {
        ItemStack originalItem = new ItemStack(Material.STONE, 1);
        SignInDate originalDate = SignInDate.getInstance(
                Calendar.getInstance().get(Calendar.YEAR), 1, 1);
        SignInGUIColumn originalColumn = new SignInGUIColumn(
                originalItem, 10, originalDate, SignInGUIColumn.KeyType.NOTHING_SIGNIN);
        SignInInventory snapshot = new SignInInventory(
                "test", new ItemStack[] {originalItem}, List.of(originalColumn), 1);

        originalItem.setAmount(32);
        originalDate.setDay(2);

        assertEquals(1, snapshot.getContents()[0].getAmount());
        SignInGUIColumn stored = snapshot.getButtons().get(0);
        assertEquals(1, stored.getItemStack().getAmount());
        assertEquals(1, stored.getDate().getDay());

        stored.getItemStack().setAmount(48);
        stored.getDate().setDay(3);
        assertEquals(1, snapshot.getButtons().get(0).getItemStack().getAmount());
        assertEquals(1, snapshot.getButtons().get(0).getDate().getDay());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getButtons().add(originalColumn));
    }
}
