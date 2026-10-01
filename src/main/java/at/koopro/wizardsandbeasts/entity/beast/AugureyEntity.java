package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.creature.wildlife.SignatureRules;
import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeWorld;
import at.koopro.wizardsandbeasts.entity.BeastNavigation;
import at.koopro.wizardsandbeasts.entity.GeoEntityBase;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;

/**
 * Augurey (Irish Phoenix) — a thin, mournful, greenish-black bird whose low throbbing cry was once believed to foretell
 * death. It foretells rain (Fantastic Beasts): the cry is the signature, and it is a true forecast.
 *
 * <ul>
 *   <li><b>Forecast:</b> while the sky is clear and rain is due within {@link SignatureRules#AUGUREY_FORECAST_TICKS},
 *       it cries every so often, and everyone in earshot who can see it has witnessed its signature.</li>
 *   <li><b>Weather:</b> it flies only in rain. In fine weather it keeps to the ground near its roost — the thorny
 *       thicket it was found in — and does not take off.</li>
 *   <li><b>Shy:</b> it moves away from anyone who comes at it upright; someone crouched and quiet can watch it.</li>
 * </ul>
 *
 * <p>Deliberately absent: any harm from the cry (canon is explicit that it only means rain), taming, and drops.
 */
public class AugureyEntity extends GeoEntityBase {

    private static final RawAnimation IDLE_ANIM = AnimHelper.loop("augurey", "idle");
    private static final RawAnimation FLY_ANIM = AnimHelper.loop("augurey", "fly");
    /** One-shots (hit, death), registered after movement so they override the loop. */
    private static final String ACTION = "augurey_action";

    /** Roost radius: how far from its thicket an Augurey wanders in fine weather. */
    static final int ROOST_RADIUS = 4;
    /** Ticks between cries while rain is coming. */
    static final int CRY_INTERVAL = 300;
    /** How far a cry carries for the bestiary. */
    static final double CRY_WITNESS_RANGE = 32.0;

    private int nextCry = CRY_INTERVAL;

    public AugureyEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 10, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0)
                .add(Attributes.FLYING_SPEED, 0.5)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return BeastNavigation.flying(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 8.0f, 1.0, 1.2,
                living -> living instanceof Player player && !player.isCrouching() && !player.isSpectator()
                        && !player.isCreative()));
        goalSelector.addGoal(2, new WaterAvoidingRandomFlyingGoal(this, 1.0) {
            @Override
            public boolean canUse() {
                return inRain() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return inRain() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return !inRain() && super.canUse();
            }
        });
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 7.0f));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    /** Rain falling where it is, which is the only weather it flies in. */
    boolean inRain() {
        return level().isRainingAt(blockPosition().above());
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel level) || !isAlive()) {
            return;
        }
        if (!hasHome()) {
            // Its roost is where it was found: the thicket it came out of.
            setHomeTo(blockPosition(), ROOST_RADIUS);
        }
        if (!inRain() && !onGround() && !isInWater() && getNavigation().isDone()) {
            // Fine weather grounds it: settle rather than hang in the air.
            setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
        }
        if (--nextCry <= 0) {
            nextCry = CRY_INTERVAL + getRandom().nextInt(CRY_INTERVAL / 2);
            cryIfRainComing(WildlifeWorld.rainComing(level));
        }
    }

    /**
     * The forecast: cries when rain is coming, and stays quiet otherwise. The weather is passed in so the rule can be
     * exercised without changing a world's weather under every other scenario sharing it.
     *
     * @return whether it cried
     */
    public boolean cryIfRainComing(boolean rainComing) {
        if (!rainComing || !(level() instanceof ServerLevel level)) {
            return false;
        }
        level.playSound(null, getX(), getY(), getZ(), ModSounds.AUGUREY_CRY.get(), SoundSource.NEUTRAL, 1.6f, 1.0f);
        BestiaryDiscoveryHandler.signatureSeenByNearby(this, CRY_WITNESS_RANGE);
        return true;
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("NextCry", nextCry);
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        nextCry = input.getIntOr("NextCry", CRY_INTERVAL);
    }

    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive()) triggerAnim(ACTION, "hit");
        return hurt;
    }

    @Override
    public void die(@NonNull DamageSource cause) {
        if (!level().isClientSide()) {
            triggerAnim(ACTION, "death");
        }
        super.die(cause);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(AnimHelper.movementController("augurey", 5, IDLE_ANIM, FLY_ANIM));
        controllers.add(new AnimationController<AugureyEntity>(ACTION, 0, test -> PlayState.STOP)
                .triggerableAnim("hit", AnimHelper.playOnce("augurey", "hit"))
                .triggerableAnim("death", AnimHelper.playOnce("augurey", "death")));
    }
}
