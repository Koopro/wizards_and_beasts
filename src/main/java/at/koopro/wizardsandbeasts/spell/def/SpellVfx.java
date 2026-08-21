package at.koopro.wizardsandbeasts.spell.def;

import at.koopro.wizardsandbeasts.spell.core.SpellFamily;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * A spell's authored look: which particle it trails, which it bursts into, and how many.
 *
 * <h2>Why this exists</h2>
 * <p>Seven distinct tinted particle types ship ({@code fire_ember}, {@code ice_shard},
 * {@code electric_arc}, {@code arcane_mote}, {@code dark_wisp}, {@code light_glow},
 * {@code water_droplet}) and a spell could not choose between them: {@code ModParticles.typeFor}
 * picked one from the spell's {@link SpellFamily}, so every spell in a family looked the same shape
 * and differed only in hue. Stupefy and Expelliarmus are both red-ish arcane bolts; side by side they
 * were the same effect twice.
 *
 * <p>The block is optional and absence means "keep doing what you did" — the family default. Nothing
 * had to be re-authored for the change to land, and a datapack spell with no {@code vfx} still looks
 * like its family.
 *
 * <h2>Why an enum and not a particle id</h2>
 * <p>A free-form registry id in a datapack is a crash waiting for a typo, and a particle that is not
 * one of ours takes options we cannot construct. The enum names the mod's own tinted set, so an
 * unknown value fails at load with a codec error naming the file, rather than at render time.
 *
 * <p>Counts are clamped on construction rather than trusted: particle spam is a client framerate
 * problem, and a datapack that asks for ten thousand embers should get the cap, not the frame drop.
 */
@NullMarked
public record SpellVfx(Style trail, Style impact, int trailDensity, int impactCount) {

    /** Hard ceiling per burst. Ten to twenty reads as an effect; a hundred reads as a stutter. */
    public static final int MAX_IMPACT_COUNT = 48;
    /** Trail particles per projectile tick. Anything above a handful is a smoke screen. */
    public static final int MAX_TRAIL_DENSITY = 6;

    /** The mod's tinted particle set, by name. */
    public enum Style implements StringRepresentable {
        FIRE_EMBER(SpellFamily.FIRE),
        ICE_SHARD(SpellFamily.ICE),
        ELECTRIC_ARC(SpellFamily.ELECTRIC),
        ARCANE_MOTE(SpellFamily.ARCANE),
        DARK_WISP(SpellFamily.DARK),
        LIGHT_GLOW(SpellFamily.LIGHT),
        WATER_DROPLET(SpellFamily.WATER);

        public static final Codec<Style> CODEC = StringRepresentable.fromValues(Style::values);

        /**
         * The family whose particle type this style is.
         *
         * <p>Styles are expressed through {@code SpellFamily} rather than by holding a
         * {@code ParticleType} because this record is loaded on both sides and the particle registry
         * is not available where spell JSON is parsed. {@code ModParticles.typeFor} already maps
         * family to type; a style is just a way for a spell to pick a family's particle without
         * <em>being</em> that family.
         */
        private final SpellFamily particleFamily;

        Style(SpellFamily particleFamily) {
            this.particleFamily = particleFamily;
        }

        public SpellFamily particleFamily() {
            return particleFamily;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The style a spell of this family gets when it authors no {@code vfx} block. */
        public static Style defaultFor(SpellFamily family) {
            for (Style style : values()) {
                if (style.particleFamily == family) {
                    return style;
                }
            }
            return ARCANE_MOTE;
        }
    }

    public SpellVfx {
        trailDensity = Math.max(0, Math.min(MAX_TRAIL_DENSITY, trailDensity));
        impactCount = Math.max(0, Math.min(MAX_IMPACT_COUNT, impactCount));
    }

    public static final Codec<SpellVfx> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Style.CODEC.optionalFieldOf("trail", Style.ARCANE_MOTE).forGetter(SpellVfx::trail),
            Style.CODEC.optionalFieldOf("impact", Style.ARCANE_MOTE).forGetter(SpellVfx::impact),
            Codec.INT.optionalFieldOf("trailDensity", 1).forGetter(SpellVfx::trailDensity),
            Codec.INT.optionalFieldOf("impactCount", 12).forGetter(SpellVfx::impactCount)
    ).apply(instance, SpellVfx::new));

    /**
     * The look a spell has when it authors nothing: its family's particle for both trail and impact,
     * at the counts the mod used before this record existed.
     */
    public static SpellVfx defaultFor(@Nullable SpellFamily family) {
        Style style = Style.defaultFor(family == null ? SpellFamily.ARCANE : family);
        return new SpellVfx(style, style, 1, 12);
    }

    /** Resolve an authored block against its spell's family. */
    public static SpellVfx resolve(Optional<SpellVfx> authored, @Nullable SpellFamily family) {
        return authored.orElseGet(() -> defaultFor(family));
    }
}
