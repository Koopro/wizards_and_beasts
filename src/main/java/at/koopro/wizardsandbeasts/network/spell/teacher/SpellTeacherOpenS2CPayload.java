package at.koopro.wizardsandbeasts.network.spell.teacher;
import at.koopro.wizardsandbeasts.network.ClientScreenHooksInvoker;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellTeacherState;
import at.koopro.wizardsandbeasts.spell.learning.SpellLearningService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record SpellTeacherOpenS2CPayload(List<SpellLearningService.SpellOffer> offers) implements CustomPacketPayload {

    public static final Type<SpellTeacherOpenS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_teacher_open"));

    public static final StreamCodec<ByteBuf, SpellTeacherOpenS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellTeacherOpenS2CPayload decode(ByteBuf buf) {
            int count = PacketCodecUtils.readBoundedCount(buf, PacketCodecUtils.MAX_KNOWN_SPELLS, "teacher-offers");
            List<SpellLearningService.SpellOffer> offers = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String spellId = PacketCodecUtils.readString(buf);
                String displayName = PacketCodecUtils.readString(buf);
                String category = PacketCodecUtils.readString(buf);
                boolean learnable = buf.readBoolean();
                String requirementText = PacketCodecUtils.readString(buf);
                int costKnuts = PacketCodecUtils.clampNonNegative(buf.readInt());
                offers.add(new SpellLearningService.SpellOffer(
                        spellId, displayName, category, learnable, requirementText, costKnuts));
            }
            return new SpellTeacherOpenS2CPayload(offers);
        }

        @Override
        public void encode(ByteBuf buf, SpellTeacherOpenS2CPayload pkt) {
            buf.writeInt(pkt.offers.size());
            for (SpellLearningService.SpellOffer offer : pkt.offers) {
                PacketCodecUtils.writeString(buf, offer.spellId());
                PacketCodecUtils.writeString(buf, offer.displayName());
                PacketCodecUtils.writeString(buf, offer.category());
                buf.writeBoolean(offer.learnable());
                PacketCodecUtils.writeString(buf, offer.requirementText());
                buf.writeInt(offer.costKnuts());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SpellTeacherOpenS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientSpellTeacherState.setOffers(pkt.offers);
            openClientScreenSafe();
        });
    }

    private static void openClientScreenSafe() {
        ClientScreenHooksInvoker.invoke("openSpellTeacherScreen");
    }

    public static void sendToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SpellTeacherOpenS2CPayload(SpellLearningService.buildOffers(player)));
    }
}
