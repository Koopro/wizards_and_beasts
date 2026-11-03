package at.koopro.wizardsandbeasts.block.floo;

import at.koopro.wizardsandbeasts.network.floo.FlooBlockSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The hearth's network state, and the roar: a GeckoLib overlay ({@code tools/station_rigs.py}) drawn over the baked
 * hearth model, empty at rest, that throws green flame out of the recess when a traveller leaves or arrives.
 */
public class FlooFireplaceBlockEntity extends BlockEntity implements GeoBlockEntity {

    /** A traveller left or arrived: the hearth roars. */
    public static final String CLIP_ROAR = "roar";
    private static final String CONTROLLER = "roar";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.floo_roar.idle");
    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    /** Plays a one-shot clip for everyone watching; call on the server. */
    public void play(@NonNull String clip) {
        triggerAnim(CONTROLLER, clip);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<FlooFireplaceBlockEntity>(CONTROLLER, 0, state -> state.setAndContinue(IDLE))
                .triggerableAnim(CLIP_ROAR, RawAnimation.begin().thenPlay("animation.floo_roar.roar")));
    }

    @Override
    public @NonNull AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableCache;
    }

    /**
     * How long a hearth stays green when nothing has configured it.
     *
     * <p>Ninety seconds: a lit hearth has to outlast opening the destination list, reading it, typing
     * an address and standing through the departure windup, and 600 ticks did not - a hearth lit while
     * the player went to find the name they wanted went out under them.
     */
    public static final int DEFAULT_LIT_TIMEOUT_TICKS = 20 * 90;

    /**
     * The configured hearth lifetime.
     *
     * <p>A method rather than a constant because {@code Config} is populated by a load event, and a
     * constant captured at class-init would freeze whatever the field happened to hold at the moment
     * this class was first touched.
     */
    public static int litTimeoutTicks() {
        int configured = at.koopro.wizardsandbeasts.Config.flooLitTimeoutTicks;
        return configured > 0 ? configured : DEFAULT_LIT_TIMEOUT_TICKS;
    }

    private String networkAddress = "";
    private boolean registered = false;
    private boolean enabled = true;
    private int litTicksRemaining = 0;

    public FlooFireplaceBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        super(ModBlockEntities.FLOO_FIREPLACE.get(), pos, state);
    }

    /**
     * Runs the hearth's own lit countdown — but only for a hearth with no flames in front of it.
     *
     * <p>{@link FlooFlamesBlock} owns the lifetime of a Floo fire: it holds the charges, it schedules
     * the burn-down, and it darkens the hearth when it goes out. Letting this timer keep counting
     * underneath would make the two disagree — the hearth would go dark after its own timeout with
     * green fire still burning in its opening — so while flames are lit the timer is simply held at
     * full.
     *
     * <p>It is not dead code: a hearth can be {@code LIT} with nothing in front of it, from a world
     * saved before flames existed or from a {@code /setblock}, and this is what eventually tidies
     * that up.
     */
    public static void serverTick(@NonNull Level level, @NonNull BlockPos pos,
                                   @NonNull BlockState state, @NonNull FlooFireplaceBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!state.getValue(FlooFireplaceBlock.LIT)) return;
        if (FlooFlamesBlock.isLitAt(sl, pos, state.getValue(FlooFireplaceBlock.FACING))) {
            if (be.litTicksRemaining != litTimeoutTicks()) {
                be.setLitTicksRemaining(litTimeoutTicks());
            }
            return;
        }
        be.litTicksRemaining--;
        if (be.litTicksRemaining <= 0) {
            be.litTicksRemaining = 0;
            level.setBlock(pos, state.setValue(FlooFireplaceBlock.LIT, false), 3);
            be.setChanged();
            FlooBlockSyncS2CPayload.sendToNear(sl, pos, false, 0);
        }
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store("networkAddress", Codec.STRING, networkAddress);
        output.store("registered", Codec.BOOL, registered);
        output.store("enabled", Codec.BOOL, enabled);
        output.store("litTicksRemaining", Codec.INT, litTicksRemaining);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        networkAddress = input.read("networkAddress", Codec.STRING).orElse("");
        registered = input.read("registered", Codec.BOOL).orElse(false);
        enabled = input.read("enabled", Codec.BOOL).orElse(true);
        litTicksRemaining = input.read("litTicksRemaining", Codec.INT).orElse(0);
    }

    @NonNull
    public String getNetworkAddress() {
        return networkAddress;
    }

    public void setNetworkAddress(@NonNull String networkAddress) {
        this.networkAddress = networkAddress;
        setChanged();
    }

    public boolean isRegistered() {
        return registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
        setChanged();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        setChanged();
    }

    public void setLitTicksRemaining(int litTicksRemaining) {
        this.litTicksRemaining = litTicksRemaining;
        setChanged();
    }
}
