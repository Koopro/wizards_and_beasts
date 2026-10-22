package at.koopro.wizardsandbeasts.artefact.cabinet;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CabinetPairsTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"));

    private static final GlobalPos A = GlobalPos.of(OVERWORLD, new BlockPos(0, 64, 0));
    private static final GlobalPos B = GlobalPos.of(NETHER, new BlockPos(10, 70, -4));
    private static final GlobalPos C = GlobalPos.of(OVERWORLD, new BlockPos(5, 64, 5));
    private static final UUID OWNER = UUID.randomUUID();

    @Test
    void linkJoinsBothEndsAndStartsPrivate() {
        CabinetPairs pairs = new CabinetPairs();
        CabinetPairs.Pair pair = pairs.link(A, B, OWNER);
        assertNotNull(pair);
        assertFalse(pair.shared());
        assertEquals(B, pairs.pairAt(A).partnerOf(A));
        assertEquals(A, pairs.pairAt(B).partnerOf(B));
        assertNull(pair.partnerOf(C));
        assertTrue(pairs.isDirty());
    }

    @Test
    void linkNeverBreaksAnExistingPairOrJoinsACabinetToItself() {
        CabinetPairs pairs = new CabinetPairs();
        pairs.link(A, B, OWNER);
        assertNull(pairs.link(A, C, OWNER), "A was taken from its pair");
        assertNull(pairs.link(C, B, OWNER), "B was taken from its pair");
        assertNull(pairs.link(C, C, OWNER), "a cabinet linked to itself");
        assertEquals(1, pairs.size());
    }

    @Test
    void severFreesBothEnds() {
        CabinetPairs pairs = new CabinetPairs();
        pairs.link(A, B, OWNER);
        assertNotNull(pairs.sever(B));
        assertNull(pairs.pairAt(A));
        assertNull(pairs.pairAt(B));
        assertNull(pairs.sever(A), "severing twice found a pair");
        assertNotNull(pairs.link(A, C, OWNER), "a freed cabinet could not be linked again");
    }

    @Test
    void sharingIsSeenFromEitherEnd() {
        CabinetPairs pairs = new CabinetPairs();
        pairs.link(A, B, OWNER);
        assertTrue(pairs.setShared(A, true).shared());
        assertTrue(pairs.pairAt(B).shared());
        assertNull(pairs.setShared(C, true));
    }

    @Test
    void saveRoundTripsAndDropsADoubleBookedCabinet() {
        CabinetPairs.Pair good = new CabinetPairs.Pair(UUID.randomUUID(), A, B, OWNER, true);
        CabinetPairs.Pair clash = new CabinetPairs.Pair(UUID.randomUUID(), C, A, OWNER, false);
        var json = CabinetPairs.Pair.CODEC.listOf().encodeStart(JsonOps.INSTANCE, List.of(good, clash)).getOrThrow();
        var wrapped = new com.google.gson.JsonObject();
        wrapped.add("pairs", json);

        CabinetPairs loaded = CabinetPairs.CODEC.parse(JsonOps.INSTANCE, wrapped).getOrThrow();
        assertEquals(1, loaded.size(), "A answered for two links");
        assertEquals(good, loaded.pairAt(A));
        assertNull(loaded.pairAt(C));

        var again = CabinetPairs.CODEC.encodeStart(JsonOps.INSTANCE, loaded).getOrThrow();
        assertEquals(List.of(good), CabinetPairs.CODEC.parse(JsonOps.INSTANCE, again).getOrThrow().snapshot());
    }

    @Test
    void onlyThePocketRealmIsRefused() {
        assertTrue(CabinetService.refusesDimension(at.koopro.wizardsandbeasts.registry.ModDimensions.EXTENSION_REALM));
        assertFalse(CabinetService.refusesDimension(OVERWORLD));
        assertFalse(CabinetService.refusesDimension(NETHER));
    }

    @Test
    void onlyTheOwnerASharedPairOrAnOperatorStepsThrough() {
        assertTrue(CabinetService.mayUse(true, false, false));
        assertTrue(CabinetService.mayUse(false, true, false));
        assertTrue(CabinetService.mayUse(false, false, true));
        assertFalse(CabinetService.mayUse(false, false, false));
    }
}
