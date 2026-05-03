package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.spell.learning.SpellLearningService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SpellTeacherLearnC2SPacket(String spellId) implements CustomPacketPayload {

    public static final Type<SpellTeacherLearnC2SPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_teacher_learn"));

    public static final StreamCodec<ByteBuf, SpellTeacherLearnC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellTeacherLearnC2SPacket decode(ByteBuf buf) {
            return new SpellTeacherLearnC2SPacket(PacketCodecUtils.readString(buf));
        }

        @Override
        public void encode(ByteBuf buf, SpellTeacherLearnC2SPacket pkt) {
            PacketCodecUtils.writeString(buf, pkt.spellId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpellTeacherLearnC2SPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            SpellLearningService.LearnResult result = SpellLearningService.tryLearnSpell(player, pkt.spellId);
            if (result.success()) {
                player.displayClientMessage(Component.literal("\u00A7aLearned " + result.message() + "!"), true);
            } else {
                player.displayClientMessage(Component.literal("\u00A7c" + result.message()), true);
            }
            SpellTeacherOpenS2CPacket.sendToPlayer(player);
        });
    }
}
