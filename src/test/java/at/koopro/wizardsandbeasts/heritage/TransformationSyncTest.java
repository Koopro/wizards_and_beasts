package at.koopro.wizardsandbeasts.heritage;

import at.koopro.wizardsandbeasts.network.heritage.HeritageIdentitySyncS2CPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the property that {@code transformationState} reaches other players.
 *
 * <p>It used to travel only on {@code HeritageDataSyncS2CPayload}, which targets one player, so a
 * transformed werewolf knew they were a wolf and nobody watching them did. That is the exact class of
 * bug single-player testing cannot reach: everything looks right with one player in the world, and the
 * second player sees nothing. These assertions are cheap and the failure is invisible, which is the
 * whole argument for having them.
 */
class TransformationSyncTest {

    private static final Path WEREWOLF = Path.of("src", "main", "java", "at", "koopro",
            "wizardsandbeasts", "event", "heritage", "WerewolfMoonHandler.java");
    private static final Path OBSCURIAL = Path.of("src", "main", "java", "at", "koopro",
            "wizardsandbeasts", "event", "heritage", "ObscurialHeritageHandler.java");
    private static final Path API = Path.of("src", "main", "java", "at", "koopro",
            "wizardsandbeasts", "heritage", "HeritageAPI.java");

    private static String read(Path path) throws IOException {
        return Files.readString(path);
    }

    @Test
    void theIdentityPayloadCarriesTransformationStateAcrossTheWire() {
        HeritageIdentitySyncS2CPayload original = new HeritageIdentitySyncS2CPayload(
                UUID.randomUUID(), "werewolf", "bitten", TransformationState.TRANSFORMED.name());

        ByteBuf buf = Unpooled.buffer();
        try {
            HeritageIdentitySyncS2CPayload.STREAM_CODEC.encode(buf, original);
            HeritageIdentitySyncS2CPayload decoded =
                    HeritageIdentitySyncS2CPayload.STREAM_CODEC.decode(buf);

            assertEquals(original.playerUUID(), decoded.playerUUID());
            assertEquals(original.heritageId(), decoded.heritageId());
            assertEquals(original.variantId(), decoded.variantId());
            assertEquals(original.transformationState(), decoded.transformationState(),
                    "transformation state did not survive the wire — remote players would render "
                            + "everyone as untransformed");
        } finally {
            buf.release();
        }
    }

    /**
     * Adding the field is only half the fix. Nothing re-broadcasts on its own, so a site that changes
     * the state and syncs only to the owning player leaves every observer stale until the next join or
     * respawn — which looks like it works, for a while.
     */
    @Test
    void everyTransformationSiteBroadcastsRatherThanSyncingOnlyToItsOwner() throws IOException {
        for (Path source : new Path[]{WEREWOLF, OBSCURIAL}) {
            String text = read(source);
            int changes = countOccurrences(text, "setTransformationState(");
            int broadcasts = countOccurrences(text, "HeritageAPI.syncTransformation(");
            assertTrue(broadcasts >= changes,
                    source.getFileName() + " changes transformation state " + changes + " time(s) but "
                            + "broadcasts " + broadcasts + " time(s) — an observer would keep seeing the "
                            + "old shape");
        }
    }

    @Test
    void theHelperSendsBothHalves() throws IOException {
        String api = read(API);
        int start = api.indexOf("public static void syncTransformation(");
        assertTrue(start > 0, "HeritageAPI.syncTransformation is gone — the transformation sites have "
                + "nothing to call and will drift back to owner-only sends");
        String body = api.substring(start, api.indexOf('}', start));

        assertTrue(body.contains("HeritageDataSyncS2CPayload.syncToPlayer"),
                "the owning player stopped being told, so their own HUD goes stale");
        assertTrue(body.contains("HeritageIdentitySyncS2CPayload.syncToTracking"),
                "observers stopped being told — this is the original bug, restored");
    }

    /**
     * The periodic Obscurial HUD tick must stay owner-only. It fires on an interval and nothing has
     * changed shape, so routing it through the broadcast would put identity packets on a timer for
     * every tracker.
     */
    @Test
    void thePeriodicResourceSyncStaysOwnerOnly() throws IOException {
        String text = read(OBSCURIAL);
        int marker = text.indexOf("FLAG_LAST_RESOURCE_SYNC_TICK, String.valueOf(gameTime)");
        assertTrue(marker > 0, "the periodic resource sync moved; re-check which sync it uses");
        String following = text.substring(marker, Math.min(text.length(), marker + 600));
        assertFalse(following.contains("HeritageAPI.syncTransformation("),
                "the periodic HUD sync now broadcasts identity to every tracker on a timer");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
