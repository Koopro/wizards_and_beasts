package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.client.admin.AdminClientHandlers;
import at.koopro.wizardsandbeasts.market.DealClass;
import at.koopro.wizardsandbeasts.market.EconomyReport;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * The Economy section's live page on the wire: the ledger's flows and every trader's book, as
 * {@link EconomyReport} builds them for the command too. Read-only, asked for by an open page at most every
 * {@link #MIN_TICKS_BETWEEN} ticks, and only by someone who may change the Economy section.
 */
@NullMarked
public final class AdminEconomyPayloads {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int MIN_TICKS_BETWEEN = 20;
    private static final int MAX_ROWS = 512;
    private static final PlayerScopedState<Long> LAST = PlayerScopedState.create("admin-economy-requests");

    private AdminEconomyPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    public static boolean mayRead(AdminContext actor) {
        return actor.canRead() && actor.canModify(AdminCategory.ECONOMY.defaultCapability());
    }

    /** Answers one request; false when refused or too soon. */
    public static boolean send(ServerPlayer player) {
        if (!mayRead(AdminContext.of(player))) {
            LOGGER.warn("[Admin] Dropped economy request from unauthorised {} ({})", player.getName().getString(),
                    player.getUUID());
            return false;
        }
        long now = player.level().getServer().getTickCount();
        if (!PlayerScopedState.throttle(LAST, player, now, MIN_TICKS_BETWEEN)) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, new Reply(EconomyReport.build(player.level().getServer())));
        return true;
    }

    public record Request() implements CustomPacketPayload {
        public static final Request INSTANCE = new Request();
        public static final Type<Request> TYPE = new Type<>(id("admin_economy_request"));
        public static final StreamCodec<ByteBuf, Request> STREAM_CODEC = PacketCodecUtils.noPayloadCodec(() -> INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Request payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    send(player);
                }
            });
        }
    }

    public record Reply(EconomyReport.Snapshot snapshot) implements CustomPacketPayload {
        public static final Type<Reply> TYPE = new Type<>(id("admin_economy"));
        public static final StreamCodec<ByteBuf, Reply> STREAM_CODEC = StreamCodec.of(
                (buf, reply) -> write(buf, reply.snapshot()), buf -> new Reply(read(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(Reply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onEconomy(payload));
        }
    }

    static void write(ByteBuf buf, EconomyReport.Snapshot snapshot) {
        List<EconomyReport.FlowLine> flows = snapshot.flows();
        int flowCount = Math.min(flows.size(), MAX_ROWS);
        buf.writeInt(flowCount);
        for (int i = 0; i < flowCount; i++) {
            EconomyReport.FlowLine f = flows.get(i);
            PacketCodecUtils.writeString(buf, f.flow());
            PacketCodecUtils.writeString(buf, f.kind());
            buf.writeLong(f.count());
            buf.writeLong(f.knuts());
        }
        List<EconomyReport.MarketRow> rows = snapshot.markets();
        int rowCount = Math.min(rows.size(), MAX_ROWS);
        buf.writeInt(rowCount);
        for (int i = 0; i < rowCount; i++) {
            EconomyReport.MarketRow r = rows.get(i);
            PacketCodecUtils.writeString(buf, r.location());
            buf.writeBoolean(r.buying());
            PacketCodecUtils.writeString(buf, r.good());
            buf.writeLong(r.unitKnuts());
            buf.writeByte(r.saturation());
            buf.writeBoolean(r.inDemand());
            buf.writeLong(r.ceilingPerDay());
            buf.writeByte(r.deal().ordinal());
        }
        buf.writeLong(snapshot.net());
    }

    static EconomyReport.Snapshot read(ByteBuf buf) {
        int flowCount = PacketCodecUtils.readBoundedCount(buf, MAX_ROWS, "economy flows");
        List<EconomyReport.FlowLine> flows = new ArrayList<>(flowCount);
        for (int i = 0; i < flowCount; i++) {
            flows.add(new EconomyReport.FlowLine(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf),
                    buf.readLong(), buf.readLong()));
        }
        int rowCount = PacketCodecUtils.readBoundedCount(buf, MAX_ROWS, "economy markets");
        List<EconomyReport.MarketRow> rows = new ArrayList<>(rowCount);
        DealClass[] classes = DealClass.values();
        for (int i = 0; i < rowCount; i++) {
            String location = PacketCodecUtils.readString(buf);
            boolean buying = buf.readBoolean();
            String good = PacketCodecUtils.readString(buf);
            long unit = buf.readLong();
            int saturation = buf.readByte();
            boolean demand = buf.readBoolean();
            long ceiling = buf.readLong();
            int ordinal = buf.readByte();
            rows.add(new EconomyReport.MarketRow(location, buying, good, unit, saturation, demand, ceiling,
                    ordinal >= 0 && ordinal < classes.length ? classes[ordinal] : DealClass.LEGAL));
        }
        return new EconomyReport.Snapshot(List.copyOf(flows), List.copyOf(rows), buf.readLong());
    }
}
