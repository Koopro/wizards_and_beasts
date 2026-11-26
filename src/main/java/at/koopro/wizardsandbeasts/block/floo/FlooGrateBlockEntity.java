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

    private int openingWidth = DEFAULT_OPENING;
    private int openingHeight = DEFAULT_OPENING;
    private long roarSince;
    private @Nullable Identifier style;

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
        if (level instanceof ServerLevel server && Math.floorMod(server.getGameTime() + pos.asLong(), 20L) == 0L) {
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
            level.setBlock(worldPosition, state.setValue(FlooGrateBlock.FIRE, HearthFire.WARM), 3);
        }
        return now != null;
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
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        openingWidth = input.read("opening_width", Codec.INT).orElse(DEFAULT_OPENING);
        openingHeight = input.read("opening_height", Codec.INT).orElse(DEFAULT_OPENING);
        roarSince = input.read("roar_since", Codec.LONG).orElse(0L);
        style = input.read("style", Identifier.CODEC).orElse(null);
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
