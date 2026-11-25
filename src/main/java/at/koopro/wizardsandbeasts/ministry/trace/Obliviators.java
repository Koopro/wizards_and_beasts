package at.koopro.wizardsandbeasts.ministry.trace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Sends the Accidental Magic Reversal Squad when a Muggle report of a breach reaches the Ministry (C2). One
 * Obliviator per breach, beside the first witness still in the world; a scene nobody has loaded is cleaned up unseen.
 */
@NullMarked
public final class Obliviators {

    private static final int[][] BESIDE = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {-1, -1}};

    private Obliviators() {}

    /** @return the Obliviator sent, or null when the clean-up happened where nobody could see it */
    public static @Nullable ObliviatorEntity dispatch(MinecraftServer server, MinistryCaseData data, Incident incident) {
        if (!incident.exposure().breachesSecrecy()) {
            return null;
        }
        List<UUID> witnesses = data.witnessEntities(incident.id());
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, incident.dimension()));
        Vec3 anchor = null;
        if (level != null) {
            for (UUID id : witnesses) {
                Entity witness = level.getEntity(id);
                if (witness != null) {
                    anchor = witness.position();
                    break;
                }
            }
            if (anchor == null && level.isLoaded(incident.pos())) {
                anchor = Vec3.atBottomCenterOf(incident.pos());
            }
        }
        if (level == null || anchor == null) {
            data.forgetWitnesses(incident.id(), id -> true);
            return null;
        }
        return ObliviatorEntity.apparate(level, standingSpot(level, anchor), incident, witnesses);
    }

    private static Vec3 standingSpot(ServerLevel level, Vec3 anchor) {
        BlockPos base = BlockPos.containing(anchor);
        for (int[] d : BESIDE) {
            BlockPos feet = base.offset(d[0], 0, d[1]);
            if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                return Vec3.atBottomCenterOf(feet);
            }
        }
        return anchor;
    }
}
