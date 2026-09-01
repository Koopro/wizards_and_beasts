package at.koopro.wizardsandbeasts.floo;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Floo registry's lifecycle rules — the ones a player hits by naming a fireplace, renaming it, or
 * breaking it.
 *
 * <p>The invariant these assertions exist to protect is <b>one block, one address</b>. Renaming used
 * to break it silently and in the worst possible way: the fireplace's own block entity accepted the
 * new name while the network kept the old row pointing at the same block, so the fireplace agreed it
 * had been renamed, the destination list showed it twice, and the old name still worked. Nothing
 * about that is visible from either side on its own, which is exactly why it needs a test.
 */
class FlooNetworkRegistrationTest {

    private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");
    private static final Identifier NETHER = Identifier.parse("minecraft:the_nether");

    private FlooNetworkManager manager;

    @BeforeEach
    void freshNetwork() {
        manager = new FlooNetworkManager();
    }

    @Test
    void registeringAFireplaceMakesItReachable() {
        assertTrue(manager.register("The Burrow", OVERWORLD, new BlockPos(10, 64, 10), true).ok());

        FlooRegistryEntry entry = manager.getEntry("The Burrow");
        assertNotNull(entry);
        assertEquals("The Burrow", entry.networkAddress());
        assertEquals(new BlockPos(10, 64, 10), entry.blockPos());
    }

    @Test
    void addressLookupIsCaseInsensitive_soPlayersNeedNotMatchCapitals() {
        manager.register("The Burrow", OVERWORLD, new BlockPos(10, 64, 10), true);
        assertNotNull(manager.getEntry("the burrow"));
        assertNotNull(manager.getEntry("THE BURROW"));
    }

    @Test
    void renamingAFireplaceRetiresItsOldAddress() {
        BlockPos pos = new BlockPos(10, 64, 10);
        manager.register("The Burrow", OVERWORLD, pos, true);
        assertTrue(manager.register("Grimmauld Place", OVERWORLD, pos, true).ok());

        assertNull(manager.getEntry("The Burrow"),
                "the old name must stop answering — it used to keep working forever");
        assertNotNull(manager.getEntry("Grimmauld Place"));
    }

    @Test
    void renamingLeavesExactlyOneEntry_notAGhostAlongsideIt() {
        BlockPos pos = new BlockPos(10, 64, 10);
        manager.register("The Burrow", OVERWORLD, pos, true);
        manager.register("Grimmauld Place", OVERWORLD, pos, true);

        List<FlooRegistryEntry> all = manager.getAllEntries();
        assertEquals(1, all.size(), "a renamed fireplace listed itself twice in every destination list");
        assertEquals("Grimmauld Place", all.get(0).networkAddress());
    }

    @Test
    void anAddressTakenByADifferentFireplaceIsRefused_andChangesNothing() {
        manager.register("The Burrow", OVERWORLD, new BlockPos(10, 64, 10), true);

        assertEquals(FlooNetworkManager.RegisterResult.ADDRESS_TAKEN,
                manager.register("The Burrow", OVERWORLD, new BlockPos(99, 64, 99), true),
                "two fireplaces must not share one address");

        FlooRegistryEntry entry = manager.getEntry("The Burrow");
        assertNotNull(entry);
        assertEquals(new BlockPos(10, 64, 10), entry.blockPos(),
                "the refused registration must not have moved the existing one");
        assertEquals(1, manager.getAllEntries().size());
    }

    @Test
    void reRegisteringTheSameBlockUnderTheSameNameIsAllowed_andIsNotAClash() {
        BlockPos pos = new BlockPos(10, 64, 10);
        manager.register("The Burrow", OVERWORLD, pos, true);

        assertTrue(manager.register("The Burrow", OVERWORLD, pos, false).ok(),
                "refreshing a fireplace's own registration is not a name collision with itself");
        assertEquals(1, manager.getAllEntries().size());
        assertFalse(manager.getAllEntries().get(0).isPublic(), "the refresh must apply the new visibility");
    }

    @Test
    void samePositionInADifferentDimensionIsADifferentFireplace() {
        BlockPos pos = new BlockPos(10, 64, 10);
        manager.register("Overworld Hearth", OVERWORLD, pos, true);
        manager.register("Nether Hearth", NETHER, pos, true);

        assertEquals(2, manager.getAllEntries().size(),
                "coordinates repeat across dimensions; a purge keyed on position alone would eat both");
        assertNotNull(manager.getEntry("Overworld Hearth"));
        assertNotNull(manager.getEntry("Nether Hearth"));
    }

    @Test
    void breakingAFireplacePurgesItByPosition() {
        BlockPos pos = new BlockPos(10, 64, 10);
        manager.register("The Burrow", OVERWORLD, pos, true);

        assertEquals("The Burrow", manager.unregisterByPos(OVERWORLD, pos));
        assertNull(manager.getEntry("The Burrow"));
        assertTrue(manager.getAllEntries().isEmpty());
    }

    @Test
    void purgingAPositionThatWasNeverRegistered_isANoOp() {
        manager.register("The Burrow", OVERWORLD, new BlockPos(10, 64, 10), true);
        assertNull(manager.unregisterByPos(OVERWORLD, new BlockPos(0, 0, 0)));
        assertEquals(1, manager.getAllEntries().size());
    }

    @Test
    void sealingADestinationKeepsItListedButRefusesIt() {
        manager.register("The Burrow", OVERWORLD, new BlockPos(10, 64, 10), true);
        manager.setEnabled("The Burrow", false);

        FlooRegistryEntry entry = manager.getEntry("The Burrow");
        assertNotNull(entry, "a sealed destination stays in the list so it can be unsealed");
        assertFalse(entry.isEnabled());
    }

    @Test
    void surroundingWhitespaceInAnAddressIsIgnored() {
        manager.register("  The Burrow  ", OVERWORLD, new BlockPos(10, 64, 10), true);

        FlooRegistryEntry entry = manager.getEntry("The Burrow");
        assertNotNull(entry);
        assertEquals("The Burrow", entry.networkAddress(),
                "the stored name is trimmed, so it displays the way it was meant to");
    }

    /**
     * The manager is the one door into the network, so it validates rather than trusting its caller.
     *
     * <p>An empty address used to register happily: it keyed on {@code ""}, appeared in every
     * destination list as a nameless row, and could not be unregistered by anyone who did not already
     * know it was there.
     */
    @Test
    void anInvalidAddressIsRefusedByTheManagerNotJustByTheCommand() {
        assertEquals(FlooNetworkManager.RegisterResult.INVALID_ADDRESS,
                manager.register("   ", OVERWORLD, new BlockPos(10, 64, 10), true));
        assertEquals(FlooNetworkManager.RegisterResult.INVALID_ADDRESS,
                manager.register("§cSneaky", OVERWORLD, new BlockPos(10, 64, 10), true));
        assertTrue(manager.getAllEntries().isEmpty(), "a refused registration must store nothing");
    }

    @Test
    void aStoredAddressKeepsItsTypedCaseButLosesItsPadding() {
        manager.register("   The Burrow   ", OVERWORLD, new BlockPos(10, 64, 10), true);
        assertEquals("The Burrow", manager.getAllEntries().get(0).networkAddress());
    }
}
