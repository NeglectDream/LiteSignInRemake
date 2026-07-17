package studio.trc.bukkit.litesignin.gui;

import org.bukkit.inventory.ItemStack;

import studio.trc.bukkit.litesignin.util.SignInDate;

/** Immutable metadata for one sign-in GUI button. */
public final class SignInGUIColumn
{
    private final int key;
    private final ItemStack item;
    private final boolean keyButton;
    private final SignInDate date;
    private final KeyType keyType;
    private final String buttonName;

    /** Creates a date/key button. */
    public SignInGUIColumn(ItemStack item, int key, SignInDate date, KeyType keyType) {
        this.key = key;
        this.item = item != null ? item.clone() : null;
        this.date = date != null ? date.copy() : null;
        this.keyType = keyType;
        this.buttonName = null;
        this.keyButton = true;
    }

    /** Creates a navigation or query button. */
    public SignInGUIColumn(ItemStack item, int key, String buttonName) {
        this.key = key;
        this.item = item != null ? item.clone() : null;
        this.date = null;
        this.keyType = null;
        this.buttonName = buttonName;
        this.keyButton = false;
    }

    public int getKeyPostion() {
        return key;
    }

    public boolean isKey() {
        return keyButton;
    }

    public SignInDate getDate() {
        return date != null ? date.copy() : null;
    }

    public String getButtonName() {
        return buttonName;
    }

    public ItemStack getItemStack() {
        return item != null ? item.clone() : null;
    }

    public KeyType getKeyType() {
        return keyType;
    }

    SignInGUIColumn copy() {
        if (keyButton) {
            return new SignInGUIColumn(item, key, date, keyType);
        }
        return new SignInGUIColumn(item, key, buttonName);
    }

    public enum KeyType {
        ALREADY_SIGNIN("Already-SignIn"),
        COMMING_SOON("Comming-Soon"),
        NOTHING_SIGNIN("Nothing-SignIn"),
        MISSED_SIGNIN("Missed-SignIn");

        private final String sectionName;

        KeyType(String sectionName) {
            this.sectionName = sectionName;
        }

        public String getSectionName() {
            return sectionName;
        }
    }
}
