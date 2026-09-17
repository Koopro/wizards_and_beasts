package at.koopro.wizardsandbeasts.network.outline;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.render.outline.OutlineEntry;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Server -> one client: a group of highlighted blocks appears, disappears, or everything goes.
 *
 * <p>Addressed to a <b>viewer</b>, not broadcast. A block highlight is something one person learned
 * (Revelio shows the caster what is hidden), so unlike {@link EntityOutlineS2CPayload} it goes only to
 * the players the server names.
 *
 * <p>The unit is a <em>highlight</em> — one id, one outline, a list of positions — rather than a colour per
 * block. One cast is one highlight, so ending it costs a single id on the wire instead of repeating every
 * position, and a later cast over the same blocks is a separate highlight that expires on its own clock.
 *
 * @param action      what to do
 * @param highlightId the group this concerns; unused for {@link Action#CLEAR}
 * @param outline     colour and expiry for {@link Action#ADD} (the expiry only drives the fade — removal still
 *                    waits for {@link Action#REMOVE}); {@link OutlineEntry#NONE} otherwise
 * @param positions   blocks for {@link Action#ADD}; empty otherwise. At most {@link #MAX_POSITIONS_PER_PAGE}.
 */
@NullMarked
public record BlockOutlineS2CPayload(Action action, int highlightId, OutlineEntry outline, List<BlockPos> positions)
        implements CustomPacketPayload {

    /**
     * Positions per packet. A large highlight is split into several {@link Action#ADD} pages with the same
     * id, which the client appends, so this bound only decides packet size — never how much can be shown.
     */
    public static final int MAX_POSITIONS_PER_PAGE = 1024;

    public enum Action { ADD, REMOVE, CLEAR }

    public static final Type<BlockOutlineS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "block_outline"));

    /**
     * Copies every position with {@link BlockPos#immutable()}. An integrated server hands this object to the
     * client without encoding it, so a {@code MutableBlockPos} left in the list would still be moving when
     * the client reads it. (A list that already holds one mutable object N times is past saving here —
     * {@code BlockOutlineService} copies while iterating for that reason.)
     */
    public BlockOutlineS2CPayload {
        positions = positions.stream().map(BlockPos::immutable).toList();
    }

    public static final StreamCodec<ByteBuf, BlockOutlineS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BlockOutlineS2CPayload decode(ByteBuf buf) {
            int ordinal = buf.readByte();
            if (ordinal < 0 || ordinal >= Action.values().length) {
                throw new IllegalArgumentException("Invalid block outline action: " + ordinal);
            }
            Action action = Action.values()[ordinal];
            return switch (action) {
                case ADD -> {
                    int id = buf.readInt();
                    OutlineEntry outline = new OutlineEntry(buf.readInt(), buf.readLong());
                    int count = PacketCodecUtils.readBoundedCount(buf, MAX_POSITIONS_PER_PAGE, "block outline positions");
                    List<BlockPos> positions = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        positions.add(BlockPos.of(buf.readLong()));
                    }
                    yield new BlockOutlineS2CPayload(Action.ADD, id, outline, positions);
                }
                case REMOVE -> remove(buf.readInt());
                case CLEAR -> clearAll();
            };
        }

        @Override
        public void encode(ByteBuf buf, BlockOutlineS2CPayload payload) {
            buf.writeByte(payload.action.ordinal());
            switch (payload.action) {
                case ADD -> {
                    buf.writeInt(payload.highlightId);
                    buf.writeInt(payload.outline.argb());
                    buf.writeLong(payload.outline.expiresAt());
                    buf.writeInt(payload.positions.size());
                    for (BlockPos pos : payload.positions) {
                        buf.writeLong(pos.asLong());
                    }
                }
                case REMOVE -> buf.writeInt(payload.highlightId);
                case CLEAR -> { }
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static BlockOutlineS2CPayload remove(int highlightId) {
        return new BlockOutlineS2CPayload(Action.REMOVE, highlightId, OutlineEntry.NONE, List.of());
    }

    public static BlockOutlineS2CPayload clearAll() {
        return new BlockOutlineS2CPayload(Action.CLEAR, 0, OutlineEntry.NONE, List.of());
    }

    /** Sends a whole highlight to one viewer, split into pages that each fit the decoder's bound. */
    public static void sendAdd(ServerPlayer viewer, int highlightId, OutlineEntry outline, Collection<BlockPos> positions) {
        for (BlockOutlineS2CPayload page : pages(highlightId, outline, positions)) {
            PacketDistributor.sendToPlayer(viewer, page);
        }
    }

    public static void send(ServerPlayer viewer, BlockOutlineS2CPayload payload) {
        PacketDistributor.sendToPlayer(viewer, payload);
    }

    /** Every page repeats the id and outline, so each one is complete on its own and the client just appends. */
    public static List<BlockOutlineS2CPayload> pages(int highlightId, OutlineEntry outline, Collection<BlockPos> positions) {
        List<BlockOutlineS2CPayload> pages = new ArrayList<>();
        List<BlockPos> page = new ArrayList<>();
        for (BlockPos pos : positions) {
            page.add(pos);
            if (page.size() == MAX_POSITIONS_PER_PAGE) {
                pages.add(new BlockOutlineS2CPayload(Action.ADD, highlightId, outline, page));
                page = new ArrayList<>();
            }
        }
        if (!page.isEmpty()) {
            pages.add(new BlockOutlineS2CPayload(Action.ADD, highlightId, outline, page));
        }
        return pages;
    }
}
