package at.koopro.wizardsandbeasts.command.debug;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugModeServiceTest {

    @BeforeEach
    @AfterEach
    void reset() {
        DebugModeService.resetForTests();
    }

    @Test
    void global_toggle_two_cycles_back_to_off() {
        assertFalse(DebugModeService.isGlobalEnabled());
        assertTrue(DebugModeService.toggleGlobal());
        assertTrue(DebugModeService.isGlobalEnabled());
        assertFalse(DebugModeService.toggleGlobal());
        assertFalse(DebugModeService.isGlobalEnabled());
    }

    @Test
    void player_uuid_toggle_toggles_membership() {
        UUID id = UUID.randomUUID();
        assertTrue(DebugModeService.togglePlayerUuid(id));
        assertFalse(DebugModeService.togglePlayerUuid(id));
        assertTrue(DebugModeService.togglePlayerUuid(id));
    }
}
