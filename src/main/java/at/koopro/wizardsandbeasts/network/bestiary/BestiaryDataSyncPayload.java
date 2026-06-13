package at.koopro.wizardsandbeasts.network.bestiary;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.bestiary.data.PlayerBestiaryData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

public record BestiaryDataSyncPayload(PlayerBestiaryData data) implements CustomPacketPayload {
    public static final Type<BestiaryDataSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "bestiary_data_sync"));

    public static final StreamCodec<ByteBuf, BestiaryDataSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BestiaryDataSyncPayload decode(ByteBuf buf) {
            int size = buf.readInt();
            Map<Identifier, DiscoveryTier> map = new HashMap<>();
            for (int i = 0; i < size; i++) {
                map.put(Identifier.parse(PacketCodecUtils.readString(buf)), DiscoveryTier.valueOf(PacketCodecUtils.readString(buf)));
            }
            return new BestiaryDataSyncPayload(new PlayerBestiaryData(map));
        }

        @Override
        public void encode(ByteBuf buf, BestiaryDataSyncPayload payload) {
            buf.writeInt(payload.data.tiers().size());
            payload.data.tiers().forEach((id, tier) -> {
                PacketCodecUtils.writeString(buf, id.toString());
                PacketCodecUtils.writeString(buf, tier.name());
            });
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void syncToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new BestiaryDataSyncPayload(player.getData(ModAttachments.BESTIARY_DATA.get())));
    }

    @EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
    public static final class LoginSync {
        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                syncToPlayer(player);
            }
        }
    }
}
