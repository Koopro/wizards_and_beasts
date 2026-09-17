package at.koopro.wizardsandbeasts.spell.revelio;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What a Revelio cast finds: the entities and the blocks worth pointing at that the caster can see.
 *
 * <p>Nothing here outlines anything or talks to a client. It answers one question against the server's
 * world, so a game test can ask it directly and the cast can hand the answer to the outline services.
 *
 * <h2>Seen, not sensed</h2>
 *
 * <p>Everything found passes a line-of-sight ray from the caster's eyes. Revelio reveals what is
 * <em>hidden in plain view</em> — a mob in the dark, a chest behind a bush, a wizard under a Cloak — not
 * what is behind a wall; a scan that ignored walls would be an x-ray, and would make every hiding mechanic
 * in the mod pointless. Rays use {@link ClipContext.Block#VISUAL}, so glass does not hide things and
 * stone does. Invisible entities are found like any other: they block nothing, and finding them is the
 * point.
 *
 * <h2>Which blocks</h2>
 *
 * <p>Data-driven, so a pack can tune it without code:
 * <ul>
 *   <li>{@link #IGNORES} first — {@code #wizards_and_beasts:revelio_ignores}. Signs, beds, banners: blocks
 *       that carry a block entity but are never a secret.</li>
 *   <li>Then anything in {@link #REVEALS} ({@code #wizards_and_beasts:revelio_reveals}: doors, levers,
 *       buttons, ores, note blocks …)</li>
 *   <li>or anything with a block entity — chests, barrels, spawners, and every storage block a mod adds,
 *       without this class having to know it exists.</li>
 * </ul>
 */
@NullMarked
public final class RevelioScan {

    public static final TagKey<Block> REVEALS = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "revelio_reveals"));

    public static final TagKey<Block> IGNORES = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "revelio_ignores"));

    /** Non-living entities worth outlining too — container minecarts, by default. */
    public static final TagKey<EntityType<?>> REVEALS_ENTITIES = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "revelio_reveals"));

    /**
     * Caps on what one cast hands to the outline services, nearest first. A mob farm or an ore-rich cave
     * would otherwise light the whole screen and read as noise, which is the opposite of revealing.
     */
    public static final int MAX_ENTITIES = 32;
    public static final int MAX_BLOCKS = 64;

    private RevelioScan() {}

    /** @param entities nearest first; @param blocks nearest first, immutable positions */
    public record Result(List<Entity> entities, List<BlockPos> blocks) {
        public boolean isEmpty() {
            return entities.isEmpty() && blocks.isEmpty();
        }

        public int total() {
            return entities.size() + blocks.size();
        }
    }

    public static Result scan(ServerLevel level, ServerPlayer caster, double radius) {
        Vec3 eye = caster.getEyePosition();
        return new Result(entities(level, caster, eye, radius), blocks(level, caster, eye, radius));
    }

    /** Whether a block is worth revealing at all, before asking whether it can be seen. */
    public static boolean reveals(BlockState state) {
        if (state.isAir() || state.is(IGNORES)) {
            return false;
        }
        return state.is(REVEALS) || state.hasBlockEntity();
    }

    private static List<Entity> entities(ServerLevel level, ServerPlayer caster, Vec3 eye, double radius) {
        double radiusSqr = radius * radius;
        List<Entity> found = new ArrayList<>();
        for (Entity candidate : level.getEntities(caster, new AABB(eye, eye).inflate(radius),
                e -> e.isAlive() && !e.isSpectator()
                        && (e instanceof LivingEntity || e.getType().is(REVEALS_ENTITIES)))) {
            if (candidate.getBoundingBox().getCenter().distanceToSqr(eye) <= radiusSqr
                    && canSee(level, caster, eye, candidate)) {
                found.add(candidate);
            }
        }
        found.sort(Comparator.comparingDouble(e -> e.getBoundingBox().getCenter().distanceToSqr(eye)));
        return found.size() > MAX_ENTITIES ? List.copyOf(found.subList(0, MAX_ENTITIES)) : found;
    }

    private static List<BlockPos> blocks(ServerLevel level, ServerPlayer caster, Vec3 eye, double radius) {
        double radiusSqr = radius * radius;
        int reach = Mth.ceil(radius);
        BlockPos origin = BlockPos.containing(eye);
        int minY = Math.max(origin.getY() - reach, level.getMinY());
        int maxY = Math.min(origin.getY() + reach, level.getMaxY());

        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.getX() - reach, minY, origin.getZ() - reach,
                origin.getX() + reach, maxY, origin.getZ() + reach)) {
            if (distanceSqrToCentre(pos, eye) > radiusSqr || !level.isLoaded(pos)) {
                continue;
            }
            // The cheap test first: nearly every block in the volume is stone or air, and a state lookup
            // costs far less than a ray.
            if (reveals(level.getBlockState(pos)) && canSee(level, caster, eye, pos)) {
                found.add(pos.immutable());
            }
        }
        found.sort(Comparator.comparingDouble(pos -> distanceSqrToCentre(pos, eye)));
        return found.size() > MAX_BLOCKS ? List.copyOf(found.subList(0, MAX_BLOCKS)) : found;
    }

    /**
     * Eyes first, then the middle of the body: a mob whose head is behind a fence post but whose flank is
     * in plain view has been seen.
     */
    private static boolean canSee(ServerLevel level, ServerPlayer caster, Vec3 eye, Entity target) {
        return clearPath(level, caster, eye, target.getEyePosition())
                || clearPath(level, caster, eye, target.getBoundingBox().getCenter());
    }

    /**
     * A ray to the block's centre that either reaches it untouched (a lever, a button: no shape in the
     * way) or stops on the block itself (a chest, an ore: its own face is what is seen).
     */
    private static boolean canSee(ServerLevel level, ServerPlayer caster, Vec3 eye, BlockPos pos) {
        BlockHitResult hit = level.clip(new ClipContext(
                eye, Vec3.atCenterOf(pos), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    private static boolean clearPath(ServerLevel level, ServerPlayer caster, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, caster))
                .getType() == HitResult.Type.MISS;
    }

    private static double distanceSqrToCentre(BlockPos pos, Vec3 point) {
        double dx = pos.getX() + 0.5 - point.x;
        double dy = pos.getY() + 0.5 - point.y;
        double dz = pos.getZ() + 0.5 - point.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
