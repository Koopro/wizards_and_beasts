package at.koopro.wizardsandbeasts.network.heritage;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.event.heritage.HeritageEvents;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.heritage.profession.ProfessionNode;
import at.koopro.wizardsandbeasts.heritage.profession.ProfessionSystemAPI;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ProfessionSelectC2SPayload(String professionId) implements CustomPacketPayload {
    public static final Type<ProfessionSelectC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "profession_select"));

    public static final StreamCodec<ByteBuf, ProfessionSelectC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ProfessionSelectC2SPayload decode(ByteBuf buf) {
            return new ProfessionSelectC2SPayload(PacketCodecUtils.readString(buf));
        }

        @Override
        public void encode(ByteBuf buf, ProfessionSelectC2SPayload pkt) {
            PacketCodecUtils.writeString(buf, pkt.professionId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ProfessionSelectC2SPayload pkt, IPayloadContext ctx) {
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
            PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
            HeritageDataSyncS2CPayload.syncToPlayer(player, false);
            NeoForge.EVENT_BUS.post(new HeritageEvents.PlayerProfessionSelectedEvent(player, node, data.getProfessionPoints()));
            player.displayClientMessage(Component.literal("§aActive profession: " + node.getDisplayName()), true);
        });
    }
}
