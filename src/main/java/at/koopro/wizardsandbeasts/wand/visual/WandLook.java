package at.koopro.wizardsandbeasts.wand.visual;

import at.koopro.wizardsandbeasts.wand.customization.WandConfiguration;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Everything that makes one wand look like itself, resolved from data the stack already carries
 * ({@link WandLookResolver}). Pure data: the renderer applies it, the dev preview prints it, tests pin it.
 *
 * <p>What it deliberately does not hold is the bond. That depends on who is looking at the wand in
 * whose hand ({@link WandBondLook}), so it is decided per draw while this record can be cached per make.
 *
 * @param grain         the grain sheet, or null for the plain sheet
 * @param tint          ARGB multiplier for the sheet: the wood's colour with this wand's slight variation
 * @param configuration which handle, shaft, tip (and ornament) the wand shows
 * @param rareIndex     which of the wood's rare constructions this wand is, or -1
 * @param lengthScale   the shaft's length relative to the model's; the grip and the tip keep theirs
 * @param girth         the shaft's and tip's width relative to the model's; flexibility thins a wand
 * @param bowDegrees    signed bend at the grip and again at the tip; flexibility bows a wand
 * @param twistDegrees  turn of the shaft about its own axis: no two hand-turned wands sit quite alike
 * @param glint         the core's light, RGB
 * @param pattern       the core light's rhythm
 * @param glintStrength how bright the core shows at its best, {@code [0, 1]}
 * @param phase         this wand's offset into its rhythm, {@code [0, 1)}
 * @param elder         the Elder Wand: its own model, never tinted, bent or resized
 */
@NullMarked
public record WandLook(@Nullable Identifier grain,
                       int tint,
                       WandConfiguration configuration,
                       int rareIndex,
                       float lengthScale,
                       float girth,
                       float bowDegrees,
                       float twistDegrees,
                       int glint,
                       WandGlintPattern pattern,
                       float glintStrength,
                       float phase,
                       boolean elder) {

    /** The modular wand's planes ({@code tools/wand_model.py}): grip 0-11, shaft 11-23, tip 23-27.5. */
    public static final float MODEL_HANDLE = 11.0f;
    public static final float MODEL_SHAFT = 12.0f;
    public static final float MODEL_TIP = 4.5f;
    public static final float MODEL_LENGTH = MODEL_HANDLE + MODEL_SHAFT + MODEL_TIP;

    /** Model units from butt to point as drawn, before any display scaling. */
    public float drawnLength() {
        return elder ? MODEL_LENGTH : MODEL_HANDLE + MODEL_SHAFT * lengthScale + MODEL_TIP;
    }

    /**
     * The colour to draw the core's light in at this moment, as an ARGB multiplier for an additive pass:
     * black is no light at all.
     */
    public int glintArgb(WandBondLook bond, float seconds) {
        float level = glintStrength * bond.strength() * pattern.intensity(seconds, phase);
        float r = ((glint >> 16) & 0xFF) / 255.0f;
        float g = ((glint >> 8) & 0xFF) / 255.0f;
        float b = (glint & 0xFF) / 255.0f;
        float grey = 0.299f * r + 0.587f * g + 0.114f * b;
        float s = bond.saturation();
        r = (grey + (r - grey) * s) * level;
        g = (grey + (g - grey) * s) * level;
        b = (grey + (b - grey) * s) * level;
        return 0xFF000000 | channel(r) << 16 | channel(g) << 8 | channel(b);
    }

    private static int channel(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255.0f)));
    }
}
