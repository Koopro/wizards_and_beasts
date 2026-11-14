package at.koopro.wizardsandbeasts.diagon;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * The invisible hull round Diagon Alley: nobody but a spectator leaves the alley's own box or rises above its roofs.
 *
 * <p>Breaking and placing are already shut in the pocket ({@link DiagonAlleyEvents}); this is the rest of it. A creative
 * flyer, an Apparition gone wrong or a pearl thrown over a roof would otherwise reach the backdrop, look past London's
 * last chimney into the void, or stand on it. A player found outside is set back to the nearest point inside, at rest.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class DiagonHull {

    /** How far above the street a player's feet may go: over the shop roofs is out of the alley. */
    static final int CEILING = 16;
    /** Street level within the alley's template. */
    static final int STREET = 6;

    private static @Nullable AABB cached;
    private static @Nullable Vec3i cachedSize;

    private DiagonHull() {}

    /** The box a player's feet stay in, for an alley template of {@code size} placed at {@code origin}. */
    static AABB box(BlockPos origin, Vec3i size) {
        return new AABB(origin.getX() + 1, origin.getY() + 1, origin.getZ() + 1,
                origin.getX() + size.getX() - 1, origin.getY() + STREET + CEILING, origin.getZ() + size.getZ() - 1);
    }

    /** Where {@code feet} is set back to, or {@code null} if it is inside {@code box}. */
    static @Nullable Vec3 clamp(Vec3 feet, AABB box) {
        double x = Math.clamp(feet.x, box.minX, box.maxX);
        double y = Math.clamp(feet.y, box.minY, box.maxY);
        double z = Math.clamp(feet.z, box.minZ, box.maxZ);
        return x == feet.x && y == feet.y && z == feet.z ? null : new Vec3(x, y, z);
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()
                || !(player.level() instanceof ServerLevel level) || level.dimension() != DiagonAlley.LEVEL) {
            return;
        }
        AABB box = hull(level);
        if (box == null) {
            return;
        }
        Vec3 back = clamp(player.position(), box);
        if (back == null) {
            return;
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(level, back.x, back.y, back.z, Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
        player.hurtMarked = true;
    }

    private static @Nullable AABB hull(ServerLevel pocket) {
        Vec3i size = pocket.getStructureManager().get(DiagonAlley.ALLEY)
                .map(template -> template.getSize()).orElse(null);
        if (size == null) {
            return null;
        }
        if (!size.equals(cachedSize) || cached == null) {
            cached = box(DiagonAlley.ORIGIN, size);
            cachedSize = size;
        }
        return cached;
    }
}
