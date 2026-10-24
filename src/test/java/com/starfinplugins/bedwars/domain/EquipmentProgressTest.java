package com.starfinplugins.bedwars.domain;

import com.starfinplugins.bedwars.domain.match.EquipmentProgress;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EquipmentProgressTest {
    @Test public void equipmentLevelsSurviveArmorAndDowngradeWeaponsIndependently() {
        EquipmentProgress state = new EquipmentProgress();
        assertTrue(state.improveArmor(3));
        assertTrue(state.improveSword(3));
        assertTrue(state.improveAxe(2));
        assertTrue(state.improvePickaxe(1));
        assertFalse(state.improveSword(2));
        state.downgrade(0, 0);
        assertEquals(3, state.getArmor());
        assertEquals(2, state.getSword());
        assertEquals(1, state.getAxe());
        assertEquals(0, state.getPickaxe());
        state.downgrade(0, 0);
        assertEquals(1, state.getSword());
        assertEquals(0, state.getAxe());
        assertEquals(0, state.getPickaxe());
        state.downgrade(0, 0);
        assertEquals(0, state.getSword());
        assertEquals(0, state.getAxe());
    }

    @Test public void neverCreatesToolsThatWereNotPurchased() {
        EquipmentProgress state = new EquipmentProgress();
        state.improveSword(3);
        state.downgrade(1, 0);
        assertEquals(2, state.getSword());
        assertEquals(-1, state.getAxe());
        assertEquals(-1, state.getPickaxe());
    }
}
