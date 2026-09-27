package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * A phoenix vanishing in a burst of flame and reappearing somewhere else.
 *
 * <p>Canon: phoenixes "can disappear and reappear at will" (Fantastic Beasts); Fawkes arrives in the Chamber
 * of Secrets and Dumbledore leaves the Ministry in a flash of fire (Order of the Phoenix). In Minecraft it is
 * a server-authoritative teleport with rules: the same dimension only, a destination whose chunk is loaded,
 * inside the world border and the build height, and a space the bird fits into that is not water or lava.
 * The caller owns the cooldown; this class only answers "where" and does "go".
 */
public final class PhoenixFlameTravel {

    private PhoenixFlameTravel() {}

    /**
     * The nearest place around {@code target} the phoenix fits, searched outward in shells and slightly
     * upward first (a bird arrives above the ground, not in it). Empty when nothing within
     * {@code radius} is loaded, open and dry.
     */
    public static Optional<Vec3> landingNear(ServerLevel level, PhoenixEntity phoenix, Vec3 target, int radius) {
        BlockPos centre = BlockPos.containing(target);
        int[] heights = {1, 2, 0, 3, -1};
        for (int r = 0; r <= radius; r++) {
            for (int dy : heights) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        BlockPos pos = centre.offset(dx, dy, dz);
                        if (fits(level, phoenix, pos)) {
                            return Optional.of(Vec3.atBottomCenterOf(pos));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    static boolean fits(ServerLevel level, PhoenixEntity phoenix, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || pos.getY() <= level.getMinY() || pos.getY() >= level.getMaxY() - 2) {
            return false;
        }
        AABB box = phoenix.getDimensions(phoenix.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(pos));
        return level.noCollision(phoenix, box) && !level.containsAnyLiquid(box);
    }

    /** Vanishes in flame here and appears in flame there. The destination must come from {@link #landingNear}. */
    public static void travel(ServerLevel level, PhoenixEntity phoenix, Vec3 destination) {
        burst(level, phoenix.position().add(0, phoenix.getBbHeight() * 0.5, 0));
        phoenix.getNavigation().stop();
        phoenix.teleportTo(destination.x, destination.y, destination.z);
        phoenix.setDeltaMovement(Vec3.ZERO);
        phoenix.resetFallDistance();
        burst(level, destination.add(0, phoenix.getBbHeight() * 0.5, 0));
    }

    private static void burst(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 40, 0.35, 0.5, 0.35, 0.06);
        level.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 20, 0.2, 0.6, 0.2, 0.02);
        level.playSound(null, at.x, at.y, at.z, ModSounds.PHOENIX_FLAME.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
    }
}
