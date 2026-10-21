package at.koopro.wizardsandbeasts.location;

import at.koopro.wizardsandbeasts.ministry.licence.MinistryArea;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Whether a player is at a location — the server's answer, never the client's.
 *
 * <p>Three forms, any of which counts ({@link LocationDefinition.Presence}):
 * <ul>
 *   <li><b>host</b>: an NPC that keeps the place admitted the player, who is still alive and within reach of it —
 *       the rule {@code GringottsCounter} already applied to the vault, made general;</li>
 *   <li><b>landmark</b>: the player stands among enough blocks of the place's palette ({@link MinistryArea});</li>
 *   <li><b>structure</b>: the player stands inside a piece of the place's worldgen structure.</li>
 * </ul>
 */
@NullMarked
public final class LocationPresence {

    /** The entity id of the host that last admitted each player. Dropped at logout. */
    private static final PlayerScopedState<Integer> HOST = PlayerScopedState.create("location_host");

    private LocationPresence() {}

    /** The location {@code entity} keeps, if any open location names its type as a host. */
    public static @Nullable Identifier hostedBy(Entity entity) {
        for (Map.Entry<Identifier, LocationDefinition> entry : Locations.all().entrySet()) {
            if (keeps(entry.getValue().presence(), entity) && Locations.open(entry.getKey()) != null) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Whether {@code entity} is one of the place's hosts: the right type, and for a villager the right trade. */
    static boolean keeps(LocationDefinition.Presence presence, Entity entity) {
        if (!presence.hosts().contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) {
            return false;
        }
        if (presence.hostProfession().isEmpty()) {
            return true;
        }
        ResourceKey<VillagerProfession> profession =
                ResourceKey.create(Registries.VILLAGER_PROFESSION, presence.hostProfession().get());
        return entity instanceof Villager villager && villager.getVillagerData().profession().is(profession);
    }

    /** A host dealt with this player: they are at its counter until they walk away from it. */
    public static void admit(ServerPlayer player, Entity host) {
        HOST.put(player.getUUID(), host.getId());
    }

    public static boolean isAt(ServerPlayer player, LocationDefinition def) {
        LocationDefinition.Presence presence = def.presence();
        if (!presence.hosts().isEmpty() && atHost(player, presence)) {
            return true;
        }
        if (presence.landmark().isPresent()) {
            TagKey<Block> palette = TagKey.create(Registries.BLOCK, presence.landmark().get());
            if (MinistryArea.isInside(player.level(), player.blockPosition(), palette)) {
                return true;
            }
        }
        if (presence.structure().isPresent() && player.level() instanceof ServerLevel level) {
            ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, presence.structure().get());
            return level.structureManager().getStructureWithPieceAt(player.blockPosition(), holder -> holder.is(key))
                    .isValid();
        }
        return false;
    }

    /** The host the player stands at for this place, or {@code null} when they are not at one of its hosts. */
    public static @Nullable Entity host(ServerPlayer player, LocationDefinition def) {
        LocationDefinition.Presence presence = def.presence();
        if (presence.hosts().isEmpty()) {
            return null;
        }
        Integer id = HOST.get(player.getUUID());
        if (id == null) {
            return null;
        }
        Entity host = player.level().getEntity(id);
        return host != null && host.isAlive() && player.isAlive() && keeps(presence, host)
                && player.distanceToSqr(host) <= presence.hostReach() * presence.hostReach() ? host : null;
    }

    private static boolean atHost(ServerPlayer player, LocationDefinition.Presence presence) {
        Integer id = HOST.get(player.getUUID());
        if (id == null) {
            return false;
        }
        Entity host = player.level().getEntity(id);
        return host != null && host.isAlive() && player.isAlive() && keeps(presence, host)
                && player.distanceToSqr(host) <= presence.hostReach() * presence.hostReach();
    }
}
