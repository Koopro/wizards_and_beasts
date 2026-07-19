package at.koopro.wizardsandbeasts.module;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The module-admin rule. This decides who can switch features of the mod on and off, so each branch is
 * pinned — including the two that exist to stop a server locking itself out.
 */
class ModuleAdminAccessTest {

    private static final UUID OWNER = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID SOMEONE_ELSE = UUID.fromString("00000000-0000-0000-0000-00000000dead");

    // ── unconfigured: behave exactly as before ──

    @Test
    void withNoListConfiguredOperatorPermissionStillDecides() {
        assertTrue(ModuleAdminAccess.decide(SOMEONE_ELSE, List.of(), true));
        assertFalse(ModuleAdminAccess.decide(SOMEONE_ELSE, List.of(), false));
    }

    @Test
    void aListOfOnlyJunkCountsAsUnconfigured() {
        // Otherwise a typo would silently lock every operator out of module administration.
        List<String> junk = List.of("", "   ", "not-a-uuid");
        assertTrue(ModuleAdminAccess.decide(SOMEONE_ELSE, junk, true));
        assertFalse(ModuleAdminAccess.decide(SOMEONE_ELSE, junk, false));
    }

    // ── configured: a closed allow-list ──

    @Test
    void theListedUuidIsAllowedEvenWithoutOperatorPermission() {
        assertTrue(ModuleAdminAccess.decide(OWNER, List.of(OWNER.toString()), false));
    }

    @Test
    void anOperatorNotOnTheListIsRefused() {
        assertFalse(ModuleAdminAccess.decide(SOMEONE_ELSE, List.of(OWNER.toString()), true),
                "a configured list is closed — operator permission does not override it");
    }

    @Test
    void oneValidEntryClosesTheListEvenAlongsideJunk() {
        List<String> mixed = List.of("nonsense", OWNER.toString(), "");
        assertTrue(ModuleAdminAccess.decide(OWNER, mixed, false));
        assertFalse(ModuleAdminAccess.decide(SOMEONE_ELSE, mixed, true));
    }

    @Test
    void severalUuidsMayBeListed() {
        List<String> both = List.of(OWNER.toString(), SOMEONE_ELSE.toString());
        assertTrue(ModuleAdminAccess.decide(OWNER, both, false));
        assertTrue(ModuleAdminAccess.decide(SOMEONE_ELSE, both, false));
        assertFalse(ModuleAdminAccess.decide(UUID.randomUUID(), both, true));
    }

    @Test
    void uuidMatchingIsCaseInsensitive() {
        assertTrue(ModuleAdminAccess.decide(OWNER, List.of(OWNER.toString().toUpperCase()), false));
    }

    @Test
    void surroundingWhitespaceIsTolerated() {
        assertTrue(ModuleAdminAccess.decide(OWNER, List.of("  " + OWNER + "  "), false));
    }
}
