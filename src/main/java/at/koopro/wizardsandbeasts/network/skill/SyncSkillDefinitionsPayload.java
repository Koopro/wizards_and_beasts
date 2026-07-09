package at.koopro.wizardsandbeasts.network.skill;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
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
 * Server → client sync of the full skill node definition list, mirroring
 * {@code SyncBestiaryEntriesPayload}. Client GUI code must not read the server-populated
 * side of {@link SkillTrees} — that only works in a shared single-player JVM; on a dedicated
 * server the client-side server registry is empty. Clients store the synced list via
 * {@link SkillTrees#setClientDefinitions}.
 */
public record SyncSkillDefinitionsPayload(List<Skill> skills) implements CustomPacketPayload {
    public static final Type<SyncSkillDefinitionsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "sync_skill_definitions"));

    public static final StreamCodec<ByteBuf, SyncSkillDefinitionsPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodec(Skill.CODEC.listOf())
                    .map(SyncSkillDefinitionsPayload::new, SyncSkillDefinitionsPayload::skills);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Fires on player join and on {@code /reload} (server datapack sync), pushing the current
     * definition list. On {@code /reload} {@link OnDatapackSyncEvent#getPlayer()} is {@code null},
     * so every player is re-synced.
     */
    @EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
    public static final class SyncOnDatapack {
        @SubscribeEvent
        public static void onDatapackSync(OnDatapackSyncEvent event) {
            SyncSkillDefinitionsPayload payload = new SyncSkillDefinitionsPayload(List.copyOf(SkillTrees.all()));
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
