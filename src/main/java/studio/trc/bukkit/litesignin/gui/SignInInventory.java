package studio.trc.bukkit.litesignin.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.bukkit.inventory.ItemStack;

import studio.trc.bukkit.litesignin.util.SignInDate;

/**
 * The sign-in menu snapshot.
 * <p>
 * Keeps a defensive snapshot of the title, contents and button metadata.
 * Bukkit owns a real empty container for interaction and protocol state, while
 * PacketEvents renders these snapshot items into authoritative server packets.
 */
public class SignInInventory
{
    private final int month;
    private final int year;
    private final String title;
    private final ItemStack[] contents;
    private final List<SignInGUIColumn> buttons;

    public SignInInventory(String title, ItemStack[] contents, List<SignInGUIColumn> buttons) {
        this.title = title;
        this.contents = cloneContents(contents);
        this.buttons = cloneButtons(buttons);
        SignInDate today = SignInDate.getInstance(new Date());
        month = today.getMonth();
        year = today.getYear();
    }

    public SignInInventory(String title, ItemStack[] contents, List<SignInGUIColumn> buttons, int month) {
        this.title = title;
        this.contents = cloneContents(contents);
        this.buttons = cloneButtons(buttons);
        this.month = month;
        year = SignInDate.getInstance(new Date()).getYear();
    }

    public SignInInventory(String title, ItemStack[] contents, List<SignInGUIColumn> buttons, int month, int year) {
        this.title = title;
        this.contents = cloneContents(contents);
        this.buttons = cloneButtons(buttons);
        this.month = month;
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public int getYear() {
        return year;
    }

    public int getNextPageMonth() {
        if (month == 12) {
            return 1;
        } else {
            return month + 1;
        }
    }

    public int getNextPageYear() {
        if (month != 12) {
            return year;
        }
        return year + 1;
    }

    public int getPreviousPageMonth() {
        if (month == 1) {
            return 12;
        } else {
            return month - 1;
        }
    }

    public int getPreviousPageYear() {
        if (month != 1) {
            return year;
        }
        return year - 1;
    }

    public String getTitle() {
        return title;
    }

    public ItemStack[] getContents() {
        return cloneContents(contents);
    }

    /**
     * Returns an unmodifiable view of the button metadata.
     *
     * <p>The snapshot is immutable: callers (e.g. {@code SignInGUIOpenEvent}
     * listeners) cannot mutate the internal list, matching the class contract
     * documented above.
     */
    public List<SignInGUIColumn> getButtons() {
        return Collections.unmodifiableList(buttons);
    }

    private static List<SignInGUIColumn> cloneButtons(List<SignInGUIColumn> buttons) {
        List<SignInGUIColumn> copy = new ArrayList<>();
        if (buttons == null) {
            return copy;
        }
        for (SignInGUIColumn column : buttons) {
            if (column != null) {
                copy.add(column.copy());
            }
        }
        return copy;
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        if (contents == null) {
            return new ItemStack[0];
        }
        ItemStack[] cloned = Arrays.copyOf(contents, contents.length);
        for (int i = 0;i < cloned.length;i++) {
            cloned[i] = cloned[i] != null ? cloned[i].clone() : null;
        }
        return cloned;
    }
}
