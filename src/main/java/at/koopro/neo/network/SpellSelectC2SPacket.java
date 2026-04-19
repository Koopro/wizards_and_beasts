package at.koopro.neo.network;

import at.koopro.neo.Neo;
import at.koopro.neo.data.PlayerSpellData;
import at.koopro.neo.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SpellSelectC2SPacket(int slotIndex) implements CustomPacketPayload {

    public static final Type<SpellSelectC2SPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Neo.MODID, "spell_select"));

    public static final StreamCodec<ByteBuf, SpellSelectC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellSelectC2SPacket decode(ByteBuf buf) {
            return new SpellSelectC2SPacket(buf.readInt());
        }

        @Override
        public void encode(ByteBuf buf, SpellSelectC2SPacket pkt) {
            buf.writeInt(pkt.slotIndex);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpellSelectC2SPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (pkt.slotIndex < 0 || pkt.slotIndex > 3) return;

            PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
            data.setActiveSlot(pkt.slotIndex);
        });
    }
}
