package at.koopro.wizardsandbeasts.floo;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The awkward cases: the registry under abuse, the visibility truth table, and the travel log's cap.
 *
 * <p>Complements {@link FlooNetworkRegistrationTest}, which owns the one-block-one-address lifecycle.
 * What is here is the part that only shows up when something goes wrong — a destination that stops
 * existing mid-journey, a log that never stops growing, a private hearth asked about by four
 * different kinds of player.
 */
class FlooEdgeCaseTest {

    private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");
    private static final Identifier NETHER = Identifier.parse("minecraft:the_nether");

    private FlooNetworkManager manager;

    @BeforeEach
    void freshNetwork() {
        manager = new FlooNetworkManager();
    }

    // ── register / get / unregisterByPos round trip ────────────────────────────────────────

    @Test
    void registerGetUnregisterByPosRoundTrips() {
        BlockPos pos = new BlockPos(12, 70, -30);
        assertTrue(manager.register("The Burrow", OVERWORLD, pos, true).ok());

        FlooRegistryEntry found = manager.getEntry("The Burrow");
        assertNotNull(found);
        assertEquals(pos, found.blockPos());
        assertEquals(OVERWORLD, found.dimension());

        assertEquals("The Burrow", manager.unregisterByPos(OVERWORLD, pos));
        assertNull(manager.getEntry("The Burrow"),
                "a purged position must take its address with it");
        assertTrue(manager.getAllEntries().isEmpty());
    }

    @Test
    void findByPosAndGetEntryAgreeOnTheSameHearth() {
        BlockPos pos = new BlockPos(1, 2, 3);
        manager.register("Spinner's End", OVERWORLD, pos, false);

        FlooRegistryEntry byName = manager.getEntry("spinner's end");
        FlooRegistryEntry byPos = manager.findByPos(OVERWORLD, pos);
        assertNotNull(byName);
        assertNotNull(byPos);
        assertEquals(byName, byPos, "the two lookups must not disagree about one hearth");
    }

    @Test
    void findByPosIsDimensionAware() {
        BlockPos pos = new BlockPos(0, 64, 0);
        manager.register("Overworld Hearth", OVERWORLD, pos, true);
        assertNotNull(manager.findByPos(OVERWORLD, pos));
        assertNull(manager.findByPos(NETHER, pos),
                "the same coordinates in another dimension are another place");
    }

    // ── case 4: one entry per block position ───────────────────────────────────────────────

    @Test
    void reRegisteringOnePositionUnderANewNameReplacesRatherThanDuplicates() {
        BlockPos pos = new BlockPos(5, 64, 5);
        manager.register("Old Name", OVERWORLD, pos, true);
        manager.register("New Name", OVERWORLD, pos, true);

        assertEquals(1, manager.getAllEntries().size(), "a rename must leave exactly one entry");
        assertNull(manager.getEntry("Old Name"), "the retired address must stop answering");
        assertNotNull(manager.getEntry("New Name"));
    }

    @Test
    void aRenameKeepsTheOriginalOwner() {
        // The owner is carried through rather than re-read, so holding the name tag is not a way to
        // take somebody's hearth off them.
        BlockPos pos = new BlockPos(5, 64, 5);
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        manager.register("Old Name", OVERWORLD, pos, true, Optional.of(owner));
        manager.register("New Name", OVERWORLD, pos, true, Optional.of(stranger));

        FlooRegistryEntry renamed = manager.getEntry("New Name");
        assertNotNull(renamed);
        assertTrue(renamed.isOwnedBy(owner), "a rename must not transfer ownership");
        assertFalse(renamed.isOwnedBy(stranger));
    }

    // ── address normalisation collisions ───────────────────────────────────────────────────

    @Test
    void twoFireplacesCannotShareOneNormalisedAddress() {
        assertTrue(manager.register("Diagon Alley", OVERWORLD, new BlockPos(0, 64, 0), true).ok());

        FlooNetworkManager.RegisterResult clash =
                manager.register("  diagon   ALLEY  ".replaceAll("\\s+", " ").strip(),
                        OVERWORLD, new BlockPos(9, 64, 9), true);
        assertEquals(FlooNetworkManager.RegisterResult.ADDRESS_TAKEN, clash);
        assertEquals(1, manager.getAllEntries().size());
    }

