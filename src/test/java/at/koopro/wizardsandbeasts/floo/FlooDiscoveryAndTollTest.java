package at.koopro.wizardsandbeasts.floo;

import at.koopro.wizardsandbeasts.travel.profile.TravelActions;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Floo discovery, tolls and wards: the address book lists only what the player has found, a spoken name still reaches
 * any public hearth, a toll is never charged to the keeper or a misfire, and every door that can move a traveller
 * honours the new rules.
 */
class FlooDiscoveryAndTollTest {

    private static final Path HANDLER = Path.of("src/main/java/at/koopro/wizardsandbeasts/floo/FlooTravelHandler.java");

    @Test
    void theAddressBookListsWhatThePlayerHasFound() {
        // landmark, owner, visited, admin
        assertTrue(FlooAccess.isListed(true, false, false, false), "a landmark is known to all");
        assertTrue(FlooAccess.isListed(false, true, false, false), "your own hearth");
        assertTrue(FlooAccess.isListed(false, false, true, false), "somewhere you have been");
        assertTrue(FlooAccess.isListed(false, false, false, true), "an administrator sees the network");
        assertFalse(FlooAccess.isListed(false, false, false, false), "someone's public hearth you never found");
        // Reaching by name is unchanged: public is enough, which is word of mouth.
        assertTrue(FlooAccess.mayReach(true, false, false, false));
    }

    @Test
    void aLandmarkIsPublicAndOwnedByNobody() {
        Identifier overworld = Identifier.parse("minecraft:overworld");
        assertTrue(FlooRegistryEntry.unowned("Diagon Alley", overworld, BlockPos.ZERO, true).isLandmark());
        assertFalse(FlooRegistryEntry.unowned("The Shed", overworld, BlockPos.ZERO, false).isLandmark());
        FlooRegistryEntry owned = new FlooRegistryEntry("The Burrow", overworld, BlockPos.ZERO, true, true,
                Optional.of(UUID.randomUUID()));
        assertFalse(owned.isLandmark(), "a player's public hearth has to be found");
    }

    @Test
    void tollsAreBoundedAndReadOldSaves() {
        Identifier overworld = Identifier.parse("minecraft:overworld");
        FlooRegistryEntry entry = new FlooRegistryEntry("The Burrow", overworld, BlockPos.ZERO, true, true,
                Optional.of(UUID.randomUUID()));
        assertEquals(0, entry.tollKnuts());
        assertEquals(FlooRegistryEntry.MAX_TOLL_KNUTS, entry.withToll(10_000).tollKnuts());
        assertEquals(0, entry.withToll(-5).tollKnuts());
        FlooRegistryEntry tolled = entry.withToll(58);
        assertEquals(tolled, FlooRegistryEntry.CODEC.parse(JsonOps.INSTANCE,
                FlooRegistryEntry.CODEC.encodeStart(JsonOps.INSTANCE, tolled).getOrThrow()).getOrThrow());
        JsonObject old = JsonParser.parseString(
                "{\"address\":\"Old\",\"dimension\":\"minecraft:overworld\",\"pos\":[0,64,0]}").getAsJsonObject();
        assertEquals(0, FlooRegistryEntry.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow().tollKnuts(),
                "a hearth saved before tolls is free");
    }

    @Test
    void whoPaysAToll() {
        // toll, keeper, admin, billed
        assertTrue(FlooTolls.owes(29, false, false, true));
        assertFalse(FlooTolls.owes(29, true, false, true), "the keeper travels free");
        assertFalse(FlooTolls.owes(29, false, true, true), "administrators travel free");
        assertFalse(FlooTolls.owes(29, false, false, false), "without Gringotts a hearth is a favour");
        assertFalse(FlooTolls.owes(0, false, false, true));
    }

    @Test
    void aKeeperCyclesTheirTollThroughSicklesAndBackToFree() {
        assertEquals(29, TravelActions.nextToll(0));
        assertEquals(58, TravelActions.nextToll(29));
        assertEquals(87, TravelActions.nextToll(58));
        assertEquals(0, TravelActions.nextToll(87));
        assertEquals(29, TravelActions.nextToll(5), "an odd toll snaps to the next Sickle");
    }

    @Test
    void theTravelPathHonoursWardsTollsAndTheBook() throws IOException {
        String handler = Files.readString(HANDLER);
        int warded = handler.split("isWarded\\(player, destLevel, dest\\)", -1).length - 1;
        assertEquals(2, warded, "the ward check must run at the start and again at the commit");
        int toll = handler.indexOf("FlooTolls.charge(player, dest)");
        int powder = handler.indexOf("if (!consumeOnePowder(player))");
        int teleport = handler.indexOf("player.teleport(transition);");
        assertTrue(toll > 0 && toll < powder && powder < teleport,
                "toll before powder before teleport, so a refused hop costs neither");
        assertTrue(handler.contains("wasMisfire ? 0L : FlooTolls.charge"), "a misfire owes no toll");
        assertTrue(handler.contains("FlooTolls.refund(player, toll)"), "a toll taken for a failed hop comes back");
        assertTrue(handler.contains("FlooAccess.isListed(player, entry)"),
                "a picked row must come from the address book, not merely the network");
        assertTrue(handler.contains("FlooAccess.reachableBy(player, manager)"),
                "a misfire can still throw a traveller into a grate they never found");
    }
}
