package studio.trc.bukkit.litesignin.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import studio.trc.bukkit.litesignin.gui.SignInGUIColumn;

class MenuTest
{
    @Test
    void alreadySignedButtonsKeepOrdinaryActionsWithoutGrantingFailedRewards() {
        assertTrue(Menu.shouldRunKeyActions(SignInGUIColumn.KeyType.ALREADY_SIGNIN, false));
        assertTrue(Menu.shouldRunKeyActions(SignInGUIColumn.KeyType.NOTHING_SIGNIN, true));
        assertTrue(Menu.shouldRunKeyActions(SignInGUIColumn.KeyType.MISSED_SIGNIN, true));

        assertFalse(Menu.shouldRunKeyActions(SignInGUIColumn.KeyType.NOTHING_SIGNIN, false));
        assertFalse(Menu.shouldRunKeyActions(SignInGUIColumn.KeyType.MISSED_SIGNIN, false));
        assertFalse(Menu.shouldRunKeyActions(SignInGUIColumn.KeyType.COMMING_SOON, false));
    }
}
