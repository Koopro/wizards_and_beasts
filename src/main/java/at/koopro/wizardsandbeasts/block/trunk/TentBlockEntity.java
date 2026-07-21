package at.koopro.wizardsandbeasts.block.trunk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Preview-harness block entity for the tent models (exterior geometry only — see the Tent Models prompt).
 * Deliberately inert: no gameplay, no pocket-space linkage. One class backs both {@code tent_canvas} and
 * {@code tent_grand}, each under its own {@link BlockEntityType}; which model renders is decided entirely by
 * which {@code DefaultedBlockGeoModel} identifier the renderer factory for that type closes over
 * ({@code GeoRendererHelper.simpleBlock}), mirroring how the mod's simple GeckoLib entities already pick
 * their model — so this class carries no variant field.
 *
 * <p>Rotation needs no code here either: {@code GeoBlockRenderer} auto-detects
 * {@code BlockStateProperties.HORIZONTAL_FACING} (the same property {@link AbstractTentBlock} exposes as
 * {@link AbstractTentBlock#FACING}) and captures it into the render state itself.
 *
 * <p>The render-bounds inflation (the tent is 2-3 blocks across on a single-block hitbox) is NOT here —
 * {@code IBlockEntityRendererExtension.getRenderBoundingBox(T)} is a renderer-side default method, not a
 * {@link BlockEntity} one, in this NeoForge version. See {@code GeoRendererHelper.simpleBlock}.
 */
public final class TentBlockEntity extends BlockEntity implements GeoBlockEntity {

    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    public TentBlockEntity(BlockEntityType<?> type, @NonNull BlockPos pos, @NonNull BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Only "idle" is wired. "flap_open" exists in the .animation.json so the rig supports flap
        // actuation later, but nothing here ever fires it (no trigger logic in this prompt, §4).
        String name = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(getType()).getPath();
        RawAnimation idle = RawAnimation.begin().thenLoop("animation." + name + ".idle");
        controllers.add(new AnimationController<>(name + "_idle", 5, state -> state.setAndContinue(idle)));
    }

    @Override
    public @NonNull AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableCache;
    }
}
