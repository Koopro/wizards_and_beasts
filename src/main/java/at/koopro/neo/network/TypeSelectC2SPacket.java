package at.koopro.neo.network;

import at.koopro.neo.Neo;
import at.koopro.neo.data.PlayerTypeData;
import at.koopro.neo.event.TypeEvents;
import at.koopro.neo.registry.ModAttachments;
import at.koopro.neo.type.TypeSystemAPI;
import at.koopro.neo.type.WizSubtype;
import at.koopro.neo.type.WizType;
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
            Identifier.fromNamespaceAndPath(Neo.MODID, "type_select"));

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

            PlayerTypeData data = player.getData(ModAttachments.TYPE_DATA.get());

            // Prevent re-selection if locked
            if (data.isLocked()) {
                player.displayClientMessage(
                        Component.literal("\u00A7cYour heritage is already locked."), false);
                return;
            }

            WizType type = WizType.byId(pkt.typeId);
            if (type == null) {
                player.displayClientMessage(
                        Component.literal("\u00A7cUnknown type: " + pkt.typeId), false);
                return;
            }

            WizSubtype subtype = WizSubtype.byId(pkt.subtypeId);
            if (subtype == null || subtype.getParentType() != type) {
                player.displayClientMessage(
                        Component.literal("\u00A7cInvalid subtype: " + pkt.subtypeId), false);
                return;
            }

            // Set and lock
            data.setSelectedType(type);
            data.setSelectedSubtype(subtype);
            data.setLocked(true);

            // Apply stat modifiers
            TypeSystemAPI.applyStats(player);

            // Sync back to client
            TypeDataSyncS2CPacket.syncToPlayer(player, false);

            // Fire event
            NeoForge.EVENT_BUS.post(new TypeEvents.PlayerTypeSelectedEvent(player, type, subtype));

            player.displayClientMessage(
                    Component.literal("\u00A7aYou are now a " + type.getDisplayName()
                            + " (" + subtype.getDisplayName() + ")!"), false);
        });
    }
}
