package at.koopro.wizardsandbeasts.creature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Dragon-only behaviour block, nested as the optional {@code dragon} field on {@link CreatureDefinition}.
 * Brooms are homogeneous (flat definition); creatures are heterogeneous, so dragon-specific tuning lives
 * here and stays off the other 59 generic creatures. Read at runtime by {@code DragonEntity} /
 * {@code DragonBreathGoal} (server) and {@code DragonRenderer} (client, copied into render-state).
 *
 * <p>No separate registry: the canonical decision is a nested sub-codec, not a new {@code DragonDefinition}.
 *
 * @param fireRange   conal fire-breath reach, in blocks
 * @param fireColor   particle tint, {@code 0xRRGGBB}
 * @param flameShape  emitter + cone half-angle profile
 * @param blockEffect what the breath does to blocks it hits
 * @param biteVenom   when present, melee applies this status effect
 * @param rideable    data-only forward-compat flag; mount behaviour ships in a follow-up
 * @param scale       render-only model scale (no hitbox change; hitbox is registry-frozen in ModCreatures)
 */
public record DragonTraits(
        float fireRange,
        int fireColor,
        FlameShape flameShape,
        BlockEffect blockEffect,
        Optional<BiteVenom> biteVenom,
        boolean rideable,
        float scale) {

    /** Emitter shape + cone geometry for the breath. */
    public enum FlameShape {
        /** Narrow continuous exhale (default cone). */
        STREAM,
        /** Tight, longer, focused lance (narrower cone, longer reach feel). */
        JET,
        /** Wide, short mushroom puff (Fireball-style). */
        BURST
    }

    /** What a flame does to the blocks it strikes. */
    public enum BlockEffect {
        /** Vanilla fire placement on flammable faces. */
        IGNITE,
        /** Clean removal (set to air, no drops) of timber/bone-tagged blocks, with an ash puff. */
        ASH
    }

    /**
     * Melee venom payload.
     *
     * @param effect        status effect id (default {@code minecraft:poison})
     * @param amplifier     effect amplifier (0 = level I)
     * @param durationTicks effect duration in ticks
     */
    public record BiteVenom(@NonNull Identifier effect, int amplifier, int durationTicks) {
        public static final Codec<BiteVenom> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.optionalFieldOf("effect", Identifier.fromNamespaceAndPath("minecraft", "poison"))
                        .forGetter(BiteVenom::effect),
                Codec.INT.optionalFieldOf("amplifier", 0).forGetter(BiteVenom::amplifier),
                Codec.INT.optionalFieldOf("duration_ticks", 120).forGetter(BiteVenom::durationTicks)
        ).apply(instance, BiteVenom::new));
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.xmap(s -> Enum.valueOf(type, s), Enum::name);
    }

    public static final Codec<DragonTraits> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("fire_range", 10.0f).forGetter(DragonTraits::fireRange),
            Codec.INT.optionalFieldOf("fire_color", 0xF97316).forGetter(DragonTraits::fireColor),
            enumCodec(FlameShape.class).optionalFieldOf("flame_shape", FlameShape.STREAM).forGetter(DragonTraits::flameShape),
            enumCodec(BlockEffect.class).optionalFieldOf("block_effect", BlockEffect.IGNITE).forGetter(DragonTraits::blockEffect),
            BiteVenom.CODEC.optionalFieldOf("bite_venom").forGetter(DragonTraits::biteVenom),
            Codec.BOOL.optionalFieldOf("rideable", false).forGetter(DragonTraits::rideable),
            Codec.FLOAT.optionalFieldOf("scale", 1.0f).forGetter(DragonTraits::scale)
    ).apply(instance, DragonTraits::new));
}
