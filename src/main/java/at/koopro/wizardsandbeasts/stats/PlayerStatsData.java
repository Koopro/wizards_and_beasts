package at.koopro.wizardsandbeasts.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.*;

public record PlayerStatsData(
        int power,
        int precision,
        int willpower,
        int reflexes,
        boolean isProdigy,
        int powerGrowthAccumulated,
        Map<PlayerStat, Float> trainingProgress,
        // Derived, transport-only snapshot of KNOWLEDGE. Computed server-side at sync time and
        // shipped to the client for display; never persisted (the server always re-derives it).
        int knowledge
) {
    public static final PlayerStatsData EMPTY =
            new PlayerStatsData(0, 0, 0, 0, false, 0, Map.of(), 0);

    private static final Codec<Map<PlayerStat, Float>> TRAINING_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).xmap(
                    strMap -> {
                        Map<PlayerStat, Float> result = new EnumMap<>(PlayerStat.class);
                        strMap.forEach((key, val) -> {
                            PlayerStat stat = PlayerStat.fromId(key);
                            if (stat != null && stat.isTrainable()) result.put(stat, val);
                        });
                        return Collections.unmodifiableMap(result);
                    },
                    enumMap -> {
                        Map<String, Float> out = new LinkedHashMap<>();
                        enumMap.forEach((stat, val) -> out.put(stat.getId(), val));
                        return out;
                    }
            );

    public static final Codec<PlayerStatsData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("power").orElse(0).forGetter(PlayerStatsData::power),
            Codec.INT.fieldOf("precision").orElse(0).forGetter(PlayerStatsData::precision),
            Codec.INT.fieldOf("willpower").orElse(0).forGetter(PlayerStatsData::willpower),
            Codec.INT.fieldOf("reflexes").orElse(0).forGetter(PlayerStatsData::reflexes),
            Codec.BOOL.fieldOf("is_prodigy").orElse(false).forGetter(PlayerStatsData::isProdigy),
            Codec.INT.fieldOf("power_growth_accumulated").orElse(0).forGetter(PlayerStatsData::powerGrowthAccumulated),
            TRAINING_CODEC.fieldOf("training_progress").orElse(Map.of()).forGetter(PlayerStatsData::trainingProgress)
            // knowledge is derived and transport-only — not persisted; loads as 0 and is re-derived server-side.
    ).apply(instance, (power, precision, willpower, reflexes, isProdigy, powerGrowthAccumulated, trainingProgress) ->
            new PlayerStatsData(power, precision, willpower, reflexes, isProdigy, powerGrowthAccumulated, trainingProgress, 0)));

    /** True only when all fields are at default/zero values — used for idempotency check in initializeStatsForNewPlayer. */
    public boolean isEmpty() {
        return power == 0 && precision == 0 && willpower == 0 && reflexes == 0
                && !isProdigy && powerGrowthAccumulated == 0 && trainingProgress.isEmpty();
    }

    public PlayerStatsData withPower(int newPower) {
        return new PlayerStatsData(clamp(newPower), precision, willpower, reflexes,
                isProdigy, powerGrowthAccumulated, trainingProgress, knowledge);
    }

    public PlayerStatsData withPrecision(int newPrecision) {
        return new PlayerStatsData(power, clamp(newPrecision), willpower, reflexes,
                isProdigy, powerGrowthAccumulated, trainingProgress, knowledge);
    }

    public PlayerStatsData withWillpower(int newWillpower) {
        return new PlayerStatsData(power, precision, clamp(newWillpower), reflexes,
                isProdigy, powerGrowthAccumulated, trainingProgress, knowledge);
    }

    public PlayerStatsData withReflexes(int newReflexes) {
        return new PlayerStatsData(power, precision, willpower, clamp(newReflexes),
                isProdigy, powerGrowthAccumulated, trainingProgress, knowledge);
    }

    public PlayerStatsData withProdigy(boolean prodigy) {
        return new PlayerStatsData(power, precision, willpower, reflexes,
                prodigy, powerGrowthAccumulated, trainingProgress, knowledge);
    }

    public PlayerStatsData withPowerGrowthAccumulated(int accumulated) {
        return new PlayerStatsData(power, precision, willpower, reflexes,
                isProdigy, accumulated, trainingProgress, knowledge);
    }

    public PlayerStatsData withTrainingProgress(Map<PlayerStat, Float> newTraining) {
        EnumMap<PlayerStat, Float> copy = new EnumMap<>(PlayerStat.class);
        copy.putAll(newTraining);
        return new PlayerStatsData(power, precision, willpower, reflexes,
                isProdigy, powerGrowthAccumulated, Collections.unmodifiableMap(copy), knowledge);
    }

    /** Returns a copy carrying the given derived KNOWLEDGE snapshot (for client sync only). */
    public PlayerStatsData withKnowledge(int newKnowledge) {
        return new PlayerStatsData(power, precision, willpower, reflexes,
                isProdigy, powerGrowthAccumulated, trainingProgress, clamp(newKnowledge));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
