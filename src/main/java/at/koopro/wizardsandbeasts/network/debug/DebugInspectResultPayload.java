package at.koopro.wizardsandbeasts.network.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.debug.DebugClientPayloadHandlers;
import at.koopro.wizardsandbeasts.command.debug.report.DebugLine;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: the dump for whatever the player is looking at, and where to hang it.
 *
 * <p>An empty {@code lines} list means "nothing under your crosshair" — the panel hides. Sent that
 * way rather than simply not replying, because a reply that never comes is indistinguishable from a
 * dropped packet, and a panel that keeps showing a stale dump of a pot you walked away from is
 * worse than one that blinks out.
 */
@NullMarked
public record DebugInspectResultPayload(double x, double y, double z,
                                        String title,
                                        List<DebugLine> lines) implements CustomPacketPayload {

    public static final Type<DebugInspectResultPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "debug_inspect_result"));

    public static final StreamCodec<ByteBuf, DebugInspectResultPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DebugInspectResultPayload decode(ByteBuf buf) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            String title = PacketCodecUtils.readString(buf);
            int count = buf.readInt();
            if (count < 0 || count > DebugReport.MAX_LINES) {
                throw new IllegalArgumentException("Invalid debug line count: " + count);
            }
            List<DebugLine> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                lines.add(DebugLine.STREAM_CODEC.decode(buf));
            }
            return new DebugInspectResultPayload(x, y, z, title, List.copyOf(lines));
        }

        @Override
        public void encode(ByteBuf buf, DebugInspectResultPayload payload) {
            buf.writeDouble(payload.x);
            buf.writeDouble(payload.y);
            buf.writeDouble(payload.z);
            PacketCodecUtils.writeString(buf, payload.title);
            int count = Math.min(payload.lines.size(), DebugReport.MAX_LINES);
            buf.writeInt(count);
            for (int i = 0; i < count; i++) {
                DebugLine.STREAM_CODEC.encode(buf, payload.lines.get(i));
            }
        }
    };

    /** Nothing under the crosshair. */
    public static DebugInspectResultPayload empty() {
        return new DebugInspectResultPayload(0.0, 0.0, 0.0, "", List.of());
    }

    public static DebugInspectResultPayload of(Vec3 anchor, DebugReport report) {
        return new DebugInspectResultPayload(anchor.x, anchor.y, anchor.z,
                report.title(), report.lines());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player, DebugInspectResultPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void handleClient(DebugInspectResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> DebugClientPayloadHandlers.setInspectResult(payload));
    }
}
