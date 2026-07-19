package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.client.ability.state.ClientAbilityCache;
import at.koopro.wizardsandbeasts.entity.GeoEntityBase;
import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Thestral — a gaunt, skeletal winged horse, visible only to those who have
 * witnessed death. Calm and neutral; grazes and wanders. Ground walker with
 * folded wings for now (flight reserved for a later pass).
 *
 * <p>Visibility uses vanilla {@link #isInvisible()}/{@link #isInvisibleTo(Player)} — the latter is
 * consulted client-side with the <em>local</em> viewer as the argument (confirmed in both vanilla
 * {@code LivingEntityRenderer} and GeckoLib's {@code GeoEntityRenderer}), so checking the local
 * player's own synced {@link ClientAbilityCache} flag here gives a genuine per-viewer difference with
 * no new render event or renderer changes needed. See {@link at.koopro.wizardsandbeasts.event.beast.ThestralWitnessHandler}
 * for how the flag is granted.
 */
public class ThestralEntity extends GeoEntityBase {

    public static final String WITNESSED_DEATH_FLAG = "witnessed_death";

    private static final RawAnimation IDLE_ANIM = AnimHelper.loop("thestral", "idle");
    private static final RawAnimation WALK_ANIM = AnimHelper.loop("thestral", "walk");
    private static final RawAnimation FLY_ANIM = AnimHelper.loop("thestral", "fly");

    public ThestralEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    public boolean isInvisibleTo(Player player) {
        if (!level().isClientSide()) {
            // No per-viewer differentiation needed server-side; the client-only check below is what
            // actually gates rendering for each observer.
            return true;
        }
        return !ClientAbilityCache.get().abilityFlags().contains(WITNESSED_DEATH_FLAG);
    }

    /**
     * Server-side counterpart to {@link #isInvisibleTo} — render-invisibility alone leaves the entity
     * collidable and clickable for non-witnesses (vanilla hit-testing ignores render-invisible state).
     * Skip mutual push entirely for a non-witness so they walk through the Thestral.
     */
    @Override
    public void push(Entity entity) {
        if (entity instanceof Player player && !PlayerAbilityHelper.hasAbilityFlag(player, WITNESSED_DEATH_FLAG)) {
            return;
        }
        super.push(entity);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!PlayerAbilityHelper.hasAbilityFlag(player, WITNESSED_DEATH_FLAG)) {
            return InteractionResult.PASS;
        }
        return super.mobInteract(player, hand);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<ThestralEntity>("thestral_movement", 5, test -> {
            if (!onGround()) {
                return test.setAndContinue(FLY_ANIM);
            }
            return test.setAndContinue(test.isMoving() ? WALK_ANIM : IDLE_ANIM);
        }));
    }
}
