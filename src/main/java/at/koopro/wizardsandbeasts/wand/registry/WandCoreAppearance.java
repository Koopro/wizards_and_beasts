package at.koopro.wizardsandbeasts.wand.registry;

import at.koopro.wizardsandbeasts.wand.visual.WandGlintPattern;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * How a core shows through its wand.
 *
 * <p>A core is canonically hidden inside the wood, so it never recolours the wand. What it gets is the
 * faint light at the point (and, for a wand loyal to its holder, at the pommel): a phoenix feather's
 * ember, a dragon heartstring's beat, a unicorn hair's steady pale gleam. Colour and rhythm are the
 * two things that tell cores apart; how bright the light is at any moment is the wand's relationship
 * with whoever holds it.
 *
 * @param glint    the light's colour, {@code "#RRGGBB"}; absent means a muted colour derived from the core id
 * @param pattern  the light's rhythm
 * @param strength how bright the core shows at its best, {@code [0, 1]}
 */
public record WandCoreAppearance(Optional<Integer> glint, WandGlintPattern pattern, float strength) {

    public static final float DEFAULT_STRENGTH = 0.5f;

    public static final WandCoreAppearance NONE =
            new WandCoreAppearance(Optional.empty(), WandGlintPattern.STEADY, DEFAULT_STRENGTH);

    private static final Codec<Integer> HEX_COLOUR = Codec.STRING.comapFlatMap(
            WandWoodAppearance::parseHex,
            argb -> String.format(java.util.Locale.ROOT, "#%06X", argb & 0xFFFFFF));

    public static final Codec<WandCoreAppearance> CODEC = RecordCodecBuilder.<WandCoreAppearance>create(instance -> instance.group(
            HEX_COLOUR.optionalFieldOf("glint").forGetter(WandCoreAppearance::glint),
            WandGlintPattern.CODEC.optionalFieldOf("pattern", WandGlintPattern.STEADY).forGetter(WandCoreAppearance::pattern),
            Codec.FLOAT.optionalFieldOf("strength", DEFAULT_STRENGTH).forGetter(WandCoreAppearance::strength)
    ).apply(instance, WandCoreAppearance::new)).validate(appearance ->
            appearance.strength >= 0.0f && appearance.strength <= 1.0f
                    ? DataResult.success(appearance)
                    : DataResult.error(() -> "Wand core glint strength must be in [0, 1]: " + appearance.strength));
}
