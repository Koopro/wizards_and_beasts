package at.koopro.wizardsandbeasts.standing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The part of a wizard's standing that has to be written down: two numbers.
 *
 * <p>Everything else on {@link StandingAxis} is composed at read time from data the mod already keeps
 * — {@code DARK_CORRUPTION} and {@code PlayerMinistryRecord} — so this record deliberately does
 * <em>not</em> mirror them. A mirrored copy is a second source of truth, and the two drift the first
 * time somebody writes to the original through a path that predates this system. There are four such
 * paths for corruption alone.
 *
 * @param tradition blood-status politics, {@code −bound … +bound}; negative reformist, positive
 *                  traditionalist. Starts at 0 for everyone: heritage sets where you begin the game,
 *                  conduct decides where you end it.
 * @param light     the light pole of the alignment axis, {@code 0 … bound}. Earned by protective and
 *                  restorative magic; the dark pole is the existing corruption meter.
 */
@NullMarked
public record MagicalStanding(float tradition, float light, float publicRep, float profession,
                              String professionOf) {

    public static final MagicalStanding DEFAULT = new MagicalStanding(0.0f, 0.0f);

    /**
     * Both fields are optional so a save written before this system existed loads as a neutral wizard
     * rather than failing to parse. Bounds are <em>not</em> applied here — the compact constructor has
     * no access to the server's configured bound, and clamping to a hardcoded one would quietly rewrite
     * a value a wider-bound server legitimately stored. {@link StandingService} clamps on write.
     */
    public static final Codec<MagicalStanding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("tradition", 0.0f).forGetter(MagicalStanding::tradition),
            Codec.FLOAT.optionalFieldOf("light", 0.0f).forGetter(MagicalStanding::light),
            Codec.FLOAT.optionalFieldOf("public", 0.0f).forGetter(MagicalStanding::publicRep),
            Codec.FLOAT.optionalFieldOf("profession", 0.0f).forGetter(MagicalStanding::profession),
            Codec.STRING.optionalFieldOf("profession_of", "").forGetter(MagicalStanding::professionOf)
    ).apply(instance, MagicalStanding::new));

    public MagicalStanding {
        // NaN is the one value that must never survive: it defeats every later clamp and comparison,
        // and renders as "NaN" on the character sheet. A corrupt save or a hand-edited datapack float
        // are both realistic ways in.
        tradition = Float.isNaN(tradition) ? 0.0f : tradition;
        light = Float.isNaN(light) ? 0.0f : Math.max(0.0f, light);
        publicRep = Float.isNaN(publicRep) ? 0.0f : publicRep;
        profession = Float.isNaN(profession) ? 0.0f : profession;
    }

    /** Tradition and light only, as the record was before public and professional reputation. */
    public MagicalStanding(float tradition, float light) {
        this(tradition, light, 0.0f, 0.0f, "");
    }

    public MagicalStanding withTradition(float value) {
        return new MagicalStanding(value, light, publicRep, profession, professionOf);
    }

    public MagicalStanding withLight(float value) {
        return new MagicalStanding(tradition, value, publicRep, profession, professionOf);
    }

    public MagicalStanding withPublic(float value) {
        return new MagicalStanding(tradition, light, value, profession, professionOf);
    }

    /** Professional reputation, held for {@code holder}; a different holder starts from {@code value} afresh. */
    public MagicalStanding withProfession(float value, String holder) {
        return new MagicalStanding(tradition, light, publicRep, value, holder);
    }

    /** Professional reputation as it applies to {@code current}: none at all for a profession not held then. */
    public float professionFor(String current) {
        return !current.isEmpty() && current.equals(professionOf) ? profession : 0.0f;
    }

    /** True for a wizard who has done nothing either way yet — used to skip work, never to hide the UI. */
    public boolean isNeutral() {
        return tradition == 0.0f && light == 0.0f && publicRep == 0.0f && profession == 0.0f;
    }
}
