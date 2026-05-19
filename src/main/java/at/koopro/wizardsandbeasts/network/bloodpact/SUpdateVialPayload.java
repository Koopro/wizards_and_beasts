package at.koopro.wizardsandbeasts.network.bloodpact;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.bloodpact.BloodPactVialItem;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.UUID;

public record SUpdateVialPayload(UUID pactUUID, UUID partnerUUID) implements CustomPacketPayload {

    public static final Type<SUpdateVialPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "update_vial"));

    public static final StreamCodec<ByteBuf, SUpdateVialPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SUpdateVialPayload::pactUUID,
            UUIDUtil.STREAM_CODEC, SUpdateVialPayload::partnerUUID,
            SUpdateVialPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendToPlayer(ServerPlayer player, UUID pactUUID, UUID partnerUUID) {
        PacketDistributor.sendToPlayer(player, new SUpdateVialPayload(pactUUID, partnerUUID));
    }

    public static void handleClient(SUpdateVialPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            ItemStack mainHand = mc.player.getMainHandItem();
            if (mainHand.getItem() instanceof BloodPactVialItem) {
                mainHand.set(ModDataComponents.PACT_UUID.get(), Optional.of(pkt.pactUUID()));
                mainHand.set(ModDataComponents.PACT_PARTNER_UUID.get(), Optional.of(pkt.partnerUUID()));
            }
        });
    }
}
