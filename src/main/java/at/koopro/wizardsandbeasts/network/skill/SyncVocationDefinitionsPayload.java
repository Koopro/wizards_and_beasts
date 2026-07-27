package at.koopro.wizardsandbeasts.network.skill;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.skill.vocation.VocationDefinition;
import at.koopro.wizardsandbeasts.skill.vocation.VocationRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Server → client sync of the Vocation catalogue, mirroring {@link SyncSkillDefinitionsPayload}.
 *
 * <p>{@link VocationRegistry} is filled by a server-side reload listener, so on a dedicated server a
 * client asked to show the list has nothing to show. The selection screen needs the catalogue, not just
 * the player's own declaration ({@code VocationDataSyncS2CPayload} carries that).
 */
public record SyncVocationDefinitionsPayload(List<VocationDefinition> vocations) implements CustomPacketPayload {

    public static final Type<SyncVocationDefinitionsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "sync_vocation_definitions"));

    public static final StreamCodec<ByteBuf, SyncVocationDefinitionsPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodec(VocationDefinition.CODEC.listOf())
                    .map(SyncVocationDefinitionsPayload::new, SyncVocationDefinitionsPayload::vocations);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Fires on join and on {@code /reload}; a null player there means "resync everyone". */
    @EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
    public static final class SyncOnDatapack {
        @SubscribeEvent
        public static void onDatapackSync(OnDatapackSyncEvent event) {
            SyncVocationDefinitionsPayload payload =
                    new SyncVocationDefinitionsPayload(List.copyOf(VocationRegistry.all()));
            ServerPlayer joining = event.getPlayer();
            if (joining != null) {
                PacketDistributor.sendToPlayer(joining, payload);
            } else {
                for (ServerPlayer player : event.getPlayerList().getPlayers()) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
        }
    }
}
