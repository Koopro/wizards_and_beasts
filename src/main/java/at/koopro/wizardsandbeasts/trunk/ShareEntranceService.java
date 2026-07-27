package at.koopro.wizardsandbeasts.trunk;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary "shared entrance" doorways opened by a sneak-cast Capacious Extremis. While a doorway is
 * open, any other player who casts Capacious near it is sent into the opener's trunk pocket instead of
 * needing a trunk of their own — a group stash / hideout. Doorways live in server memory only and
 * expire after a fixed window, so no world state or dimension plumbing is added.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ShareEntranceService {

    /** How long a doorway stays open, in ticks (30s). */
    public static final int DOORWAY_TICKS = 600;
    /** How close an ally must be to step through, in blocks. */
    public static final double USE_RADIUS = 3.0;

    public record Doorway(TrunkRecord pocket, ResourceKey<Level> dim, BlockPos pos, long expiryTick) {}

    private static final Map<UUID, Doorway> DOORWAYS = new ConcurrentHashMap<>();

    private ShareEntranceService() {}

    /** Opens (or replaces) this caster's shared doorway at their feet. */
    public static void open(ServerPlayer caster, TrunkRecord pocket) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        DOORWAYS.put(caster.getUUID(), new Doorway(pocket, level.dimension(), caster.blockPosition(),
                level.getGameTime() + DOORWAY_TICKS));
    }

    /** The nearest live doorway opened by someone else, in the player's dimension and radius, else null. */
    public static @Nullable Doorway findUsable(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        long now = level.getGameTime();
        Doorway best = null;
        double bestSq = USE_RADIUS * USE_RADIUS;
        for (Map.Entry<UUID, Doorway> e : DOORWAYS.entrySet()) {
            if (e.getKey().equals(player.getUUID())) {
                continue;
            }
            Doorway d = e.getValue();
            if (d.expiryTick() <= now || !d.dim().equals(level.dimension())) {
                continue;
            }
            double sq = d.pos().distToCenterSqr(player.getX(), player.getY(), player.getZ());
            if (sq <= bestSq) {
                bestSq = sq;
                best = d;
            }
        }
        return best;
    }

    /** Drops a player's own doorway. */
    public static void clear(UUID playerId) {
        DOORWAYS.remove(playerId);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (DOORWAYS.isEmpty()) {
            return;
        }
        long now = event.getServer().overworld().getGameTime();
        Iterator<Map.Entry<UUID, Doorway>> it = DOORWAYS.entrySet().iterator();
        while (it.hasNext()) {
            Doorway d = it.next().getValue();
            if (d.expiryTick() <= now) {
                it.remove();
                continue;
            }
            if (now % 4 == 0) {
                ServerLevel dl = event.getServer().getLevel(d.dim());
                if (dl != null) {
                    dl.sendParticles(ParticleTypes.PORTAL,
                            d.pos().getX() + 0.5, d.pos().getY() + 1.0, d.pos().getZ() + 0.5,
                            12, 0.35, 0.9, 0.35, 0.02);
                    dl.sendParticles(ParticleTypes.REVERSE_PORTAL,
                            d.pos().getX() + 0.5, d.pos().getY() + 0.3, d.pos().getZ() + 0.5,
                            6, 0.25, 0.1, 0.25, 0.04);
                }
            }
        }
    }
}
