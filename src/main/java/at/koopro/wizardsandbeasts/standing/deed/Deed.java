package at.koopro.wizardsandbeasts.standing.deed;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.standing.StandingAxis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * One datapack-authored rule: <em>when this happens, standing moves by this much.</em>
 *
 * <p>Lives at {@code data/<namespace>/magical_deeds/<id>.json}. Loaded by {@link DeedLoader} on every
 * server reload, like every other data-driven system in the mod.
 *
 * <pre>{@code
 * {
 *   "trigger": "spell_cast",
 *   "match": "expecto_patronum",
 *   "effects": { "alignment": 2.0 },
 *   "cooldownSeconds": 120
 * }
 * }</pre>
 *
 * @param trigger        when this is evaluated
 * @param match          what it is about — a spell id, an offence name, a bestiary entry id. Absent
 *                       means "any event of this trigger", which is how a broad rule such as
 *                       "studying anything at all leans you reformist" is written.
 * @param minTier        {@link DeedTrigger#BESTIARY_TIER} only: the lowest tier that counts.
 * @param effects        axis → delta. An axis that cannot take the write is rejected at load rather
 *                       than at fire time — see the codec note.
 * @param cooldownSeconds how long before the same player can score this deed again. Zero means every
 *                       occurrence counts, which is right for rare acts and disastrous for a spell
 *                       somebody can spam.
 */
@NullMarked
public record Deed(DeedTrigger trigger,
                   @Nullable String match,
                   @Nullable DiscoveryTier minTier,
                   Map<StandingAxis, Float> effects,
                   int cooldownSeconds) {

    /**
     * Validated at parse time, not at fire time. Content errors that only surface as "this deed never
     * seems to do anything" are the hardest kind to notice, so a deed that could never have an effect
     * is a loud load failure instead:
     *
     * <ul>
     *   <li>an empty {@code effects} map does nothing at all;</li>
     *   <li>{@link StandingAxis#MINISTRY} is derived from the criminal record and cannot be written —
     *       content wanting to move it should report an offence or grant a rank;</li>
     *   <li>a negative {@link StandingAxis#ALIGNMENT} delta is <em>darkening</em>, which belongs to
     *       {@code DarkCorruptionService} so that vocation scaling still applies.</li>
     * </ul>
     */
    public static final Codec<Deed> CODEC = RecordCodecBuilder.<Deed>create(instance -> instance.group(
            DeedTrigger.CODEC.fieldOf("trigger").forGetter(Deed::trigger),
            Codec.STRING.optionalFieldOf("match").forGetter(d -> java.util.Optional.ofNullable(d.match())),
            DiscoveryTier.CODEC.optionalFieldOf("minTier")
                    .forGetter(d -> java.util.Optional.ofNullable(d.minTier())),
            Codec.unboundedMap(StandingAxis.CODEC, Codec.FLOAT).fieldOf("effects").forGetter(Deed::effects),
            Codec.INT.optionalFieldOf("cooldownSeconds", 0).forGetter(Deed::cooldownSeconds)
    ).apply(instance, (trigger, match, minTier, effects, cooldown) ->
            new Deed(trigger, match.orElse(null), minTier.orElse(null), effects, cooldown)))
            .validate(Deed::validate);

    public Deed {
        effects = Map.copyOf(effects);
        cooldownSeconds = Math.max(0, cooldownSeconds);
    }

    private static DataResult<Deed> validate(Deed deed) {
        if (deed.effects.isEmpty()) {
            return DataResult.error(() -> "deed has no effects — it could never do anything");
        }
        for (Map.Entry<StandingAxis, Float> entry : deed.effects.entrySet()) {
            StandingAxis axis = entry.getKey();
            float delta = entry.getValue();
            if (Float.isNaN(delta) || delta == 0.0f) {
                return DataResult.error(() -> "deed effect for axis '" + axis.getSerializedName()
                        + "' is " + delta + "; a zero or NaN delta is never intentional");
            }
            if (axis == StandingAxis.MINISTRY) {
                return DataResult.error(() -> "the ministry axis is derived from the criminal record and "
                        + "cannot be written by a deed — report an offence or grant a rank instead");
            }
            if (axis == StandingAxis.ALIGNMENT && delta < 0.0f) {
                return DataResult.error(() -> "a negative alignment delta is dark corruption, which is "
                        + "owned by DarkCorruptionService so that vocation scaling still applies");
            }
        }
        if (deed.minTier != null && deed.trigger != DeedTrigger.BESTIARY_TIER) {
            return DataResult.error(() -> "minTier only applies to the bestiary_tier trigger");
        }
        return DataResult.success(deed);
    }

    /**
     * Whether this deed is about {@code key}.
     *
     * <p>Case-insensitive, and a bare path matches a namespaced key so content can say
     * {@code "expecto_patronum"} without repeating the namespace on every file — the same latitude
     * {@code SpellIds.matches} gives the rest of the mod.
     */
    public boolean matches(@Nullable String key) {
        if (match == null) {
            return true;
        }
        if (key == null) {
            return false;
        }
        String want = match.toLowerCase(Locale.ROOT);
        String have = key.toLowerCase(Locale.ROOT);
        if (want.equals(have)) {
            return true;
        }
        return !want.contains(":") && have.endsWith(":" + want);
    }

    /** Whether a bestiary advancement to {@code reached} clears this deed's tier floor. */
    public boolean meetsTier(@Nullable DiscoveryTier reached) {
        if (minTier == null) {
            return true;
        }
        return reached != null && reached.ordinal() >= minTier.ordinal();
    }
}
