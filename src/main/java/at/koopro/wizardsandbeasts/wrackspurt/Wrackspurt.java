package at.koopro.wizardsandbeasts.wrackspurt;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NullMarked;

/**
 * Seeing what is trying not to be seen.
 *
 * <p>Luna wears Spectrespecs and looks for Wrackspurts; a Dirigible Plum is the same idea eaten
 * rather than worn. What it actually does is narrow and useful: for forty-five seconds, anything
 * nearby that is <em>concealing itself</em> is outlined — and only to you.
 *
 * <p>Deliberately not "see all entities". A wallhack would trivialise every hiding mechanic in the
 * mod and would not be Luna-ish at all; she does not see through walls, she notices things other
 * people have decided are not there. So the rule is concealment, not proximity: an ordinary zombie
 * three blocks away is not outlined, and a wizard under a Cloak is.
 */
@NullMarked
public final class Wrackspurt {

    /** How long the sight lasts. */
    public static final int SIGHT_TICKS = 900;      // 45s

    /** The bob on eating. Just long enough to leave the ground. */
    public static final int LEVITATION_TICKS = 20;  // 1s

    /** The muddle afterwards. Amplifier 0 — mad, not incapacitated. */
    public static final int CONFUSION_TICKS = 100;  // 5s

    /** How far the sight carries. */
    public static final double RANGE = 16.0;

    /** Wrackspurt violet, packed opaque by the provider. */
    public static final int OUTLINE_RGB = 0xB07ACB;

    /**
     * Effects that count as concealment.
     *
     * <p>A tag rather than a hard-coded list, so a pack's own hiding charm is revealed by a Dirigible
     * Plum without this class knowing it exists — and so a pack that thinks one of the mod's own
     * effects should stay hidden can take it out.
     */
    public static final TagKey<MobEffect> CONCEALMENT = TagKey.create(
            Registries.MOB_EFFECT,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "wrackspurt_reveals"));

    private Wrackspurt() {}

    /** Whether this viewer currently has the sight. */
    public static boolean canSee(LivingEntity viewer) {
        return viewer.hasEffect(ModEffects.WRACKSPURT_SIGHT);
    }

    /**
     * Whether {@code target} is hiding from ordinary eyes.
     *
     * <p>{@code isInvisible()} rather than the potion effect, because the Invisibility Cloak sets the
     * flag and casts no charm — the same reason the Sneakoscope reads it. Everything else comes off
     * the {@link #CONCEALMENT} tag.
     */
    public static boolean isConcealed(Entity target) {
        if (target.isInvisible()) {
            return true;
        }
        if (!(target instanceof LivingEntity living)) {
            return false;
        }
        for (MobEffectInstance instance : living.getActiveEffects()) {
            if (instance.getEffect().is(CONCEALMENT)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code target} is close enough to notice. */
    public static boolean inRange(Entity viewer, Entity target) {
        return viewer.distanceToSqr(target) <= RANGE * RANGE;
    }
}
