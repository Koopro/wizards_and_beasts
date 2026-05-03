package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.data.PlayerTypeData;
import at.koopro.wizardsandbeasts.event.TypeEvents;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.type.TypeSystemAPI;
import at.koopro.wizardsandbeasts.type.WizSubtype;
import at.koopro.wizardsandbeasts.type.WizType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TypeSelectC2SPacket(String typeId, String subtypeId) implements CustomPacketPayload {

    public static final Type<TypeSelectC2SPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "type_select"));

    public static final StreamCodec<ByteBuf, TypeSelectC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TypeSelectC2SPacket decode(ByteBuf buf) {
            String typeId = PacketCodecUtils.readString(buf);
            String subtypeId = PacketCodecUtils.readString(buf);
            return new TypeSelectC2SPacket(typeId, subtypeId);
        }

        @Override
        public void encode(ByteBuf buf, TypeSelectC2SPacket pkt) {
            PacketCodecUtils.writeString(buf, pkt.typeId);
            PacketCodecUtils.writeString(buf, pkt.subtypeId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TypeSelectC2SPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            String safeTypeId = PacketCodecUtils.normalizeIdentifier(pkt.typeId);
            String safeSubtypeId = PacketCodecUtils.normalizeIdentifier(pkt.subtypeId);
            if (safeTypeId.isBlank() || safeSubtypeId.isBlank()) {
                player.displayClientMessage(Component.literal("\u00A7cInvalid heritage selection payload."), true);
                return;
            }

            PlayerTypeData data = player.getData(ModAttachments.TYPE_DATA.get());

            // Prevent re-selection if locked
            if (data.isLocked()) {
                player.displayClientMessage(
                        Component.literal("\u00A7cYour heritage is already locked."), true);
                return;
            }

            WizType type = WizType.byId(safeTypeId);
            if (type == null) {
                player.displayClientMessage(
                        Component.literal("\u00A7cUnknown type: " + safeTypeId), true);
                return;
            }
            if (!type.isAlphaAvailable()) {
                player.displayClientMessage(
                        Component.translatable("message.wizards_and_beasts.type_selection.coming_soon"), true);
                return;
            }

            WizSubtype subtype = WizSubtype.byId(safeSubtypeId);
            if (subtype == null || subtype.getParentType() != type) {
                player.displayClientMessage(
                        Component.literal("\u00A7cInvalid subtype: " + safeSubtypeId), true);
                return;
            }

            // Set and lock
            data.setSelectedType(type);
            data.setSelectedSubtype(subtype);
            data.setLocked(true);
            data.resetProfessionProgress();
            data.addProfessionPoints(3);

            // Apply stat modifiers
            TypeSystemAPI.applyStats(player);

            // Sync back to client
            TypeDataSyncS2CPacket.syncToPlayer(player, false);

            // Fire event
            NeoForge.EVENT_BUS.post(new TypeEvents.PlayerTypeSelectedEvent(player, type, subtype));

            player.displayClientMessage(
                    Component.literal("\u00A7aYou are now a " + type.getDisplayName()
                            + " (" + subtype.getDisplayName() + ")!"), true);
        });
    }
}
