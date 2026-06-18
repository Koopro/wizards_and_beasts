package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.manager.AnimatableManager;

/** Generic swimmer (AQUATIC body-plan). */
public class GenericAquaticBeastEntity extends GenericBeastEntity {

    public GenericAquaticBeastEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.moveControl = new net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl(this, 85, 10, 0.02f, 0.1f, true);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    protected void addMovementGoals() {
        goalSelector.addGoal(5, new RandomSwimmingGoal(this, 1.0, 20));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        String name = assetName();
        controllers.add(AnimHelper.movementController(name, 5,
                AnimHelper.loop(name, "idle"), AnimHelper.loop(name, "swim")));
    }
}
