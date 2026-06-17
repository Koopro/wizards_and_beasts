package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.manager.AnimatableManager;

/** Generic near-static creature (SESSILE body-plan): idle animation only, no wander goals. */
public class GenericSessileBeastEntity extends GenericBeastEntity {

    public GenericSessileBeastEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        registerCommonGoals();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        String name = assetName();
        controllers.add(AnimHelper.idleController(name, 5, AnimHelper.loop(name, "idle")));
    }
}
