package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

/** Generic ground walker (QUADRUPED / BIPED / SERPENTINE / ARTHROPOD / BLOB / WORM body-plans). */
public class GenericGroundBeastEntity extends GenericBeastEntity {

    public GenericGroundBeastEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void addMovementGoals() {
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
    }

    @Override
    protected void addMovementController(AnimatableManager.ControllerRegistrar controllers) {
        String name = assetName();
        // A shapeshifter in someone else's shape moves with that creature's clips (see Guise); otherwise what it is
        // doing picks the loop (CreatureActivity), falling back to walk and idle.
        controllers.add(new AnimationController<GenericBeastEntity>(name + "_movement", 5, state -> {
            Guise guise = Guise.decode(getGuise());
            if (guise != null) {
                state.setControllerSpeed(1.0f);
                return state.setAndContinue(state.isMoving() ? guise.moving() : guise.idle());
            }
            at.koopro.wizardsandbeasts.creature.profile.CreatureActivity.Loop loop =
                    activity().loop(state.isMoving(), clip -> AnimHelper.rigHas(name, clip), "walk");
            state.setControllerSpeed((float) loop.speed());
            return state.setAndContinue(AnimHelper.activityLoop(name, loop.clip()));
        }));
    }
}
