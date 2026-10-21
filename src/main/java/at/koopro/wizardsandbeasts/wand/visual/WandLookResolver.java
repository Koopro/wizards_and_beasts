package at.koopro.wizardsandbeasts.wand.visual;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.wand.WandAppearance;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.customization.WandConfiguration;
import at.koopro.wizardsandbeasts.wand.registry.WandCoreAppearance;
import at.koopro.wizardsandbeasts.wand.registry.WandCoreDefinition;
import at.koopro.wizardsandbeasts.wand.registry.WandWoodDefinition;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Turns a wand's make into its {@link WandLook}.
 *
 * <p>Reads only what a wand already carries — wood, core, length, flexibility, the optional explicit
 * configuration and the Elder Wand marker — plus the wood's and core's datapack {@code appearance}
 * blocks. Writes nothing, so no saved wand changes and no stat moves: the look is derived every time,
 * and two wands of one make always look alike.
 *
 * <p>Where a wand needs to look a little unlike its neighbours (a hand-turned shaft never sits quite
 * straight) the variation comes from a seed hashed out of the make itself. The length is rolled per
 * craft as a float, so in practice every crafted wand gets its own seed.
 *
 * <p>Shared rather than client-only so tests and the dev preview resolve exactly what the renderer does.
 */
@NullMarked
public final class WandLookResolver {

    /** The length the model is drawn at, in inches: about the middle of the wandmaking recipes' range. */
    public static final float REFERENCE_INCHES = 12.25f;
    /**
     * Shaft scale per inch. Steeper than true proportion would be at the short end (a real 9" wand has
     * barely half the shaft of a 13" one), so a short wand still reads as a wand and not a stub, but steep
     * enough that 9" against 15" is obvious at a glance.
     */
    public static final float SCALE_PER_INCH = 0.10f;
    public static final float MIN_LENGTH_SCALE = 0.55f;
    public static final float MAX_LENGTH_SCALE = 1.45f;

    /** Most a shaft turns about its own axis, either way. */
    public static final float MAX_TWIST_DEGREES = 8.0f;
    /** Most a wand's brightness differs from its wood's colour, either way. */
    public static final float TINT_VARIATION = 0.04f;

    /** Bone-white, cold: the Elder Wand's light, nothing like any ordinary core's. */
    public static final int ELDER_GLINT = 0xD8E4F2;
    public static final float ELDER_GLINT_STRENGTH = 0.9f;

    private static final long SALT_TINT = 1;
    private static final long SALT_TWIST = 2;
    private static final long SALT_PHASE = 3;
    private static final long SALT_BOW = 4;
    public static final long SALT_RARE = 5;

    private WandLookResolver() {}

    public static WandLook resolve(HolderLookup.@Nullable Provider registries, ItemStack stack) {
        Identifier wood = WandComponents.getWood(stack);
        Identifier core = WandComponents.getCore(stack);
        return resolve(WandAppearance.definition(registries, wood), wood,
                WandAppearance.coreDefinition(registries, core), core,
                WandComponents.getLength(stack), WandComponents.getFlexibility(stack),
                stack.get(WandComponents.WAND_CONFIGURATION.get()), ModDataComponents.isElderWand(stack));
    }

