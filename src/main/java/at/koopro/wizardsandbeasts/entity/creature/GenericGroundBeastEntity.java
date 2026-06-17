package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.manager.AnimatableManager;

/** Generic ground walker (QUADRUPED / BIPED / SERPENTINE / ARTHROPOD / BLOB / WORM body-plans). */
public class GenericGroundBeastEntity extends GenericBeastEntity {

    public GenericGroundBeastEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        registerCommonGoals();
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        String name = assetName();
        controllers.add(AnimHelper.movementController(name, 5,
                AnimHelper.loop(name, "idle"), AnimHelper.loop(name, "walk")));
    }
}
