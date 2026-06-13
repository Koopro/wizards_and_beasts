package at.koopro.wizardsandbeasts.network.spell;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public record SpellProficiencySyncS2CPayload(
        String spellId,
        float proficiency) implements CustomPacketPayload {

    public static final Type<SpellProficiencySyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_proficiency_sync"));

    public static final StreamCodec<ByteBuf, SpellProficiencySyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellProficiencySyncS2CPayload decode(ByteBuf buf) {
            return new SpellProficiencySyncS2CPayload(PacketCodecUtils.readString(buf), buf.readFloat());
        }

        @Override
        public void encode(ByteBuf buf, SpellProficiencySyncS2CPayload pkt) {
            PacketCodecUtils.writeString(buf, pkt.spellId());
            buf.writeFloat(pkt.proficiency());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player, String spellId, float proficiency) {
        PacketDistributor.sendToPlayer(player, new SpellProficiencySyncS2CPayload(spellId, proficiency));
    }
}
