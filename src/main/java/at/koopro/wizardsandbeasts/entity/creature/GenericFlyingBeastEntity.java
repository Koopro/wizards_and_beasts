package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.creature.Trait;
import at.koopro.wizardsandbeasts.creature.profile.CreatureActivity;
import at.koopro.wizardsandbeasts.entity.BeastNavigation;
import at.koopro.wizardsandbeasts.entity.flight.FlightBeats;
import at.koopro.wizardsandbeasts.entity.flight.FlightLoop;
import at.koopro.wizardsandbeasts.entity.flight.WingedPursuitGoal;
import at.koopro.wizardsandbeasts.entity.flight.WingedWalker;
import at.koopro.wizardsandbeasts.entity.flight.WingedWalkerFlightGoal;
import at.koopro.wizardsandbeasts.entity.flight.WingedWalkerMoveControl;
import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;

/**
 * Generic flyer (WINGED_QUADRUPED / AVIAN / INSECTOID_FLYER / flying BLOB body-plans), in two kinds.
 *
 * <p><b>Small fliers</b> (billywig, doxy, fairy, snidget…) live in the air: one flying control, the flying path
 * finder, hover and flit.
 *
 * <p><b>Large fliers</b> ({@link #walksTheGround()}: every dragon, the winged horses, the griffin, the thunderbird,
 * the snallygaster, the hippogriff) are {@link WingedWalker winged walkers} (BEAST_RENAISSANCE §4.6). They stand and
 * walk with gravity on ordinary ground navigation, take off for bounded flights ({@link WingedWalkerFlightGoal}) or
 * to hunt what they cannot reach on foot ({@link WingedPursuitGoal}), and come down again. Before this they ran the
 * small fliers' single flying control: they floated off every ledge, flapped while walking, and a dragon never
 * visibly took off or landed.
 */
public class GenericFlyingBeastEntity extends GenericBeastEntity implements WingedWalker {

    /** Bodies at least this wide walk the ground and fly by choice; smaller ones live in the air. */
    public static final float LARGE_BODY_WIDTH = 1.5f;

    /** Server state, not saved: a creature loaded mid-air is not flying, gets its gravity back and comes down. */
    private boolean flying;
    private final FlightBeats beats = new FlightBeats();

    public GenericFlyingBeastEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.moveControl = walksTheGround() ? new WingedWalkerMoveControl<>(this)
                : new FlyingMoveControl(this, 10, false);
    }

    /**
     * Whether this body is big enough to live on the ground and fly by choice. Read from the entity type, which is
     * fixed, because navigation and goals are chosen once in the constructor; a body scale applied later (a young
     * creature, a Creature Lab size) must not flip the answer.
     */
    public final boolean walksTheGround() {
        return getType().getDimensions().width() >= LARGE_BODY_WIDTH;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        if (walksTheGround()) {
            GroundPathNavigation navigation = new GroundPathNavigation(this, level);
            navigation.setCanFloat(true);
            return navigation;
        }
        return BeastNavigation.flying(this, level);
    }

    @Override
    protected void addMovementGoals() {
        if (walksTheGround()) {
            // Below the dive-bomb ability (2) so its stoop can take over, above the melee goal (4) so a target out
            // of reach on foot is not walked at forever.
            goalSelector.addGoal(3, new WingedPursuitGoal<>(this));
            // Above the ecology goal (3), which would have it flee a blow on foot.
            goalSelector.addGoal(2, WingedWalkerFlightGoal.startled(this));
            goalSelector.addGoal(5, new WingedWalkerFlightGoal<>(this, takeoffChance()));
            goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        } else {
            // Small fliers keep the planned flight, which is cheap for them and threads them between leaves.
            goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        }
    }

    /** One in this many ticks, on average, an idle large flier takes off. */
    protected int takeoffChance() {
        return 600;
    }

    @Override
    protected void applyDefinition() {
        super.applyDefinition();
        if (walksTheGround()) {
            // A body this size steps over a block; at the default 0.6 a dragon stopped at every kerb.
            AttributeInstance step = getAttribute(Attributes.STEP_HEIGHT);
            if (step != null && step.getBaseValue() < 1.0) {
                step.setBaseValue(1.0);
            }
        }
    }

    // ── winged walker ─────────────────────────────────────────────────────────

    @Override
    public boolean isFlying() {
        return flying;
    }

    @Override
    public void setFlying(boolean flying) {
        this.flying = flying;
        if (!flying) {
            setNoGravity(false);
        }
    }

    @Override
    public void flyRidden(Vec3 input, float speed) {
        travelFlying(input, speed);
    }

    /** Bigger bodies cruise higher and range wider: a horse-sized flier 9–16 blocks up, a dragon 11–20. */
    @Override
    public int cruiseAltitude() {
        return 6 + Math.round(getType().getDimensions().width() * 2.0f);
    }

    @Override
    public int cruiseAltitudeSpan() {
        return 7 + Math.round(getType().getDimensions().width());
    }

    @Override
    public double flightReach() {
        return 14.0 + getType().getDimensions().width() * 5.0;
    }

    @Override
    public boolean perches() {
        return has(Trait.PERCHER);
    }

    /** The thunderbird makes its storm as it flies, so it never fights standing. */
    @Override
    public boolean fightsFromTheAir() {
        for (at.koopro.wizardsandbeasts.creature.ability.CreatureAbility ability : abilities()) {
            if (ability instanceof at.koopro.wizardsandbeasts.creature.ability.ThunderbirdStorm) {
                return true;
            }
        }
        return false;
    }

    /** Plays a takeoff or landing beat; the default asks for the clip only if this creature declares it. */
    protected void flightBeat(String clip) {
        triggerDeclared(clip);
    }

    @Override
    public void tick() {
        super.tick();
        if (walksTheGround() && level() instanceof ServerLevel) {
            if (isVehicle()) {
                flying = false;
            }
            beats.update(this, this::flightBeat);
        }
    }

    /**
     * A flier is never hurt by falling. {@code FlyingMoveControl} hands gravity back between flight targets, so every
     * wander ended in a drop from up to ten blocks, and every dragon, winged horse and griffin took fall damage after
     * it (Beast Renaissance D7). The Hippogriff, Thestral and Phoenix already opted out; this covers the rest.
     */
    @Override
    public boolean causeFallDamage(double distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    protected void addMovementController(AnimatableManager.ControllerRegistrar controllers) {
        String name = assetName();
        if (!walksTheGround()) {
            controllers.add(AnimHelper.<GenericBeastEntity>activityController(name, 5, "fly"));
            return;
        }
        controllers.add(new AnimationController<GenericBeastEntity>(name + "_movement", 5, state -> {
            double dx = getX() - xo;
            double dz = getZ() - zo;
            CreatureActivity.Loop loop = FlightLoop.pick(onGround() || isInWater(), state.isMoving(), getY() - yo,
                    dx * dx + dz * dz > 0.01, activity(), clip -> AnimHelper.rigHas(name, clip));
            state.setControllerSpeed((float) loop.speed());
            return state.setAndContinue(AnimHelper.activityLoop(name, loop.clip()));
        }));
    }
}
