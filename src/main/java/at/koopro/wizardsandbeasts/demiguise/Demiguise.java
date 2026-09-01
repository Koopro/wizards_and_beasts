package at.koopro.wizardsandbeasts.demiguise;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import org.jspecify.annotations.NullMarked;

/**
 * Every number and shared move in the Demiguise kit, in one place and with no duplication between
 * the three ways a player can vanish with one.
 *
 * <p>The three are deliberately different bargains rather than three sizes of the same one:
 *
 * <ul>
 *   <li><b>The hair itself</b> — five seconds, plus camouflage, and it costs you the hair. Ninety
 *       seconds before you can do it again. The strongest effect and the only one that consumes
 *       something.</li>
 *   <li><b>The Weave trim</b> — eight seconds on a crouch, every sixty. Longer and free, but plain
 *       invisibility with no camouflage, and it announces itself every time you sneak.</li>
 *   <li><b>A charged cloak</b> — indefinite while it lasts, and it lasts exactly as long as the
 *       hairs woven into it. See {@code CloakCharges}.</li>
 * </ul>
 */
@NullMarked
public final class Demiguise {

    // ── the hair, used directly ──
    /** Invisibility from one hair. */
    public static final int HAIR_INVISIBILITY_TICKS = 100;   // 5s
    /** Camouflage from one hair, matched to the invisibility so the two end together. */
    public static final int HAIR_CAMOUFLAGE_TICKS = 100;     // 5s
    /** Before another hair can be spent. */
    public static final int HAIR_COOLDOWN_TICKS = 1800;      // 90s
    /**
     * Ticks the hair takes to fade out of your hand before it is spent.
     *
     * <p>Not flavour: the item is <em>held</em> for this long, and the model dispatches over the
     * remaining use time so the sprite actually thins out as it goes. Long enough to see, short
     * enough that it is not a channel you can be interrupted out of by walking.
     */
    public static final int HAIR_FADE_TICKS = 24;
    /** Fade frames the item model dispatches over. */
    public static final int FADE_FRAMES = 5;

    // ── the Weave trim ──
    /** Invisibility from crouching in Demiguise Weave. */
    public static final int WEAVE_INVISIBILITY_TICKS = 160;  // 8s
    /** Before the Weave answers a crouch again. */
    public static final int WEAVE_COOLDOWN_TICKS = 1200;     // 60s
    /**
     * Armour pieces that must carry the Weave before it does anything.
     *
     * <p>One is not enough and four is punitive. Two is the level at which a player has clearly
     * chosen this over a trim they might otherwise want, which is the point at which it should pay.
     */
    public static final int WEAVE_PIECES_REQUIRED = 2;

    /** The trim material that counts as Demiguise Weave. */
    public static final ResourceKey<TrimMaterial> WEAVE_MATERIAL = ResourceKey.create(
            Registries.TRIM_MATERIAL,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "demiguise"));

    private Demiguise() {}

    /**
     * Hides {@code entity} for {@code invisibilityTicks}, optionally with camouflage on top.
     *
     * <p>{@code false} on the ambient and visible flags: a Demiguise does not trail particles, and a
     * concealment that draws a cloud of swirls around the player is not one.
     */
    public static void conceal(LivingEntity entity, int invisibilityTicks, int camouflageTicks) {
        entity.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY, invisibilityTicks, 0, false, false, true));
        if (camouflageTicks > 0) {
            entity.addEffect(new MobEffectInstance(
                    ModEffects.CAMOUFLAGE, camouflageTicks, 0, false, false, true));
            // Cancelling future target selection is not enough on its own: whatever was already
            // walking toward you would keep walking, and the effect would feel broken exactly when
            // it was bought to save you.
            CamouflageHandler.forgetTargets(entity);
        }
    }

    /** The sound and the dust of something ceasing to be looked at. */
    public static void vanishEffects(LivingEntity entity) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.35f, 1.9f);
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.WHITE_ASH,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    14, 0.3, 0.5, 0.3, 0.005);
        }
    }
}
