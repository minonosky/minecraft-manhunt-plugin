package com.manhunt;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManhuntGameTest {

    private final ManhuntGame game = new ManhuntGame();
    private final UUID player = UUID.randomUUID();

    @Test
    void doesNotAddDuplicateRunners() {
        assertTrue(game.addRunner(player));
        assertFalse(game.addRunner(player));
        assertEquals(1, game.getRunners().size());
    }

    @Test
    void movingPlayerToHuntersRemovesRunnerRole() {
        game.addRunner(player);

        assertTrue(game.addHunter(player));

        assertFalse(game.isRunner(player));
        assertTrue(game.isHunter(player));
    }

    @Test
    void movingPlayerToRunnersRemovesHunterRole() {
        game.addHunter(player);

        assertTrue(game.addRunner(player));

        assertTrue(game.isRunner(player));
        assertFalse(game.isHunter(player));
    }

    @Test
    void storesTrackingIndexForHunters() {
        game.addHunter(player);
        game.setTrackingIndex(player, 2);

        assertEquals(2, game.getTrackingIndex(player));
    }

    @Test
    void resetClearsAllState() {
        UUID runner = UUID.randomUUID();
        game.addRunner(runner);
        game.addHunter(player);
        game.setTrackingIndex(player, 3);

        game.reset();

        assertTrue(game.getRunners().isEmpty());
        assertTrue(game.getHunters().isEmpty());
        assertEquals(0, game.getTrackingIndex(player));
    }

    @Test
    void exposedRoleCollectionsCannotBeModified() {
        game.addRunner(player);

        assertThrows(UnsupportedOperationException.class, () -> game.getRunners().clear());
    }

}
