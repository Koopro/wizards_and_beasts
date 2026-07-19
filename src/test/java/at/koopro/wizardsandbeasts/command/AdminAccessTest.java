package at.koopro.wizardsandbeasts.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The admin rule. This decides who can use every {@code /wandb} administrative command and change module
 * state, so each branch is pinned — including the two that exist to stop a server locking itself out.
 */
class AdminAccessTest {

    private static final UUID OWNER = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID SOMEONE_ELSE = UUID.fromString("00000000-0000-0000-0000-00000000dead");

    // ── unconfigured: behave exactly as before ──

    @Test
    void withNoListConfiguredOperatorPermissionStillDecides() {
        assertTrue(AdminAccess.decide(SOMEONE_ELSE, List.of(), true));
        assertFalse(AdminAccess.decide(SOMEONE_ELSE, List.of(), false));
    }

    @Test
    void aListOfOnlyJunkCountsAsUnconfigured() {
        // Otherwise a typo would silently lock every operator out of module administration.
        List<String> junk = List.of("", "   ", "not-a-uuid");
        assertTrue(AdminAccess.decide(SOMEONE_ELSE, junk, true));
        assertFalse(AdminAccess.decide(SOMEONE_ELSE, junk, false));
    }

    // ── configured: a closed allow-list ──

    @Test
    void theListedUuidIsAllowedEvenWithoutOperatorPermission() {
        assertTrue(AdminAccess.decide(OWNER, List.of(OWNER.toString()), false));
    }

    @Test
    void anOperatorNotOnTheListIsRefused() {
        assertFalse(AdminAccess.decide(SOMEONE_ELSE, List.of(OWNER.toString()), true),
                "a configured list is closed — operator permission does not override it");
    }

    @Test
    void oneValidEntryClosesTheListEvenAlongsideJunk() {
        List<String> mixed = List.of("nonsense", OWNER.toString(), "");
        assertTrue(AdminAccess.decide(OWNER, mixed, false));
        assertFalse(AdminAccess.decide(SOMEONE_ELSE, mixed, true));
    }

    @Test
    void severalUuidsMayBeListed() {
        List<String> both = List.of(OWNER.toString(), SOMEONE_ELSE.toString());
        assertTrue(AdminAccess.decide(OWNER, both, false));
        assertTrue(AdminAccess.decide(SOMEONE_ELSE, both, false));
        assertFalse(AdminAccess.decide(UUID.randomUUID(), both, true));
    }

    @Test
    void uuidMatchingIsCaseInsensitive() {
        assertTrue(AdminAccess.decide(OWNER, List.of(OWNER.toString().toUpperCase()), false));
    }

    @Test
    void surroundingWhitespaceIsTolerated() {
        assertTrue(AdminAccess.decide(OWNER, List.of("  " + OWNER + "  "), false));
    }
}
