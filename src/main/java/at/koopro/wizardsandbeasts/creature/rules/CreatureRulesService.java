package at.koopro.wizardsandbeasts.creature.rules;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModCreatures;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * The server side of the creature rules: the only writer of {@link CreatureRulesData}, and the natural-spawn gate.
 *
 * <p><b>The gate</b> listens to NeoForge's {@link MobSpawnEvent.SpawnPlacementCheck}, which
 * {@code SpawnPlacements.checkSpawnRules} fires for every placement check — the documented runtime counterpart of
 * the placement predicates {@code BeastSpawnHandler} registers. It only ever answers "fail", only for this mod's
 * creatures, and only for {@link EntitySpawnReason#NATURAL} and {@link EntitySpawnReason#CHUNK_GENERATION}: spawn
 * eggs, commands, structures, breeding and the Creature Lab's own test spawns are untouched. One listener covers
 * every creature, including those whose placement predicate is registered elsewhere (the Niffler) or not at all.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class CreatureRulesService {

    private CreatureRulesService() {}

    public static void update(MinecraftServer server, String creatureId, UnaryOperator<CreatureRule> change) {
        CreatureRulesData data = CreatureRulesData.get(server);
        data.put(creatureId, change.apply(data.rule(creatureId)));
        CreatureRules.publish(data.rules());
    }

    /** The roster id path of {@code type} when it is one of this mod's creatures, else null. */
    public static @Nullable String creatureIdOf(EntityType<?> type) {
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return WizardsAndBeastsMod.MODID.equals(key.getNamespace()) && ModCreatures.ROSTER.contains(key.getPath())
                ? key.getPath() : null;
    }

    /** Whether the rules refuse this spawn; the event listener below is this and nothing more. */
    public static boolean refuses(EntityType<?> type, EntitySpawnReason reason) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return false;
        }
        String id = creatureIdOf(type);
        return id != null && !CreatureRules.naturalSpawn(id);
    }

    @SubscribeEvent
    static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (refuses(event.getEntityType(), event.getSpawnType())) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        CreatureRules.publish(CreatureRulesData.get(event.getServer()).rules());
    }

    /** A world closing takes its rules with it. */
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        CreatureRules.publish(Map.of());
    }
}
