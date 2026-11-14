package at.koopro.wizardsandbeasts.block.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** The moving part of an {@link AnimatedDecoBlock}: its rig's idle clip, looping, and nothing to save. */
@NullMarked
public class AnimatedDecoBlockEntity extends BlockEntity implements GeoBlockEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final RawAnimation idle;

    public AnimatedDecoBlockEntity(BlockPos pos, BlockState state) {
        super(((AnimatedDecoBlock) state.getBlock()).type(), pos, state);
        this.idle = RawAnimation.begin().thenLoop("animation." + ((AnimatedDecoBlock) state.getBlock()).rig() + ".idle");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<AnimatedDecoBlockEntity>("idle", 0, state -> state.setAndContinue(idle)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
