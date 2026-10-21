package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.client.admin.AdminClientHandlers;
import at.koopro.wizardsandbeasts.ministry.trace.CaseReport;
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
 * The Ministry section's Cases page on the wire: every open case, unsolved incident and posted case, with the
 * evidence on each and where it came from, as {@link CaseReport} builds them for the command too. Read-only, asked
 * for by an open page at most every {@link #MIN_TICKS_BETWEEN} ticks, and only by someone who may change the
 * Ministry section.
 */
@NullMarked
public final class AdminMinistryPayloads {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int MIN_TICKS_BETWEEN = 20;
    static final int MAX_ROWS = 512;
    private static final PlayerScopedState<Long> LAST = PlayerScopedState.create("admin-ministry-requests");

    private AdminMinistryPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    public static boolean mayRead(AdminContext actor) {
        return actor.canRead() && actor.canModify(AdminCategory.MINISTRY.defaultCapability());
    }

    /** Answers one request; false when refused or too soon. */
    public static boolean send(ServerPlayer player) {
        if (!mayRead(AdminContext.of(player))) {
            LOGGER.warn("[Admin] Dropped Ministry cases request from unauthorised {} ({})",
                    player.getName().getString(), player.getUUID());
            return false;
        }
        long now = player.level().getServer().getTickCount();
        if (!PlayerScopedState.throttle(LAST, player, now, MIN_TICKS_BETWEEN)) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, new Reply(CaseReport.build(player.level().getServer())));
        return true;
    }

    public record Request() implements CustomPacketPayload {
        public static final Request INSTANCE = new Request();
        public static final Type<Request> TYPE = new Type<>(id("admin_ministry_cases_request"));
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

    public record Reply(CaseReport.Snapshot snapshot) implements CustomPacketPayload {
        public static final Type<Reply> TYPE = new Type<>(id("admin_ministry_cases"));
        public static final StreamCodec<ByteBuf, Reply> STREAM_CODEC = StreamCodec.of(
                (buf, reply) -> write(buf, reply.snapshot()), buf -> new Reply(read(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(Reply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onMinistryCases(payload));
        }
    }

    static void write(ByteBuf buf, CaseReport.Snapshot snapshot) {
        List<CaseReport.CaseRow> rows = snapshot.rows();
        int count = Math.min(rows.size(), MAX_ROWS);
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            CaseReport.CaseRow row = rows.get(i);
            buf.writeLong(row.id());
            PacketCodecUtils.writeString(buf, row.kind());
            PacketCodecUtils.writeString(buf, row.subject());
            PacketCodecUtils.writeString(buf, row.spell());
            PacketCodecUtils.writeString(buf, row.legalClass());
            PacketCodecUtils.writeString(buf, row.stage());
            buf.writeInt(row.strength());
            buf.writeInt(row.bar());
            PacketCodecUtils.writeString(buf, row.holder());
            PacketCodecUtils.writeString(buf, row.where());
            PacketCodecUtils.writeString(buf, row.evidence());
        }
        buf.writeInt(snapshot.incidents());
        buf.writeInt(snapshot.pending());
        buf.writeInt(snapshot.dossiers());
    }

    static CaseReport.Snapshot read(ByteBuf buf) {
        int count = PacketCodecUtils.readBoundedCount(buf, MAX_ROWS, "ministry cases");
        List<CaseReport.CaseRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new CaseReport.CaseRow(buf.readLong(), PacketCodecUtils.readString(buf),
                    PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf),
                    PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf), buf.readInt(), buf.readInt(),
                    PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf),
                    PacketCodecUtils.readString(buf)));
        }
        return new CaseReport.Snapshot(rows, buf.readInt(), buf.readInt(), buf.readInt());
    }
}
