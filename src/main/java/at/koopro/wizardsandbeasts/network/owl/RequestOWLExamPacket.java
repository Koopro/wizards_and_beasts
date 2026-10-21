package at.koopro.wizardsandbeasts.network.owl;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C→S: player confirms they want to sit the OWL exam. */
public record RequestOWLExamPacket() implements CustomPacketPayload {
    public static final Type<RequestOWLExamPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "request_owl_exam"));

    public static final StreamCodec<ByteBuf, RequestOWLExamPacket> STREAM_CODEC =
            PacketCodecUtils.noPayloadCodec(RequestOWLExamPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleServer(RequestOWLExamPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            // Opens an O.W.L. sitting. ExamSittings.begin makes every check — the module, the desk within reach
            // (the screen is opened client-side, so the server never saw the desk: 2026-09-29,
            // documentation/MULTIPLAYER_AUDIT.md), the resit wait, a sitting already open.
            at.koopro.wizardsandbeasts.education.ExamSittings.begin(player,
                    at.koopro.wizardsandbeasts.owl.Qualification.OWL, true);
        });
    }

    /** Blocks from an examination desk within which a candidate is sitting at it. */
    public static final int DESK_REACH = 6;

    /** Whether an examination desk stands within {@link #DESK_REACH} blocks of the player. */
    public static boolean atExaminationDesk(ServerPlayer player) {
        net.minecraft.core.BlockPos centre = player.blockPosition();
        for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(
                centre.offset(-DESK_REACH, -DESK_REACH, -DESK_REACH), centre.offset(DESK_REACH, DESK_REACH, DESK_REACH))) {
            if (player.level().getBlockState(pos).is(at.koopro.wizardsandbeasts.registry.ModBlocks.EXAMINATION_DESK.get())) {
                return true;
            }
        }
        return false;
    }
}
