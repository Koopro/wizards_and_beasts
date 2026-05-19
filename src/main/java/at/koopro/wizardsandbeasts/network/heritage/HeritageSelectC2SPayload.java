package at.koopro.wizardsandbeasts.network.heritage;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.event.heritage.HeritageEvents;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HeritageSelectC2SPayload(String typeId, String subtypeId) implements CustomPacketPayload {

    public static final Type<HeritageSelectC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "type_select"));

    public static final StreamCodec<ByteBuf, HeritageSelectC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public HeritageSelectC2SPayload decode(ByteBuf buf) {
            String typeId = PacketCodecUtils.readString(buf);
            String subtypeId = PacketCodecUtils.readString(buf);
            return new HeritageSelectC2SPayload(typeId, subtypeId);
        }

        @Override
        public void encode(ByteBuf buf, HeritageSelectC2SPayload pkt) {
            PacketCodecUtils.writeString(buf, pkt.typeId);
            PacketCodecUtils.writeString(buf, pkt.subtypeId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HeritageSelectC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            String safeTypeId = PacketCodecUtils.normalizeIdentifier(pkt.typeId);
            String safeSubtypeId = PacketCodecUtils.normalizeIdentifier(pkt.subtypeId);
            if (safeTypeId.isBlank() || safeSubtypeId.isBlank()) {
                player.displayClientMessage(Component.literal("\u00A7cInvalid heritage selection payload."), true);
                return;
            }

            PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());

            // Prevent re-selection if locked
            if (data.isLocked()) {
                player.displayClientMessage(
                        Component.literal("\u00A7cYour heritage is already locked."), true);
                return;
            }

            Heritage heritage = Heritage.byId(safeTypeId);
            if (heritage == null) {
                player.displayClientMessage(
                        Component.literal("\u00A7cUnknown type: " + safeTypeId), true);
                return;
            }
            if (!heritage.isAlphaAvailable()) {
                player.displayClientMessage(
                        Component.translatable("message.wizards_and_beasts.type_selection.coming_soon"), true);
                return;
            }

            HeritageVariant variant = HeritageVariant.byId(safeSubtypeId);
            if (variant == null || variant.getParentHeritage() != heritage) {
                player.displayClientMessage(
                        Component.literal("\u00A7cInvalid subtype: " + safeSubtypeId), true);
                return;
            }

            // Set and lock
            data.setSelectedHeritage(heritage);
            data.setSelectedHeritageVariant(variant);
            data.setLocked(true);
            data.resetProfessionProgress();
            data.addProfessionPoints(3);

            // Apply stat modifiers
            HeritageAPI.applyStats(player);

            // Sync back to client
            HeritageDataSyncS2CPayload.syncToPlayer(player, false);

            // Fire event
            NeoForge.EVENT_BUS.post(new HeritageEvents.PlayerHeritageSelectedEvent(player, heritage, variant));

            player.displayClientMessage(
                    Component.literal("\u00A7aYou are now a " + heritage.getDisplayName()
                            + " (" + variant.getDisplayName() + ")!"), true);
        });
    }
}