    /**
     * @param explicit a configuration set on the stack by command or preset; wins over the wood's shape
     *                 and any rare construction, exactly as before looks existed
     */
    public static WandLook resolve(@Nullable WandWoodDefinition woodDef, @Nullable Identifier wood,
                                   @Nullable WandCoreDefinition coreDef, @Nullable Identifier core,
                                   @Nullable Float lengthInches, @Nullable WandFlexibility flexibility,
                                   @Nullable WandConfiguration explicit, boolean elder) {
        long seed = seed(wood, core, lengthInches, flexibility);
        float phase = unit(seed, SALT_PHASE);
        if (elder) {
            // One specific wand, not a sample of a make: its own model and art, and no length, bend or
            // grain borrowed from the system every other wand goes through.
            return new WandLook(null, WandAppearance.UNTINTED, WandConfiguration.DEFAULT, -1,
                    1.0f, 1.0f, 0.0f, 0.0f, ELDER_GLINT, WandGlintPattern.SMOKE, ELDER_GLINT_STRENGTH, phase, true);
        }

        int rare = explicit != null ? -1 : WandAppearance.rareIndex(woodDef, seed);
        WandConfiguration configuration = explicit != null ? explicit : WandAppearance.configurationFor(woodDef, rare);
        Identifier grain = woodDef == null ? null : woodDef.appearance().grain().orElse(null);
        int tint = vary(WandAppearance.woodTint(woodDef, wood), (unit(seed, SALT_TINT) * 2.0f - 1.0f) * TINT_VARIATION);
        // Which way a wand bows is the wood's whim; a straight wand has no side to bow to (and no -0.0).
        float bow = bowDegrees(flexibility);
        if (bow != 0.0f && unit(seed, SALT_BOW) < 0.5f) {
            bow = -bow;
        }

        int glint = 0;
        float glintStrength = 0.0f;
        WandGlintPattern pattern = WandGlintPattern.STEADY;
        if (core != null) {
            WandCoreAppearance look = coreDef == null ? WandCoreAppearance.NONE : coreDef.appearance();
            glint = look.glint().map(c -> c & 0xFFFFFF).orElseGet(() -> derivedGlint(core));
            pattern = look.pattern();
            glintStrength = look.strength();
        }

        return new WandLook(grain, tint, configuration, rare,
                lengthScale(lengthInches), girth(flexibility), bow,
                (unit(seed, SALT_TWIST) * 2.0f - 1.0f) * MAX_TWIST_DEGREES,
                glint, pattern, glintStrength, phase, false);
    }

    // ── the channels, each a pure function so tests can pin its shape ────────

    /** Shaft scale for a wand this long; a wand of unknown length is drawn as modelled. */
    public static float lengthScale(@Nullable Float inches) {
        if (inches == null || !Float.isFinite(inches)) {
            return 1.0f;
        }
        float scale = 1.0f + (inches - REFERENCE_INCHES) * SCALE_PER_INCH;
        return Math.max(MIN_LENGTH_SCALE, Math.min(MAX_LENGTH_SCALE, scale));
    }

    /** A wand that gives is a slimmer wand; the stiffest keep the model's full section. */
    public static float girth(@Nullable WandFlexibility flexibility) {
        if (flexibility == null) {
            return 1.0f;
        }
        return switch (flexibility) {
            case UNYIELDING, RIGID -> 1.0f;
            case PLIANT -> 0.95f;
            case SUPPLE -> 0.90f;
            case SPRINGY -> 0.85f;
        };
    }

    /** The bend, unsigned, at each of the grip and the tip joints. An unyielding wand is dead straight. */
    public static float bowDegrees(@Nullable WandFlexibility flexibility) {
        if (flexibility == null) {
            return 0.0f;
        }
        return switch (flexibility) {
            case UNYIELDING -> 0.0f;
            case RIGID -> 0.75f;
            case PLIANT -> 1.75f;
            case SUPPLE -> 3.0f;
            case SPRINGY -> 4.5f;
        };
    }

    // ── seed ─────────────────────────────────────────────────────────────────

    /**
     * A stable number for a make. Built from {@code String} hashes and the length's bits — never an enum's
     * or an object's identity hash, which change between runs.
     */
    public static long seed(@Nullable Identifier wood, @Nullable Identifier core,
                            @Nullable Float lengthInches, @Nullable WandFlexibility flexibility) {
        long h = wood == null ? 0 : wood.toString().hashCode();
        h = h * 31 + (core == null ? 0 : core.toString().hashCode());
        h = h * 31 + (lengthInches == null ? 0 : Float.floatToIntBits(lengthInches));
        h = h * 31 + (flexibility == null ? -1 : flexibility.ordinal());
        return h;
    }

    /** A uniform value in {@code [0, 1)} drawn from the seed; each salt is an independent draw. */
    public static float unit(long seed, long salt) {
        long z = seed + salt * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 40) / (float) (1L << 24);
    }

    // ── colour ───────────────────────────────────────────────────────────────

    private static int vary(int argb, float amount) {
        float f = 1.0f + amount;
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * f));
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * f));
        int b = Math.min(255, Math.round((argb & 0xFF) * f));
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /**
     * A light for a core nobody authored one for: any hue, but muted, so a datapack core reads as a
     * quiet unknown rather than a beacon.
     */
    static int derivedGlint(Identifier core) {
        int hash = core.toString().hashCode();
        float hue = ((hash >>> 8) & 0x3FF) / 1024.0f;
        return WandAppearance.hsb(hue, 0.35f, 0.75f) & 0xFFFFFF;
    }
}
