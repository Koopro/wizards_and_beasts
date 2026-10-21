package at.koopro.wizardsandbeasts.network.bestiary;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
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


public record BestiaryDataSyncPayload(PlayerBestiaryData data) implements CustomPacketPayload {
    public static final Type<BestiaryDataSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "bestiary_data_sync"));

    /**
     * The whole record, through its save codec: tiers, field notes, discovery history, watching time and harvest
     * lockouts. The journal shows all of it — how long you have watched, when you can next harvest — and a
     * hand-written codec that carried tiers and then notes was a second schema to keep in step with the first.
     */
    public static final StreamCodec<ByteBuf, BestiaryDataSyncPayload> STREAM_CODEC =
            net.minecraft.network.codec.ByteBufCodecs.fromCodec(PlayerBestiaryData.CODEC)
                    .map(BestiaryDataSyncPayload::new, BestiaryDataSyncPayload::data);

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
