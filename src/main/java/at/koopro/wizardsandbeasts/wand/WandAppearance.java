package at.koopro.wizardsandbeasts.wand;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * What a wand looks like, derived from the wood it is made of.
 *
 * <h2>The problem</h2>
 * <p>Every wand in the mod rendered the same sprite. {@code WandRenderer} branched on exactly one
 * thing — Elder Wand or not — so ten woods and three cores shared a single texture, and two wands
 * lying side by side in an inventory were indistinguishable until you hovered both and read the
 * tooltips. A wand is the most personal object in the mod and the one a player owns for the whole
 * game; it could not be told apart from anyone else's.
 *
 * <h2>Why a tint rather than ten sprites</h2>
 * <p>A tint is data-driven for free. Wands carry their wood as an {@code Identifier} component, not
 * an enum, so a datapack can add a wood tomorrow — ten hand-drawn sprites would leave that wood
 * looking like whichever one the renderer fell back to, while a tint gives it a colour derived from
 * its own id. The canon ten get hand-picked colours drawn from the real timber; anything else gets a
 * deterministic hash, kept in a narrow band of warm wood hues so an unknown wood still reads as
 * wood and not as a bug.
 *
 * <p>Shared rather than client-only so the same colour can key a tooltip or a GUI later without a
 * second table drifting from this one.
 */
@NullMarked
public final class WandAppearance {

    /** No tint. */
    public static final int UNTINTED = 0xFFFFFFFF;

    /**
     * The canon ten, coloured from the real timber rather than from a palette ramp: elder is pale
     * grey-white, yew almost black-red, holly a light warm cream, vine a green-tinged olive.
     * A player who learns "mine is the dark one" is learning something true about the wand.
     */
    private static final Map<String, Integer> CANON_WOOD_TINTS = Map.ofEntries(
            Map.entry("elder", 0xFFD9D2C2),
            Map.entry("yew", 0xFF5A3A32),
            Map.entry("holly", 0xFFE8D9B8),
            Map.entry("rowan", 0xFFC98F72),
            Map.entry("ash", 0xFFCFC3A4),
            Map.entry("vine", 0xFF8E9B62),
            Map.entry("walnut", 0xFF6B4A32),
            Map.entry("willow", 0xFFBFA97E),
            Map.entry("hawthorn", 0xFF9C6B54),
            Map.entry("blackthorn", 0xFF3E3330));

    private WandAppearance() {}

    /**
     * ARGB multiplier for a wand of this wood, or {@link #UNTINTED} when the wood is unknown and no
     * sensible colour can be derived.
     */
    public static int woodTint(@Nullable Identifier wood) {
        if (wood == null) {
            return UNTINTED;
        }
        Integer canon = CANON_WOOD_TINTS.get(wood.getPath());
        return canon != null ? canon : derivedTint(wood);
    }

    /** Convenience for render and tooltip call sites that hold the stack. */
    public static int woodTint(ItemStack stack) {
        return woodTint(WandComponents.getWood(stack));
    }

    /**
     * A stable colour for a wood nobody hand-picked one for.
     *
     * <p>Constrained to warm timber: hue anywhere in the wood band, but saturation and value pinned
     * to narrow ranges. An unconstrained hash would eventually hand some datapack a hot magenta wand,
     * which reads as a missing texture rather than as an unfamiliar wood.
     */
    private static int derivedTint(Identifier wood) {
        int hash = wood.toString().hashCode();
        float hue = ((hash >>> 8) % 360) / 360.0f * 0.12f + 0.03f;   // 11deg..54deg: amber to straw
        float saturation = 0.28f + ((hash >>> 3) & 0x3F) / 63.0f * 0.22f;
        float value = 0.55f + (hash & 0x3F) / 63.0f * 0.35f;
        return hsbToArgb(hue, saturation, value);
    }

    /**
     * HSB to packed ARGB, written out rather than borrowed from {@code java.awt.Color}.
     *
     * <p>AWT is not something to drag into a rendering path: it is a desktop toolkit whose class
     * initialisation can touch a display, and on a dedicated server this class is reachable from the
     * tooltip side. Twenty lines of arithmetic is the cheaper dependency.
     */
    private static int hsbToArgb(float hue, float saturation, float value) {
        float h = (hue - (float) Math.floor(hue)) * 6.0f;
        float f = h - (float) Math.floor(h);
        float p = value * (1.0f - saturation);
        float q = value * (1.0f - saturation * f);
        float t = value * (1.0f - saturation * (1.0f - f));
        float r;
        float g;
        float b;
        switch ((int) h) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }
        return 0xFF000000
                | (Math.round(r * 255.0f) << 16)
                | (Math.round(g * 255.0f) << 8)
                | Math.round(b * 255.0f);
    }
}