    @Test
    void aCollisionChangesNothingAboutTheEntryThatWonIt() {
        BlockPos first = new BlockPos(0, 64, 0);
        manager.register("Diagon Alley", OVERWORLD, first, true);
        manager.register("DIAGON ALLEY", OVERWORLD, new BlockPos(9, 64, 9), false);

        FlooRegistryEntry survivor = manager.getEntry("diagon alley");
        assertNotNull(survivor);
        assertEquals(first, survivor.blockPos(), "the refused registration must not move the winner");
        assertTrue(survivor.isPublic(), "nor change its visibility");
    }

    // ── visibility truth table ─────────────────────────────────────────────────────────────

    @Test
    void aPublicHearthIsVisibleToEveryone() {
        assertTrue(FlooAccess.mayReach(true, false, false, false));
    }

    @Test
    void aPrivateHearthIsInvisibleToAStranger() {
        assertFalse(FlooAccess.mayReach(false, false, false, false),
                "a private hearth you do not own and have never visited must not be listed");
    }

    @Test
    void aPrivateHearthIsVisibleToItsOwner() {
        assertTrue(FlooAccess.mayReach(false, true, false, false));
    }

    @Test
    void aPrivateHearthIsVisibleOnceVisited() {
        assertTrue(FlooAccess.mayReach(false, false, true, false));
    }

    @Test
    void aPrivateHearthIsVisibleToAnAdmin() {
        // An operator who cannot see a hearth cannot moderate it, and the alternative is reading raw
        // saved data to answer a report.
        assertTrue(FlooAccess.mayReach(false, false, false, true));
    }

    @Test
    void everyClauseIsIndependentlySufficient() {
        // Flat disjunction: no clause may gate another. Exhaustive over the four inputs, which is
        // sixteen cases and cheap - the rule is only correct if "not public and nothing else true"
        // is the single false row.
        for (int mask = 0; mask < 16; mask++) {
            boolean isPublic = (mask & 1) != 0;
            boolean owner = (mask & 2) != 0;
            boolean visited = (mask & 4) != 0;
            boolean admin = (mask & 8) != 0;
            boolean expected = isPublic || owner || visited || admin;
            assertEquals(expected, FlooAccess.mayReach(isPublic, owner, visited, admin),
                    "mask " + mask);
        }
    }

    // ── travel log ring buffer ─────────────────────────────────────────────────────────────

    @Test
    void theTravelLogIsCappedAtOneHundredEntries() {
        // Uncapped, this is a save file that grows for the life of a world.
        for (int i = 0; i < 250; i++) {
            manager.logTravel("Wizard" + i, "A", "B");
        }
        assertEquals(100, manager.getRecentLog(1000).size(),
                "the log must not grow past its cap however much is written to it");
    }

    @Test
    void theTravelLogKeepsTheNewestEntriesNotTheOldest() {
        for (int i = 0; i < 150; i++) {
            manager.logTravel("Wizard" + i, "A", "B");
        }
        List<String> recent = manager.getRecentLog(100);
        assertTrue(recent.getLast().contains("Wizard149"), "the last hop must still be in the log");
        assertFalse(recent.getFirst().contains("Wizard0"), "the first hop must have aged out");
    }

    @Test
    void askingForMoreLogThanExistsReturnsWhatThereIs() {
        manager.logTravel("Harry", "Diagon Alley", "The Burrow");
        assertEquals(1, manager.getRecentLog(50).size());
    }

    @Test
    void aFreshNetworkHasAnEmptyLog() {
        assertTrue(manager.getRecentLog(20).isEmpty());
    }

    // ── sealing ────────────────────────────────────────────────────────────────────────────

    @Test
    void sealingAndPublishingAreIndependent() {
        BlockPos pos = new BlockPos(3, 64, 3);
        manager.register("The Burrow", OVERWORLD, pos, false);

        manager.setEnabled("The Burrow", false);
        FlooRegistryEntry sealed = manager.getEntry("The Burrow");
        assertNotNull(sealed);
        assertFalse(sealed.isEnabled());
        assertFalse(sealed.isPublic(), "sealing must not publish a private hearth");

        manager.setPublic("The Burrow", true);
        FlooRegistryEntry published = manager.getEntry("The Burrow");
        assertNotNull(published);
        assertTrue(published.isPublic());
        assertFalse(published.isEnabled(), "publishing must not unseal a sealed line");
    }
}
