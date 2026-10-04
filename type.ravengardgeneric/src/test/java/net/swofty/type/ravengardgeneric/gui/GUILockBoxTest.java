package net.swofty.type.ravengardgeneric.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GUILockBoxTest {
    @Test
    void cellsFillTheEightColumnsThePanelDrawsAndSkipTheBorderColumn() {
        assertEquals(1, GUILockBox.chestSlot(0));
        assertEquals(8, GUILockBox.chestSlot(7));
        assertEquals(10, GUILockBox.chestSlot(8));
        assertEquals(32, GUILockBox.chestSlot(28));
        assertEquals(44, GUILockBox.chestSlot(39));
        assertEquals(1, GUILockBox.chestSlot(40));
        for (int cell = 0; cell < 80; cell++) {
            int slot = GUILockBox.chestSlot(cell);
            assertEquals(true, slot % 9 != 0 && slot < 45, "cell " + cell + " -> " + slot);
        }
    }
}
