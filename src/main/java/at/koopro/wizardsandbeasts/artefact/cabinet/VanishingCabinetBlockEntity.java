package at.koopro.wizardsandbeasts.artefact.cabinet;

import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * The lower half of a Vanishing Cabinet. It remembers who placed it -- the only person who may link it -- and
 * nothing else: whether it is linked, and to where, lives in {@link CabinetPairs}.
 *
 * <p>It also draws the whole cabinet, both halves, through its GeckoLib rig ({@code tools/station_rigs.py}), whose
 * doors open when someone passes through.
 */
public class VanishingCabinetBlockEntity extends BlockEntity implements GeoBlockEntity {

    /** Someone stepped through: the doors are thrown open and slammed. */
    public static final String CLIP_PASS = "pass";
    private static final String CONTROLLER = "doors";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.vanishing_cabinet.idle");

    private @Nullable UUID owner;
    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    public VanishingCabinetBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        super(ModBlockEntities.VANISHING_CABINET.get(), pos, state);
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    /** Plays a one-shot clip for everyone watching; call on the server. */
    public void play(@NonNull String clip) {
        triggerAnim(CONTROLLER, clip);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<VanishingCabinetBlockEntity>(CONTROLLER, 0, state -> state.setAndContinue(IDLE))
                .triggerableAnim(CLIP_PASS, RawAnimation.begin().thenPlay("animation.vanishing_cabinet.pass")));
    }

    @Override
    public @NonNull AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableCache;
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        if (owner != null) {
            output.store("owner", UUIDUtil.CODEC, owner);
        }
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
    }
}
