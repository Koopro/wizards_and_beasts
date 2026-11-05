package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.entity.BeastNavigation;
import at.koopro.wizardsandbeasts.entity.creature.ai.DirectFlightWanderGoal;
import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.manager.AnimatableManager;

/** Generic flyer (WINGED_QUADRUPED / AVIAN / INSECTOID_FLYER / flying BLOB body-plans). */
public class GenericFlyingBeastEntity extends GenericBeastEntity {

    public GenericFlyingBeastEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 10, false);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return BeastNavigation.flying(this, level);
    }

    @Override
    protected void addMovementGoals() {
        // A large body wanders by straight, collision-checked flight (vanilla's Ghast pattern); planning each wander
        // with the 3D path finder costs milliseconds for a dragon-sized box. Small fliers keep the planned flight,
        // which is cheap for them and threads them between leaves and branches.
        if (getBbWidth() >= DirectFlightWanderGoal.LARGE_BODY_WIDTH) {
            goalSelector.addGoal(5, new DirectFlightWanderGoal(this, 1.0));
        } else {
            goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0));
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
        controllers.add(AnimHelper.<GenericBeastEntity>activityController(name, 5, "fly"));
    }
}
