package at.koopro.wizardsandbeasts.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base class for GeckoLib-animated PathfinderMob entities.
 * Handles the AnimatableInstanceCache boilerplate.
 */
public abstract class GeoEntityBase extends PathfinderMob implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected GeoEntityBase(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
