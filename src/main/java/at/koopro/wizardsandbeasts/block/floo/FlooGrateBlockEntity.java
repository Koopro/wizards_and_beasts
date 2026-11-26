package at.koopro.wizardsandbeasts.block.floo;

import at.koopro.wizardsandbeasts.floo.hearth.HearthPatterns;
import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * The grate's memory: which hearth style the build around it completes ({@code null} while it is not a finished
 * hearth), how big that hearth's opening is (the roar is shaped to it) and when the current roar began (so the renderer
 * can draw the flames rising).
 *
 * <p>The build is checked against {@link HearthPatterns} once a second, staggered by position so a room of grates does
 * not check on the same tick. A grate is cheap to check (about sixty block reads), and polling keeps one rule instead
 * of a block-change listener per hearth. A hearth broken while its green fire burns drops back to an ordinary fire:
 * an ordinary grate can burn anywhere, only a whole hearth carries the Floo.
 */
@NullMarked
public class FlooGrateBlockEntity extends BlockEntity {

    /** Burrow hearth (CANON.md): a 3 x 3 walk-in opening. */
    public static final int DEFAULT_OPENING = 3;
    /** How long a thrown pinch keeps the fire green if nobody steps in: thirty seconds. */
    public static final int PINCH_TICKS = 600;
    /** How long a hearth flares green when somebody arrives out of it: four seconds. */
    public static final int ARRIVAL_FLARE_TICKS = 80;

    private int openingWidth = DEFAULT_OPENING;
    private int openingHeight = DEFAULT_OPENING;
    private long roarSince;
    private @Nullable Identifier style;
    /** Network side (moved here from the retired 1x1 hearth): the address this hearth answers to. */
    private String networkAddress = "";
    private boolean registered;
    private boolean enabled = true;
    /** Ticks the green fire has left, and the fire it falls back to when they run out. */
    private int greenTicks;
    private HearthFire after = HearthFire.WARM;

    public FlooGrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLOO_GRATE.get(), pos, state);
    }

    public int openingWidth() {
        return openingWidth;
    }

    public int openingHeight() {
        return openingHeight;
    }

    public long roarSince() {
        return roarSince;
    }

    /** The style of the finished hearth this grate stands in, or empty while the build is incomplete. */
    public Optional<Identifier> style() {
        return Optional.ofNullable(style);
    }

    public boolean formed() {
        return style != null;
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, FlooGrateBlockEntity grate) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (state.getValue(FlooGrateBlock.FIRE) == HearthFire.FLOO && grate.greenTicks > 0 && --grate.greenTicks == 0) {
            grate.fallBack(server);
            return;
        }
        if (Math.floorMod(server.getGameTime() + pos.asLong(), 20L) == 0L) {
            grate.recheck(server, state);
        }
    }

    /** Checks the build now; returns whether it is a finished hearth. */
    public boolean recheck(ServerLevel level, BlockState state) {
        Optional<HearthPatterns.Match> match = HearthPatterns.match(level, worldPosition, state.getValue(FlooGrateBlock.FACING));
        Identifier now = match.map(HearthPatterns.Match::style).orElse(null);
        int width = match.map(m -> m.pattern().openingWidth()).orElse(DEFAULT_OPENING);
        int height = match.map(m -> m.pattern().openingHeight()).orElse(DEFAULT_OPENING);
        if (!Objects.equals(now, style) || width != openingWidth || height != openingHeight) {
            style = now;
            openingWidth = width;
            openingHeight = height;
            changed();
        }
        if (now == null && state.getValue(FlooGrateBlock.FIRE) == HearthFire.FLOO) {
            after = HearthFire.WARM;
            fallBack(level);
        }
        return now != null;
    }

    public String getNetworkAddress() {
        return networkAddress;
    }

    public void setNetworkAddress(String address) {
        networkAddress = address;
        changed();
    }

    public boolean isRegistered() {
        return registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
        changed();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        changed();
    }

    public int greenTicks() {
        return greenTicks;
    }

    /**
     * A pinch thrown into the ordinary fire: it roars emerald for one traveller (CoS ch. 4), then falls back to the
     * ordinary fire. Only from {@link HearthFire#WARM}; returns whether it took.
     */
    public boolean throwPinch(ServerLevel level) {
        BlockState state = getBlockState();
        if (state.getValue(FlooGrateBlock.FIRE) != HearthFire.WARM) {
            return false;
        }
        goGreen(level, state, PINCH_TICKS, HearthFire.WARM);
        return true;
    }

    /** Somebody arrives out of this hearth: it flares green whatever it was doing, then returns to that. */
    public void flareForArrival(ServerLevel level) {
        BlockState state = getBlockState();
        HearthFire was = state.getValue(FlooGrateBlock.FIRE);
        goGreen(level, state, ARRIVAL_FLARE_TICKS, was == HearthFire.FLOO ? after : was);
    }

    /** The traveller has gone: the pinch is spent and the fire falls back at once. */
    public void spendPinch(ServerLevel level) {
        greenTicks = 0;
        fallBack(level);
    }

    private void goGreen(ServerLevel level, BlockState state, int ticks, HearthFire fallBackTo) {
        greenTicks = ticks;
        after = fallBackTo;
        roarSince = level.getGameTime();
        level.setBlock(worldPosition, state.setValue(FlooGrateBlock.FIRE, HearthFire.FLOO), 3);
        changed();
    }

    private void fallBack(ServerLevel level) {
        BlockState state = level.getBlockState(worldPosition);
        if (state.is(getBlockState().getBlock()) && state.getValue(FlooGrateBlock.FIRE) == HearthFire.FLOO) {
            HearthFire to = after == HearthFire.WARM && !state.getValue(FlooGrateBlock.FUEL) ? HearthFire.NONE : after;
            level.setBlock(worldPosition, state.setValue(FlooGrateBlock.FIRE, to), 3);
        }
        at.koopro.wizardsandbeasts.floo.FlooDeparture.cancelAllFrom(level, worldPosition);
        changed();
    }

    /** The green roar starts now: the renderer grows the flames from this tick. */
    public void roar(long gameTime) {
        roarSince = gameTime;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("opening_width", Codec.INT, openingWidth);
        output.store("opening_height", Codec.INT, openingHeight);
        output.store("roar_since", Codec.LONG, roarSince);
        if (style != null) {
            output.store("style", Identifier.CODEC, style);
        }
        output.store("network_address", Codec.STRING, networkAddress);
        output.store("registered", Codec.BOOL, registered);
        output.store("enabled", Codec.BOOL, enabled);
        output.store("green_ticks", Codec.INT, greenTicks);
        output.store("after", Codec.STRING, after.getSerializedName());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        openingWidth = input.read("opening_width", Codec.INT).orElse(DEFAULT_OPENING);
        openingHeight = input.read("opening_height", Codec.INT).orElse(DEFAULT_OPENING);
        roarSince = input.read("roar_since", Codec.LONG).orElse(0L);
        style = input.read("style", Identifier.CODEC).orElse(null);
        networkAddress = input.read("network_address", Codec.STRING).orElse("");
        registered = input.read("registered", Codec.BOOL).orElse(false);
        enabled = input.read("enabled", Codec.BOOL).orElse(true);
        greenTicks = input.read("green_ticks", Codec.INT).orElse(0);
        String afterName = input.read("after", Codec.STRING).orElse("warm");
        after = java.util.Arrays.stream(HearthFire.values()).filter(f -> f.getSerializedName().equals(afterName))
                .findFirst().orElse(HearthFire.WARM);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
