package at.koopro.wizardsandbeasts.spell.petrify;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Server-serialised petrification state for a player. Unlike {@link at.koopro.wizardsandbeasts.spell.imperio.ImperioControlState},
 * there is no tick-down toward release on a fixed schedule — petrification lasts until cured (Mandrake
 * Restoration Draught) or until {@code petrifiedAtGameTime} plus the natural-cure window elapses, matching canon's
 * "wears off after about a century" (scaled down to ~100 in-game days here). A Bezoar dose advances
 * {@code petrifiedAtGameTime} backward to shorten that remaining window without lifting the stone state outright.
 * The frozen pose is captured at the moment of petrification so the movement-freeze tick can pin the
 * player exactly, rather than merely zeroing velocity (which still allows creep from knockback/pushing).
 */
public record PetrifiedState(
        boolean isPetrified,
        double x,
        double y,
        double z,
        float yRot,
        float xRot,
        long petrifiedAtGameTime) {

    public static final PetrifiedState DEFAULT = new PetrifiedState(false, 0, 0, 0, 0, 0, 0L);

    public static final Codec<PetrifiedState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.BOOL.fieldOf("is_petrified").forGetter(PetrifiedState::isPetrified),
            Codec.DOUBLE.fieldOf("x").forGetter(PetrifiedState::x),
            Codec.DOUBLE.fieldOf("y").forGetter(PetrifiedState::y),
            Codec.DOUBLE.fieldOf("z").forGetter(PetrifiedState::z),
            Codec.FLOAT.fieldOf("y_rot").forGetter(PetrifiedState::yRot),
            Codec.FLOAT.fieldOf("x_rot").forGetter(PetrifiedState::xRot),
            Codec.LONG.optionalFieldOf("petrified_at", 0L).forGetter(PetrifiedState::petrifiedAtGameTime)
    ).apply(inst, PetrifiedState::new));

    public PetrifiedState withPetrifiedAtGameTime(long v) {
        return new PetrifiedState(isPetrified, x, y, z, yRot, xRot, v);
    }
}
