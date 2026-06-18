package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
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
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void addMovementGoals() {
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        String name = assetName();
        controllers.add(AnimHelper.movementController(name, 5,
                AnimHelper.loop(name, "idle"), AnimHelper.loop(name, "fly")));
    }
}
