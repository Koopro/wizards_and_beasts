package at.koopro.wizardsandbeasts.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A player's stat block.
 *
 * <p>Stats live in a map keyed by {@link PlayerStat} rather than in a field each. The four-named-field
 * shape this replaced meant a fifth stat touched the record, its codec, the sync payload's positional
 * ints, and every {@code with*} method — a wire-format change to add a number. Everything here is driven
 * off {@link PlayerStat#values()} instead, so a new stat is one enum constant plus its lang key.
 *
 * <p>{@code power()}, {@code precision()} and friends survive as ordinary methods over the map. They read
 * better than {@code get(PlayerStat.POWER)} at the ~34 call sites that use them, and keeping them meant
 * this change touched two files rather than a dozen.
 *
 * <p>Derived stats (currently only {@code KNOWLEDGE}) sit in the same map but are <b>never persisted</b> —
 * the codec skips them and the server re-derives on demand, which is the behaviour the old transport-only
 * {@code knowledge} field had. Folding it in is what makes "add a stat" one constant regardless of kind.
 */
public record PlayerStatsData(
        Map<PlayerStat, Integer> values,
        boolean isProdigy,
        int powerGrowthAccumulated,
        Map<PlayerStat, Float> trainingProgress
) {

    public static final int MIN_VALUE = 0;
    public static final int MAX_VALUE = 100;

    public static final PlayerStatsData EMPTY = new PlayerStatsData(Map.of(), false, 0, Map.of());

    /** Defensive, clamped, and stable in iteration order — an EnumMap sorts by declaration. */
    public PlayerStatsData {
        EnumMap<PlayerStat, Integer> copiedValues = new EnumMap<>(PlayerStat.class);
        values.forEach((stat, value) -> copiedValues.put(stat, clamp(value)));
        values = Collections.unmodifiableMap(copiedValues);

        EnumMap<PlayerStat, Float> copiedTraining = new EnumMap<>(PlayerStat.class);
        trainingProgress.forEach((stat, progress) -> {
            if (stat.isTrainable()) {
                copiedTraining.put(stat, progress);
            }
        });
        trainingProgress = Collections.unmodifiableMap(copiedTraining);

        powerGrowthAccumulated = Math.max(0, powerGrowthAccumulated);
    }

    // ── reads ────────────────────────────────────────────────────────────────────────────────────

    public int get(PlayerStat stat) {
        return values.getOrDefault(stat, 0);
    }

    public int power() {
        return get(PlayerStat.POWER);
    }

    public int precision() {
        return get(PlayerStat.PRECISION);
    }

    public int willpower() {
        return get(PlayerStat.WILLPOWER);
    }

    public int reflexes() {
        return get(PlayerStat.REFLEXES);
    }

    /** Derived and transport-only; 0 on a freshly loaded block until the server re-derives it. */
    public int knowledge() {
        return get(PlayerStat.KNOWLEDGE);
    }

    /** True only at defaults — the idempotency check {@code initializeStatsForNewPlayer} relies on. */
    public boolean isEmpty() {
        return values.values().stream().allMatch(v -> v == 0)
                && !isProdigy && powerGrowthAccumulated == 0 && trainingProgress.isEmpty();
    }

    // ── writes ───────────────────────────────────────────────────────────────────────────────────

    public PlayerStatsData with(PlayerStat stat, int value) {
        EnumMap<PlayerStat, Integer> next = new EnumMap<>(PlayerStat.class);
        next.putAll(values);
        next.put(stat, clamp(value));
        return new PlayerStatsData(next, isProdigy, powerGrowthAccumulated, trainingProgress);
    }

    public PlayerStatsData withPower(int newPower) {
        return with(PlayerStat.POWER, newPower);
    }

    /** Carries the derived KNOWLEDGE snapshot for client sync; not persisted. */
    public PlayerStatsData withKnowledge(int newKnowledge) {
        return with(PlayerStat.KNOWLEDGE, newKnowledge);
    }

    /**
     * Replaces the heritage-derived half of the block — the POWER roll, the prodigy flag and the spent
     * growth allowance — and leaves everything a player <em>earned</em> exactly where it is.
     *
     * <p>Its reason for existing is that changing heritage and re-rolling Power are not the same act as
     * starting a character. Both paths used to be expressed as "wipe the block, then run the new-player
     * initialiser", which also took PRECISION, REFLEXES, WILLPOWER and all four training accumulators with
     * it — hundreds of hours of practice deleted by a command named {@code reroll_power}.
     *
     * <p>{@code powerGrowthAccumulated} resets because the allowance is spent against a band, and the band
     * is what just changed; carrying it over would hand a player a Squib's spent growth on a Centaur's band.
     */
    public PlayerStatsData withHeritageRoll(int newPower, boolean prodigy) {
        EnumMap<PlayerStat, Integer> next = new EnumMap<>(PlayerStat.class);
        next.putAll(values);
        next.put(PlayerStat.POWER, clamp(newPower));
        return new PlayerStatsData(next, prodigy, 0, trainingProgress);
    }

    public PlayerStatsData withProdigy(boolean prodigy) {
        return new PlayerStatsData(values, prodigy, powerGrowthAccumulated, trainingProgress);
    }

    public PlayerStatsData withPowerGrowthAccumulated(int accumulated) {
        return new PlayerStatsData(values, isProdigy, accumulated, trainingProgress);
    }

    public PlayerStatsData withTrainingProgress(Map<PlayerStat, Float> newTraining) {
        return new PlayerStatsData(values, isProdigy, powerGrowthAccumulated, newTraining);
    }

    // ── serialization ────────────────────────────────────────────────────────────────────────────

    private static final Codec<Map<PlayerStat, Float>> TRAINING_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).xmap(
                    strMap -> {
                        Map<PlayerStat, Float> result = new EnumMap<>(PlayerStat.class);
                        strMap.forEach((key, val) -> {
                            PlayerStat stat = PlayerStat.fromId(key);
                            if (stat != null && stat.isTrainable()) {
                                result.put(stat, val);
                            }
                        });
                        return Collections.unmodifiableMap(result);
                    },
                    enumMap -> {
                        Map<String, Float> out = new LinkedHashMap<>();
                        enumMap.forEach((stat, val) -> out.put(stat.getId(), val));
                        return out;
                    });

    /** Persisted stat values, keyed by id. Derived stats are dropped on write and ignored on read. */
    private static final Codec<Map<PlayerStat, Integer>> VALUES_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(
                    strMap -> {
                        Map<PlayerStat, Integer> result = new EnumMap<>(PlayerStat.class);
                        strMap.forEach((key, val) -> {
                            PlayerStat stat = PlayerStat.fromId(key);
                            if (stat != null && !stat.isDerived()) {
                                result.put(stat, val);
                            }
                        });
                        return Collections.unmodifiableMap(result);
                    },
                    enumMap -> {
                        Map<String, Integer> out = new LinkedHashMap<>();
                        enumMap.forEach((stat, val) -> {
                            if (!stat.isDerived()) {
                                out.put(stat.getId(), val);
                            }
                        });
                        return out;
                    });

    /**
     * Reads the {@code stats} map, falling back to the four flat fields that every save written before
     * this change carries. Both are optional and the map wins, so an old save loads correctly with no
     * migrator class and no version field — the two encodings cannot be confused for one another.
     *
     * <p>The flat fields are still <em>written</em>, mirroring the map: {@code RecordCodecBuilder} emits
     * every field in the group, and that is left alone deliberately. It costs four ints and means a save
     * touched by this build still loads on the one before it, which matters while people are running
     * alpha builds against worlds they care about. The four names are frozen for that reason — a fifth
     * stat goes in the map only, and older builds simply will not see it.</p>
     */
    public static final Codec<PlayerStatsData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            VALUES_CODEC.fieldOf("stats").orElse(Map.of()).forGetter(PlayerStatsData::values),
            Codec.BOOL.fieldOf("is_prodigy").orElse(false).forGetter(PlayerStatsData::isProdigy),
            Codec.INT.fieldOf("power_growth_accumulated").orElse(0)
                    .forGetter(PlayerStatsData::powerGrowthAccumulated),
            TRAINING_CODEC.fieldOf("training_progress").orElse(Map.of())
                    .forGetter(PlayerStatsData::trainingProgress),
            // Legacy flat fields. Written by no current code path; getters return the same numbers the
            // map holds, so a round-trip through this codec is stable either way.
            Codec.INT.fieldOf("power").orElse(0).forGetter(PlayerStatsData::power),
            Codec.INT.fieldOf("precision").orElse(0).forGetter(PlayerStatsData::precision),
            Codec.INT.fieldOf("willpower").orElse(0).forGetter(PlayerStatsData::willpower),
            Codec.INT.fieldOf("reflexes").orElse(0).forGetter(PlayerStatsData::reflexes)
    ).apply(instance, (stats, prodigy, growth, training, power, precision, willpower, reflexes) -> {
        Map<PlayerStat, Integer> merged = new EnumMap<>(PlayerStat.class);
        merged.put(PlayerStat.POWER, power);
        merged.put(PlayerStat.PRECISION, precision);
        merged.put(PlayerStat.WILLPOWER, willpower);
        merged.put(PlayerStat.REFLEXES, reflexes);
        merged.putAll(stats);
        return new PlayerStatsData(merged, prodigy, growth, training);
    }));

    private static int clamp(int v) {
        return Math.max(MIN_VALUE, Math.min(MAX_VALUE, v));
    }
}
