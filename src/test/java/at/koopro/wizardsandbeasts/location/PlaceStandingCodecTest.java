package at.koopro.wizardsandbeasts.location;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Character Sheet's place-standing summary: its wire format and the two readings the Reputation page makes. */
class PlaceStandingCodecTest {

    private static PlaceStanding place(String path, int rep, int nextRep, int tier) {
        return new PlaceStanding(Identifier.fromNamespaceAndPath("wizards_and_beasts", path),
                Component.translatable("location.wizards_and_beasts." + path),
                Component.translatable("location.wizards_and_beasts." + path + ".tier." + tier),
                nextRep < 0 ? Component.empty() : Component.translatable("location.wizards_and_beasts." + path + ".tier." + (tier + 1)),
                rep, nextRep, tier, 4, path.equals("knockturn"));
    }

    @Test
    void aListRoundTrips() {
        List<PlaceStanding> sent = List.of(place("gringotts", 40, 100, 1), place("knockturn", 0, 20, 0),
                place("ollivanders", 500, -1, 3));
        ByteBuf buf = Unpooled.buffer();
        PlaceStanding.LIST_CODEC.encode(buf, sent);
        assertEquals(sent, PlaceStanding.LIST_CODEC.decode(buf));
    }

    @Test
    void neverDealtWithAndTheTopTierReadAsSuch() {
        assertFalse(place("knockturn", 0, 20, 0).dealtWith(), "no reputation and the first tier: a stranger");
        assertTrue(place("gringotts", 1, 100, 0).dealtWith());
        assertTrue(place("ollivanders", 500, -1, 3).atTop());
        assertFalse(place("gringotts", 40, 100, 1).atTop());
    }

    @Test
    void anOversizedListIsRefusedRatherThanAllocated() {
        ByteBuf buf = Unpooled.buffer();
        net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(buf, PlaceStanding.MAX_PLACES + 1);
        assertThrows(IllegalArgumentException.class, () -> PlaceStanding.LIST_CODEC.decode(buf));
    }

    @Test
    void theEncoderCapsWhatItSends() {
        List<PlaceStanding> many = new ArrayList<>();
        for (int i = 0; i < PlaceStanding.MAX_PLACES + 5; i++) {
            many.add(place("p" + i, i, 100, 0));
        }
        ByteBuf buf = Unpooled.buffer();
        PlaceStanding.LIST_CODEC.encode(buf, many);
        assertEquals(PlaceStanding.MAX_PLACES, PlaceStanding.LIST_CODEC.decode(buf).size());
    }
}
