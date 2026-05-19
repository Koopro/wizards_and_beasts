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
            OWLExaminationHandler.conductExam(player);
        });
    }
}
