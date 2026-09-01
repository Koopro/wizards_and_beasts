package at.koopro.wizardsandbeasts.network.broom;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.broom.BroomDefinitionRegistry;
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
 * Pushes the whole broom definition table to a client, on login and on every {@code /reload}.
 *
 * <h2>Why this had to exist</h2>
 * {@code BroomDefinitionLoader} is registered on {@code AddServerReloadListenersEvent}, so
 * {@link BroomDefinitionRegistry} is populated <b>on the server only</b>. The entity syncs its
 * {@code DEFINITION_ID} through {@code SynchedEntityData}, which is necessary and was never
 * sufficient: an id is a key into a table, and on a dedicated server the client's copy of that table
 * was empty. Every {@code resolveDefinition()} on the client fell through to the code default, so
 * every broom in the world drew the generic sheet, sat at the generic seat and shed the generic
 * trail — no matter what its JSON said.
 *
 * <p>It worked in single-player and on a LAN host purely by accident: the integrated server shares a
 * JVM with the client, and the registry is a static field, so both were reading the same map. That
 * is exactly the shape of bug that survives every hour of dev testing and appears the moment someone
 * joins a real server.
 *
 * <h2>One codec, not two</h2>
 * The stream codec is {@link BroomDefinition#CODEC} run through
 * {@code ByteBufCodecs.fromCodecWithRegistries}, rather than a hand-written field-by-field
 * serializer like {@code AbilityDefinitionsSyncS2CPayload}'s. A definition now carries twenty-five
 * components across four nested records; spelling those out again would mean every new field has to
 * be added in three places, and forgetting the third is silent — the field simply arrives as its
 * default on the client, which is the failure this class was written to fix in the first place.
 *
 * <p>Registries are needed because {@code displayName} and {@code loreLines} are {@code Component}s,
 * whose serialization consults the registry access for styled and translated content.
 */
@NullMarked
public record BroomDefinitionsSyncS2CPayload(List<BroomDefinition> definitions)
        implements CustomPacketPayload {

    /**
     * Sanity bound on a packet built from server data. Generous: eight brooms ship, and a datapack
     * adding a few hundred is a reasonable thing to do.
     */
    private static final int MAX_DEFINITIONS = 512;

    public static final Type<BroomDefinitionsSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "broom_definitions_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BroomDefinitionsSyncS2CPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(BroomDefinition.CODEC)
                    .apply(ByteBufCodecs.list(MAX_DEFINITIONS))
                    .map(BroomDefinitionsSyncS2CPayload::new, BroomDefinitionsSyncS2CPayload::definitions);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Swaps the client's mirror of the registry.
     *
     * <p>{@code replaceAll} publishes an immutable map through a volatile field, so a render thread
     * reading a broom mid-reload sees either the old table or the new one and never a half-built
     * map. Entities cache their resolved definition, so the swap also has to invalidate those —
     * {@code BroomEntity.onSyncedDataUpdated} covers an id change, but a {@code /reload} that
     * retunes a broom without changing its id would otherwise keep the stale record.
     */
    public void applyToClientRegistry() {
        Map<Identifier, BroomDefinition> map = new HashMap<>(definitions.size());
        for (BroomDefinition definition : definitions) {
            map.put(definition.id(), definition);
        }
        BroomDefinitionRegistry.replaceAll(map);
    }

    public static void syncToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new BroomDefinitionsSyncS2CPayload(new ArrayList<>(BroomDefinitionRegistry.getAll())));
    }
}
