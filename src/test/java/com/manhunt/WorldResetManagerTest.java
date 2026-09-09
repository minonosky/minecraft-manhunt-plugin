package com.manhunt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorldResetManagerTest {

    @Test
    void parsesNumericSeeds() {
        assertEquals(123456789L, WorldResetManager.parseSeed("123456789"));
        assertEquals(-42L, WorldResetManager.parseSeed("-42"));
    }

    @Test
    void generatesRandomSeedWhenArgumentIsMissing() {
        assertNotNull(WorldResetManager.parseSeed(null));
        assertNotNull(WorldResetManager.parseSeed(""));
    }

    @Test
    void rejectsInvalidSeed() {
        assertNull(WorldResetManager.parseSeed("not-a-seed"));
    }
}
