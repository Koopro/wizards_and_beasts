package at.koopro.wizardsandbeasts.stats;

import at.koopro.wizardsandbeasts.network.stats.PlayerStatsSyncPayload;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the property the stat block was reshaped for: that adding a stat is one enum constant.
 *
 * <p>Every assertion here is driven off {@link PlayerStat#values()} rather than naming the five stats
 * that exist today, so a sixth constant is covered the moment it is declared. That is the point — the
 * shape this replaced had the stat list written out in the record, its codec and the sync payload's
 * positional ints, and the only thing that noticed a mismatch was a player with wrong numbers on screen.
 */
class PlayerStatsExtensibilityTest {

    /** A block with a distinct, recognisable value in every stat, derived ones included. */
    private static PlayerStatsData populated() {
        Map<PlayerStat, Integer> values = new EnumMap<>(PlayerStat.class);
        Map<PlayerStat, Float> training = new EnumMap<>(PlayerStat.class);
        int n = 1;
        for (PlayerStat stat : PlayerStat.values()) {
            values.put(stat, n * 7 % 100);
            if (stat.isTrainable()) {
                training.put(stat, n * 0.125f);
            }
            n++;
        }
        return new PlayerStatsData(values, true, 42, training);
    }

    @Test
    void wireRoundTripPreservesEveryStat() {
        PlayerStatsData original = populated();
        ByteBuf buf = Unpooled.buffer();
        try {
            PlayerStatsSyncPayload.STREAM_CODEC.encode(buf, new PlayerStatsSyncPayload(original));
            PlayerStatsData decoded = PlayerStatsSyncPayload.STREAM_CODEC.decode(buf).data();

            for (PlayerStat stat : PlayerStat.values()) {
                assertEquals(original.get(stat), decoded.get(stat),
                        stat.getId() + " did not survive the wire — a stat is missing from the payload");
            }
            assertEquals(original.isProdigy(), decoded.isProdigy());
            assertEquals(original.powerGrowthAccumulated(), decoded.powerGrowthAccumulated());
            assertEquals(original.trainingProgress(), decoded.trainingProgress());
        } finally {
            buf.release();
        }
    }

    @Test
    void derivedStatsRideTheWireButAreNeverPersisted() {
        PlayerStatsData original = populated();
        JsonObject encoded = PlayerStatsData.CODEC
                .encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow()
                .getAsJsonObject();
        JsonObject stats = encoded.getAsJsonObject("stats");

        for (PlayerStat stat : PlayerStat.values()) {
            if (stat.isDerived()) {
                assertFalse(stats.has(stat.getId()),
                        stat.getId() + " is derived but was written to disk — the server re-derives it, "
                                + "so a persisted copy is a second source of truth that will drift");
            } else {
                assertTrue(stats.has(stat.getId()),
                        stat.getId() + " is not derived but was not persisted — it would reset on reload");
            }
        }
    }

    @Test
    void codecRoundTripPreservesEveryPersistedStat() {
        PlayerStatsData original = populated();
        var encoded = PlayerStatsData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        PlayerStatsData decoded = PlayerStatsData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        for (PlayerStat stat : PlayerStat.values()) {
            int expected = stat.isDerived() ? 0 : original.get(stat);
            assertEquals(expected, decoded.get(stat), stat.getId() + " did not survive a save/load");
        }
        assertEquals(original.isProdigy(), decoded.isProdigy());
        assertEquals(original.trainingProgress(), decoded.trainingProgress());
    }

    /**
     * Saves written before the map existed carry four flat ints and no {@code stats} object. They have to
     * keep loading: this is an alpha and people are running it against worlds they care about.
     */
    @Test
    void aPreMapSaveStillLoads() {
        JsonObject legacy = JsonParser.parseString("""
                {
                  "power": 31,
                  "precision": 12,
                  "willpower": 7,
                  "reflexes": 44,
                  "is_prodigy": true,
                  "power_growth_accumulated": 9,
                  "training_progress": { "precision": 0.5 }
                }
                """).getAsJsonObject();

        PlayerStatsData loaded = PlayerStatsData.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();

        assertEquals(31, loaded.power(), "a pre-map save lost its POWER");
        assertEquals(12, loaded.precision());
        assertEquals(7, loaded.willpower());
        assertEquals(44, loaded.reflexes());
        assertTrue(loaded.isProdigy());
        assertEquals(9, loaded.powerGrowthAccumulated());
        assertEquals(0.5f, loaded.trainingProgress().get(PlayerStat.PRECISION));
    }

    @Test
    void valuesAreClampedIntoRange() {
        PlayerStatsData data = PlayerStatsData.EMPTY
                .with(PlayerStat.POWER, 5000)
                .with(PlayerStat.PRECISION, -20);
        assertEquals(PlayerStatsData.MAX_VALUE, data.power(), "an out-of-range write was not clamped");
        assertEquals(PlayerStatsData.MIN_VALUE, data.precision());
    }

    @Test
    void emptyStaysEmptyThroughARoundTrip() {
        assertTrue(PlayerStatsData.EMPTY.isEmpty());
        var encoded = PlayerStatsData.CODEC.encodeStart(JsonOps.INSTANCE, PlayerStatsData.EMPTY).getOrThrow();
        PlayerStatsData decoded = PlayerStatsData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertTrue(decoded.isEmpty(),
                "EMPTY stopped round-tripping as empty — initializeStatsForNewPlayer keys its "
                        + "idempotency check off this, so a returning player would be re-rolled");
    }

    @Test
    void everyStatHasAnId() {
        for (PlayerStat stat : PlayerStat.values()) {
            assertFalse(stat.getId().isBlank(), stat.name() + " has no id");
            assertEquals(stat, PlayerStat.fromId(stat.getId()),
                    stat.getId() + " does not resolve back through fromId — the wire and the codec both "
                            + "key on that, so it would silently drop");
        }
    }
}
