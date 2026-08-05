package at.koopro.wizardsandbeasts.ability.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How practised a player is at each non-spell ability, keyed by the ability's {@link Identifier} — the same
 * key the datapack definition and {@code AbilityIds} use, so there is one vocabulary and not two.
 *
 * <p>Deliberately separate from {@code PlayerSpellData}'s per-spell proficiency map. That one lives behind
 * {@code Module.PROFICIENCY} and is only ever written by successful spell hits, so an ability could neither
 * earn into it nor read out of it when the module is off. This attachment is generic on purpose: Animagus,
 * Legilimency, Metamorphmagus and Wandless Casting can all key into it later without another attachment.
 *
 * <p>Values are clamped to {@code [0, 1]} and zero entries are dropped, so an untouched ability and an
 * ability practised back down to nothing serialise identically — the map never accumulates dead keys.
 *
 * @param values per-ability proficiency, {@code (0, 1]}; a missing key reads as {@link #MIN}
 */
@NullMarked
public record PlayerAbilityProficiency(Map<Identifier, Float> values) {

    /** Never practised. Also what any absent key reads as. */
    public static final float MIN = 0.0f;
    /** Mastery. */
    public static final float MAX = 1.0f;

    public static final PlayerAbilityProficiency EMPTY = new PlayerAbilityProficiency(Map.of());

    public PlayerAbilityProficiency {
        Map<Identifier, Float> cleaned = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Float> entry : values.entrySet()) {
            float clamped = clamp(entry.getValue());
            // NaN fails this comparison and is discarded with the zeroes, which is the intent.
            if (clamped > MIN) {
                cleaned.put(entry.getKey(), clamped);
            }
        }
        values = Map.copyOf(cleaned);
    }

    public static final Codec<PlayerAbilityProficiency> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Identifier.CODEC, Codec.FLOAT).optionalFieldOf("values", Map.of())
                    .forGetter(PlayerAbilityProficiency::values)
    ).apply(instance, PlayerAbilityProficiency::new));

    /** Proficiency in {@code abilityId}, or {@link #MIN} if it has never been practised. */
    public float get(Identifier abilityId) {
        return values.getOrDefault(abilityId, MIN);
    }

    /** Returns a copy with {@code abilityId} set to {@code value}, clamped. */
    public PlayerAbilityProficiency with(Identifier abilityId, float value) {
        Map<Identifier, Float> next = new LinkedHashMap<>(values);
        next.put(abilityId, clamp(value));
        return new PlayerAbilityProficiency(next);
    }

    /** Returns a copy with {@code delta} added to {@code abilityId}, clamped. */
    public PlayerAbilityProficiency plus(Identifier abilityId, float delta) {
        return with(abilityId, get(abilityId) + delta);
    }

    /** Returns a copy with {@code abilityId} forgotten entirely. */
    public PlayerAbilityProficiency without(Identifier abilityId) {
        if (!values.containsKey(abilityId)) {
            return this;
        }
        Map<Identifier, Float> next = new LinkedHashMap<>(values);
        next.remove(abilityId);
        return new PlayerAbilityProficiency(next);
    }

    private static float clamp(float value) {
        return Math.max(MIN, Math.min(MAX, value));
    }
}
