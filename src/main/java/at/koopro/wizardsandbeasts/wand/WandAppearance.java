package at.koopro.wizardsandbeasts.wand;

import at.koopro.wizardsandbeasts.wand.customization.WandConfiguration;
import at.koopro.wizardsandbeasts.wand.customization.WandSlot;
import at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries;
import at.koopro.wizardsandbeasts.wand.registry.WandWoodAppearance;
import at.koopro.wizardsandbeasts.wand.registry.WandWoodDefinition;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * What a wand looks like, derived from the wood it is made of: its colour and its silhouette.
 *
 * <p>Both come from the wood's datapack entry ({@link WandWoodAppearance} on
 * {@link WandWoodDefinition}), which the client already has: the wood registry is synced. This used
 * to be a hard-coded table of ten tints beside that registry, and the silhouette was not derived at
 * all -- every wand made in play rendered the base wand's classic handle, straight shaft and pointed
 * tip, so ten woods differed only in colour.
 *
 * <h2>Why a tint rather than ten sprites</h2>
 * <p>A tint is data-driven for free: wands carry their wood as an {@code Identifier} component, not an
 * enum, so a datapack can add a wood tomorrow. A wood that names no tint gets a deterministic colour
 * derived from its id, kept in a narrow band of warm timber hues so an unknown wood still reads as
 * wood and not as a bug.
 *
 * <p>Shared rather than client-only so a tooltip or GUI can key off the same colour without a second
 * table drifting from this one.
 */
@NullMarked
public final class WandAppearance {

    /** No tint. */
    public static final int UNTINTED = 0xFFFFFFFF;

    /**
     * A wand with no wood at all. The sheet is painted light and neutral so each wood's tint can
     * colour it; left untinted it would read as bare bleached wood, so a woodless wand gets the
     * classic warm brown the sheet used to be painted in.
     */
    public static final int NO_WOOD_TINT = 0xFFA9784B;

    private WandAppearance() {}

    /** The wood's datapack entry, or null when there is no registry to ask or the wood is unknown. */
    public static @Nullable WandWoodDefinition definition(HolderLookup.@Nullable Provider registries,
                                                         @Nullable Identifier wood) {
        if (registries == null || wood == null) {
            return null;
        }
        return registries.lookup(WandDatapackRegistries.WAND_WOOD_REGISTRY)
                .flatMap(woods -> woods.get(ResourceKey.create(WandDatapackRegistries.WAND_WOOD_REGISTRY, wood)))
                .map(Holder::value)
                .orElse(null);
    }

    /**
     * ARGB multiplier for a wand of this wood: the wood's own tint, a colour derived from its id when
     * it names none, or {@link #NO_WOOD_TINT} for a wand with no wood at all.
     */
    public static int woodTint(@Nullable WandWoodDefinition definition, @Nullable Identifier wood) {
        if (wood == null) {
            return NO_WOOD_TINT;
        }
        if (definition != null && definition.appearance().tint().isPresent()) {
            return definition.appearance().tint().get();
        }
        return derivedTint(wood);
    }

    /** Convenience for render and tooltip call sites that hold the stack. */
    public static int woodTint(HolderLookup.@Nullable Provider registries, ItemStack stack) {
        Identifier wood = WandComponents.getWood(stack);
        return woodTint(definition(registries, wood), wood);
    }

    /**
     * Which handle, shaft and tip a wand shows. An explicit configuration on the stack (set by
     * {@code /wandb wand config}) is kept exactly; otherwise the wood's modules are laid over the base
     * wand, so a slot the wood leaves open keeps the base wand's choice.
     */
    public static WandConfiguration configuration(HolderLookup.@Nullable Provider registries, ItemStack stack) {
        WandConfiguration explicit = stack.get(WandComponents.WAND_CONFIGURATION.get());
        if (explicit != null) {
            return explicit;
        }
        return configurationFor(definition(registries, WandComponents.getWood(stack)));
    }

    /** The base wand with this wood's modules laid over it. */
    public static WandConfiguration configurationFor(@Nullable WandWoodDefinition definition) {
        WandConfiguration config = WandConfiguration.DEFAULT;
        if (definition == null) {
            return config;
        }
        for (Map.Entry<WandSlot, Identifier> module : definition.appearance().modules().entrySet()) {
            config = config.withModule(module.getKey(), module.getValue());
        }
        return config;
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
