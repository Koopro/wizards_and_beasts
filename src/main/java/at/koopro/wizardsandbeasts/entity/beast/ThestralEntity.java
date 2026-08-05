package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.client.ability.state.ClientAbilityCache;
import at.koopro.wizardsandbeasts.entity.GeoEntityBase;
import at.koopro.wizardsandbeasts.entity.beast.ai.ThestralGrazeGoal;
import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;

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
    public static final String ACTION_CONTROLLER = "thestral_action";

    private static final RawAnimation IDLE_ANIM = AnimHelper.loop("thestral", "idle");
    private static final RawAnimation WALK_ANIM = AnimHelper.loop("thestral", "walk");
    private static final RawAnimation GALLOP_ANIM = AnimHelper.loop("thestral", "gallop");
    private static final RawAnimation FLY_ANIM = AnimHelper.loop("thestral", "fly");
    private static final RawAnimation GRAZE_ANIM = AnimHelper.loop("thestral", "graze");

    /**
     * Limb-swing speed above which the walk clip gives way to the gallop. {@code WalkAnimationState}
     * feeds in {@code min(distance * 4, 1)}, so the stroll goal's 0.7 multiplier settles near 0.56
     * and the panic goal's 1.6 saturates at 1.0 — this sits between them.
     */
    private static final float GALLOP_SWING = 0.75f;

    private static final EntityDataAccessor<Boolean> DATA_GRAZING =
            SynchedEntityData.defineId(ThestralEntity.class, EntityDataSerializers.BOOLEAN);

    public ThestralEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_GRAZING, false);
    }

    public boolean isGrazing() {
        return entityData.get(DATA_GRAZING);
    }

    public void setGrazing(boolean grazing) {
        entityData.set(DATA_GRAZING, grazing);
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

    /** Bolting when struck is what puts the gallop clip on screen; nothing else moves it that fast. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive()) {
            setGrazing(false);
            triggerAnim(ACTION_CONTROLLER, "screech");
        }
        return hurt;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.6));
        goalSelector.addGoal(2, new ThestralGrazeGoal(this));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<ThestralEntity>("thestral_movement", 5, test -> {
            if (!onGround()) {
                return test.setAndContinue(FLY_ANIM);
            }
            if (isGrazing()) {
                return test.setAndContinue(GRAZE_ANIM);
            }
            if (!test.isMoving()) {
                return test.setAndContinue(IDLE_ANIM);
            }
            // walkAnimation is updated client-side for every entity, unlike getDeltaMovement,
            // which is only meaningful on the server for a mob the client interpolates.
            return test.setAndContinue(walkAnimation.speed() >= GALLOP_SWING ? GALLOP_ANIM : WALK_ANIM);
        }));
        // Zero transition ticks: the screech is a reaction, and easing into it reads as a stretch.
        controllers.add(new AnimationController<ThestralEntity>(ACTION_CONTROLLER, 0, state -> PlayState.STOP)
                .triggerableAnim("screech", AnimHelper.playOnce("thestral", "screech")));
    }
}
