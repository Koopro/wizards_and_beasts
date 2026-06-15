package at.koopro.wizardsandbeasts.network.trinket;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.diary.DiaryService;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A line the player wrote into Riddle's Diary. */
public record DiaryWriteC2SPayload(String text) implements CustomPacketPayload {

    public static final Type<DiaryWriteC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "diary_write"));

    public static final StreamCodec<ByteBuf, DiaryWriteC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DiaryWriteC2SPayload decode(ByteBuf buf) {
            return new DiaryWriteC2SPayload(PacketCodecUtils.readString(buf));
        }

        @Override
        public void encode(ByteBuf buf, DiaryWriteC2SPayload pkt) {
            PacketCodecUtils.writeString(buf, pkt.text);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DiaryWriteC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                String text = pkt.text == null ? "" : pkt.text.trim();
                if (!text.isEmpty()) {
                    DiaryService.onWrite(player, text);
                }
            }
        });
    }
}
