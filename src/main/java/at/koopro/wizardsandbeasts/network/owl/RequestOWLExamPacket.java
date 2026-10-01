package at.koopro.wizardsandbeasts.network.owl;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.owl.OWLExaminationHandler;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
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
            PlayerOWLData current = player.getData(ModAttachments.OWL_DATA.get());
            if (current.examTaken()) return;
            // The exam screen is opened client-side from the desk, so the server never saw the desk. Without
            // these two checks the packet sat the exam anywhere, with the OWLS module off (2026-09-29,
            // documentation/MULTIPLAYER_AUDIT.md).
            if (!at.koopro.wizardsandbeasts.module.ModuleManager.isEnabled(at.koopro.wizardsandbeasts.module.Module.OWLS)
                    || !atExaminationDesk(player)) {
                return;
            }
            OWLExaminationHandler.conductExam(player);
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
