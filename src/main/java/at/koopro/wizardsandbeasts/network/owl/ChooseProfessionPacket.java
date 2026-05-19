package at.koopro.wizardsandbeasts.network.owl;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.owl.OWLExaminationHandler;
import at.koopro.wizardsandbeasts.owl.Profession;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/** C→S: player selects a profession after passing their OWLs. */
public record ChooseProfessionPacket(@NonNull Profession profession) implements CustomPacketPayload {
    public static final Type<ChooseProfessionPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "choose_profession"));

    public static final StreamCodec<ByteBuf, ChooseProfessionPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ChooseProfessionPacket decode(ByteBuf buf) {
            int ord = buf.readByte();
            Profession[] profs = Profession.values();
            Profession prof = (ord >= 0 && ord < profs.length) ? profs[ord] : Profession.JOURNALIST;
            return new ChooseProfessionPacket(prof);
        }

        @Override
        public void encode(ByteBuf buf, ChooseProfessionPacket payload) {
            buf.writeByte(payload.profession().ordinal());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleServer(ChooseProfessionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            OWLExaminationHandler.chooseProfession(player, packet.profession());
        });
    }
}
