package at.koopro.wizardsandbeasts.command.debug.inspect;

import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every inspector the mod ships, and the one raycast that decides which of them answers.
 *
 * <h2>The server picks the target, not the client</h2>
 *
 * <p>The floating panel needs to know what you are looking at, and the obvious design — the client
 * raycasts and asks the server about that block — hands an unauthenticated client the ability to
 * read any block entity anywhere in the world by naming its position. So the client asks nothing but
 * "what am I looking at"; the server does its own pick from the player's own rotation and answers
 * about whatever it finds. There is no target field on the request to forge.
 *
 * <p>It also removes a whole class of disagreement. Client and server raycasts can differ by a tick
 * of rotation, and a panel that describes a block the server does not think you are looking at is
 * worse than no panel.
 */
@NullMarked
public final class DebugInspectors {

    /**
     * How far the pick reaches. Deliberately longer than a survival block reach: the panel is for
     * looking at a pot across the room, and there is no interaction here to balance.
     */
    public static final double REACH = 12.0;

    private static final List<DebugInspector.OfBlock> BLOCKS = new ArrayList<>();
    private static final List<DebugInspector.OfEntity> ENTITIES = new ArrayList<>();
    private static boolean bootstrapped;

    private DebugInspectors() {}

    /**
     * Registers the built-ins, once.
     *
     * <p>Order is precedence: the first inspector that claims the target wins, so the two catch-alls
     * go last and every feature's own inspector gets first refusal.
     */
    public static void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;
        registerBlock(new at.koopro.wizardsandbeasts.brew.debug.CauldronDebugInspector());
        registerBlock(new at.koopro.wizardsandbeasts.wand.debug.WandmakersBenchDebugInspector());
        registerBlock(new at.koopro.wizardsandbeasts.floo.debug.FlooHearthDebugInspector());
        registerEntity(new at.koopro.wizardsandbeasts.entity.debug.BroomDebugInspector());
        registerEntity(new at.koopro.wizardsandbeasts.entity.debug.CreatureDebugInspector());
        registerEntity(new at.koopro.wizardsandbeasts.entity.debug.PlayerDebugInspector());
        // Catch-alls. Anything without a bespoke inspector still answers with its identity, its
        // blockstate and, for a block entity, its saved tag -- which is the whole point of a debug
        // overlay that claims to cover "every feature".
        registerBlock(new GenericBlockInspector());
        registerEntity(new GenericEntityInspector());
    }

    public static void registerBlock(DebugInspector.OfBlock inspector) {
        BLOCKS.add(inspector);
    }

    public static void registerEntity(DebugInspector.OfEntity inspector) {
        ENTITIES.add(inspector);
    }

    /** The block inspectors in precedence order, for callers that resolve a position themselves. */
    public static List<DebugInspector.OfBlock> blockInspectors() {
        bootstrap();
        return List.copyOf(BLOCKS);
    }

    public static List<DebugInspector> all() {
        bootstrap();
        List<DebugInspector> combined = new ArrayList<>(BLOCKS);
        combined.addAll(ENTITIES);
        return List.copyOf(combined);
    }

    /** What one player is looking at right now, described by whichever inspector claims it. */
    public static Optional<Result> lookedAt(ServerPlayer player) {
        bootstrap();
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(REACH));

        BlockHitResult blockHit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 blockEnd = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : end;

        // Entities only up to the first solid block: you cannot inspect what you cannot see.
        Entity entity = pickEntity(player, eye, blockEnd);
        if (entity != null) {
            for (DebugInspector.OfEntity inspector : ENTITIES) {
                if (inspector.matches(entity)) {
                    return Optional.of(new Result(
                            inspector.inspect(entity, player),
                            entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0)));
                }
            }
        }
        if (blockHit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        for (DebugInspector.OfBlock inspector : BLOCKS) {
            if (inspector.matches(level, pos, state)) {
                return Optional.of(new Result(
                        inspector.inspect(level, pos, state, player),
                        Vec3.atCenterOf(pos)));
            }
        }
        return Optional.empty();
    }

    /**
     * Nearest entity whose box the ray crosses.
     *
     * <p>Hand-rolled rather than {@code ProjectileUtil.getEntityHitResult}, following
     * {@code BeamRayResolver}: that helper's signature has changed in three of the last four
     * versions and this needs neither its precision nor its filters.
     */
    private static @Nullable Entity pickEntity(ServerPlayer player, Vec3 start, Vec3 end) {
        AABB search = player.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0);
        Entity closest = null;
        double closestDistSqr = start.distanceToSqr(end);
        for (Entity candidate : player.level().getEntities(player, search, e -> !e.isSpectator())) {
            AABB box = candidate.getBoundingBox().inflate(Math.max(0.1, candidate.getPickRadius()));
            Optional<Vec3> intercept = box.clip(start, end);
            if (intercept.isEmpty()) continue;
            double distSqr = start.distanceToSqr(intercept.get());
            if (distSqr < closestDistSqr) {
                closestDistSqr = distSqr;
                closest = candidate;
            }
        }
        return closest;
    }

    /**
     * A dump and the point in the world it describes.
     *
     * @param anchor where the panel hangs — a block's centre, an entity's midriff
     */
    public record Result(DebugReport report, Vec3 anchor) {}
}
