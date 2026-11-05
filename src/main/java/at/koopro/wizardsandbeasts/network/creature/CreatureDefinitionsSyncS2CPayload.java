package at.koopro.wizardsandbeasts.network.creature;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.CreatureDefinition;
import at.koopro.wizardsandbeasts.creature.CreatureDefinitionRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends the whole creature definition table to a client, on login and after every {@code /reload}.
 *
 * <h2>Why this exists</h2>
 * {@code CreatureDefinitionLoader} is a server reload listener, so {@link CreatureDefinitionRegistry} was filled on
 * the server only. The client reads that table too: {@code GenericBeastEntity.registerControllers} builds its
 * one-shot controller from the definition's {@code clips}. On a dedicated server, or as a LAN guest, the client's
 * table was empty, so no generic creature registered a single triggerable clip. Every attack, hit, death, idle and
 * call trigger the server sent then fell on nothing. Singleplayer hid it, because the integrated server shares the
 * static table with the client: the same accident {@code BroomDefinitionsSyncS2CPayload} documents.
 *
 * <p>One codec: {@link CreatureDefinition#CODEC} through {@code fromCodecWithRegistries}, so a new definition field
 * reaches the client without a second serializer to forget.
 */
@NullMarked
public record CreatureDefinitionsSyncS2CPayload(List<CreatureDefinition> definitions) implements CustomPacketPayload {

    /** Sanity bound on a packet built from server data: 96 ship, and datapacks may add a few hundred. */
    private static final int MAX_DEFINITIONS = 1024;

    public static final Type<CreatureDefinitionsSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "creature_definitions_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreatureDefinitionsSyncS2CPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CreatureDefinition.CODEC)
                    .apply(ByteBufCodecs.list(MAX_DEFINITIONS))
                    .map(CreatureDefinitionsSyncS2CPayload::new, CreatureDefinitionsSyncS2CPayload::definitions);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Swaps the client's copy of the table. {@code replaceAll} publishes a new immutable map, which is also what
     * tells each creature's cached {@code definition()} lookup that it went stale.
     */
    public void applyToClientRegistry() {
        Map<Identifier, CreatureDefinition> map = new HashMap<>(definitions.size());
        for (CreatureDefinition definition : definitions) {
            map.put(definition.id(), definition);
        }
        CreatureDefinitionRegistry.replaceAll(map);
    }

    public static void syncToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new CreatureDefinitionsSyncS2CPayload(new ArrayList<>(CreatureDefinitionRegistry.getAll())));
    }
}
