package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.creature.CreatureDefinition;
import at.koopro.wizardsandbeasts.creature.DragonTraits;
import at.koopro.wizardsandbeasts.entity.creature.ai.DragonBreathGoal;
import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.object.PlayState;

/**
 * One breed-parameterised dragon entity for all ten canonical breeds — the canonical "one entity, not
 * ten classes" decision. A sibling to {@link GenericFlyingBeastEntity}: it inherits the flight
 * navigation/move-control + idle/fly animation, and layers on the shared dragon fire/venom kit so the
 * generic flyer stays clean.
 *
 * <p>Breed behaviour is read from the bound {@link CreatureDefinition}'s nested {@link DragonTraits}.
 * The fire-breath goal is injected via {@link #addMovementGoals()} (the only goal-registration hook
 * exposed by the {@code final} {@code registerGoals()} of {@link GenericBeastEntity}); melee venom is
 * applied on hit. Breath/bite animations are driven through GeckoLib's built-in {@code triggerAnim}
 * sync — no bespoke packet.
 */
public class DragonEntity extends GenericFlyingBeastEntity {

    /** Controller name carrying the triggerable {@code breath}/{@code bite} animations. */
    public static final String ACTION_CONTROLLER = "dragon_action";

    public DragonEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    /** This breed's dragon-specific tuning, or {@code null} if the definition omits the {@code dragon} block. */
    @Nullable
    public DragonTraits dragonTraits() {
        CreatureDefinition def = definition();
        return def != null ? def.dragon().orElse(null) : null;
    }

    // ── goals ─────────────────────────────────────────────────────────────────

    @Override
    protected void addMovementGoals() {
        super.addMovementGoals(); // inherited flying wander
        goalSelector.addGoal(3, new DragonBreathGoal(this));
    }

    // ── melee venom ─────────────────────────────────────────────────────────────

    @Override
    public boolean doHurtTarget(@NonNull ServerLevel level, @NonNull Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity victim) {
            applyVenom(victim);
            triggerAnim(ACTION_CONTROLLER, "bite");
        }
        return hit;
    }

    private void applyVenom(LivingEntity victim) {
        DragonTraits traits = dragonTraits();
        if (traits == null || traits.biteVenom().isEmpty()) {
            return;
        }
        DragonTraits.BiteVenom venom = traits.biteVenom().get();
        ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT, venom.effect());
        BuiltInRegistries.MOB_EFFECT.get(key).ifPresent(holder ->
                victim.addEffect(new MobEffectInstance(holder, venom.durationTicks(), venom.amplifier())));
    }

    // ── GeckoLib ─────────────────────────────────────────────────────────────

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers); // inherited idle/fly movement controller
        String name = assetName();
        controllers.add(new AnimationController<DragonEntity>(ACTION_CONTROLLER, 0, state -> PlayState.STOP)
                .triggerableAnim("breath", AnimHelper.playOnce(name, "breath"))
                .triggerableAnim("bite", AnimHelper.playOnce(name, "bite")));
    }
}
