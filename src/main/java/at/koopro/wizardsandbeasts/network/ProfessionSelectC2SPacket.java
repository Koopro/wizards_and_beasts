package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.data.PlayerTypeData;
import at.koopro.wizardsandbeasts.event.TypeEvents;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.type.profession.ProfessionNode;
import at.koopro.wizardsandbeasts.type.profession.ProfessionSystemAPI;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ProfessionSelectC2SPacket(String professionId) implements CustomPacketPayload {
    public static final Type<ProfessionSelectC2SPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "profession_select"));

    public static final StreamCodec<ByteBuf, ProfessionSelectC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ProfessionSelectC2SPacket decode(ByteBuf buf) {
            return new ProfessionSelectC2SPacket(PacketCodecUtils.readString(buf));
        }

        @Override
        public void encode(ByteBuf buf, ProfessionSelectC2SPacket pkt) {
            PacketCodecUtils.writeString(buf, pkt.professionId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ProfessionSelectC2SPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            String safeProfessionId = PacketCodecUtils.normalizeIdentifier(pkt.professionId);
            if (safeProfessionId.isBlank()) {
                player.displayClientMessage(Component.literal("§cInvalid profession payload."), true);
                return;
            }
            ProfessionNode node = ProfessionNode.byId(safeProfessionId);
            if (node == null) {
                player.displayClientMessage(Component.literal("§cUnknown profession: " + safeProfessionId), true);
                return;
            }
            ProfessionSystemAPI.UnlockCheck check = ProfessionSystemAPI.evaluateSelect(player, node);
            if (!check.allowed()) {
                player.displayClientMessage(Component.literal("§cCannot select profession: " + check.reason()), true);
                return;
            }
            if (!ProfessionSystemAPI.trySelect(player, node.getId())) {
                player.displayClientMessage(Component.literal("§cFailed to select profession."), true);
                return;
            }
            PlayerTypeData data = player.getData(ModAttachments.TYPE_DATA.get());
            TypeDataSyncS2CPacket.syncToPlayer(player, false);
            NeoForge.EVENT_BUS.post(new TypeEvents.PlayerProfessionSelectedEvent(player, node, data.getProfessionPoints()));
            player.displayClientMessage(Component.literal("§aActive profession: " + node.getDisplayName()), true);
        });
    }
}
