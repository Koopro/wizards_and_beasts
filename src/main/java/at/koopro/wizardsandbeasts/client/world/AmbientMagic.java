package at.koopro.wizardsandbeasts.client.world;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Quiet magic in the open world, outside any built location: on clear nights, fireflies blink over grass, flowers and
 * leaves near the player, and pale wisps hang over swamp water (Hinkypunk country); by day, pollen motes drift down over
 * flowers. Anywhere, in any light worth the name, gold catches it: a glint twinkles on coin heaps, gilt and gold blocks.
 * The world felt dead between the mod's places; this is the first, smallest piece of making it
 * feel lived in, and it is held to that: a handful of sparks, never a cloud.
 *
 * <p>Client-side only and cosmetic: nothing is sent, nothing the server knows changes. Uses the mod's own firefly sprite, lit
 * by itself (a world-lit sprite went dark at night), never vanilla's end-rod or villager sparkles. Scaled by
 * {@code Config.ambientMagicDensity} (0 turns it off) and, like every tinted mod particle, thinned by the player's
 * particle setting.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class AmbientMagic {

    /** Warm green-gold, a firefly's light, not a spell colour. */
    private static final int FIREFLY = 0xFFD8F06A;
    /** How far round the player fireflies may appear, in blocks. */
    private static final int REACH = 14;
    /** Tries per tick at density 1; most land on ground that does not qualify. */
    private static final int TRIES = 1;
    /** A bog wisp's light: pale cold blue-green. */
    private static final int WISP = 0xFF9EEAD8;
    /** Wisps are rare: one try every this many ticks at density 1. */
    private static final int WISP_EVERY = 6;
    /** How far round the player wisps may appear; they are seen from across a bog. */
    private static final int WISP_REACH = 24;
    /** Sunlit pollen: warm cream. */
    private static final int MOTE = 0xFFFFF4C8;
    /** How far round the player motes may appear: they are specks, only seen close. */
    private static final int MOTE_REACH = 9;
    /** Mote tries per tick at density 1. */
    private static final int MOTE_TRIES = 2;
    /** Light off gold: warm white, a shade of the gilt. */
    private static final int GLINT = 0xFFFFF0B8;
    /** Blocks that catch the light: gold, gilt, coin heaps. Data-driven so a datapack can add its own. */
    private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> GLINTS =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "glints"));
    /** How far round the player glints may appear, in blocks (half that up and down). */
    private static final int GLINT_REACH = 12;
    /** Blind tries per tick at density 1. */
    private static final int GLINT_TRIES = 10;
    /** A face darker than this does not glint. */
    private static final int GLINT_MIN_LIGHT = 7;
    /** Faces a glint may sit on: never the underside. */
    private static final net.minecraft.core.Direction[] GLINT_FACES = {
            net.minecraft.core.Direction.UP, net.minecraft.core.Direction.UP, net.minecraft.core.Direction.NORTH,
            net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST};

    private AmbientMagic() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        // Round what is seen: the camera, which is the player except when spectating through something else.
        net.minecraft.world.entity.Entity player = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        float density = Config.ambientMagicDensity;
        if (level == null || player == null || mc.isPaused() || density <= 0f) {
            return;
        }
        // Gold catches whatever light there is, indoors, underground and in the alley's pocket alike.
        glints(level, player, level.random, density);
        if (level.isRaining() || !level.dimensionType().hasSkyLight()) {
            return;
        }
        RandomSource random = level.random;
        if (!isNight(level)) {
            if (level.getSkyDarken() <= 1) {
                motes(level, player, random, density);
            }
            return;
        }
        if (random.nextInt(Math.max(1, Math.round(WISP_EVERY / density))) == 0) {
            wisp(level, player, random);
        }
        int tries = Math.round(TRIES * density);
        var glow = new at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions(ModParticles.FIREFLY.get(), FIREFLY);
        for (int i = 0; i < tries; i++) {
            int x = player.getBlockX() + random.nextInt(REACH * 2 + 1) - REACH;
            int z = player.getBlockZ() + random.nextInt(REACH * 2 + 1) - REACH;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos ground = new BlockPos(x, top - 1, z);
            if (!level.isLoaded(ground) || !growing(level.getBlockState(ground))
                    || Math.abs(top - player.getY()) > 12) {
                continue;
            }
            double y = top + 0.4 + random.nextDouble() * 1.8;
            level.addParticle(glow, x + random.nextDouble(), y, z + random.nextDouble(), 0, 0, 0);
        }
    }

    /** One try at a wisp: over open water in a swamp, hanging a little above the surface. */
    private static void wisp(ClientLevel level, net.minecraft.world.entity.Entity player, RandomSource random) {
        int x = player.getBlockX() + random.nextInt(WISP_REACH * 2 + 1) - WISP_REACH;
        int z = player.getBlockZ() + random.nextInt(WISP_REACH * 2 + 1) - WISP_REACH;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos surface = new BlockPos(x, top - 1, z);
        if (!level.isLoaded(surface) || Math.abs(top - player.getY()) > 16
                || !level.getBiome(surface).is(Tags.Biomes.IS_SWAMP)) {
            return;
        }
        BlockState state = level.getBlockState(surface);
        if (!state.getFluidState().is(Fluids.WATER) && !state.is(Blocks.LILY_PAD)) {
            return;
        }
        double y = top + 0.4 + random.nextDouble() * 1.2;
        level.addParticle(new at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions(ModParticles.WISP.get(), WISP),
                x + random.nextDouble(), y, z + random.nextDouble(), 0, 0, 0);
    }

    /** Tries at pollen motes: over flowers in bright day, drifting down from a little above them. */
    private static void motes(ClientLevel level, net.minecraft.world.entity.Entity player, RandomSource random,
                              float density) {
        int tries = Math.round(MOTE_TRIES * density);
        for (int i = 0; i < tries; i++) {
            int x = player.getBlockX() + random.nextInt(MOTE_REACH * 2 + 1) - MOTE_REACH;
            int z = player.getBlockZ() + random.nextInt(MOTE_REACH * 2 + 1) - MOTE_REACH;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos plant = new BlockPos(x, top, z);
            if (!level.isLoaded(plant) || Math.abs(top - player.getY()) > 8
                    || !level.getBlockState(plant).is(BlockTags.FLOWERS)) {
                continue;
            }
            double y = top + 0.6 + random.nextDouble() * 1.6;
            level.addParticle(new at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions(ModParticles.MOTE.get(), MOTE),
                    x + random.nextDouble(), y, z + random.nextDouble(), 0, 0, 0);
        }
    }

    /**
     * Tries at a glint: a random spot near the player; if it is gold ({@code #wizards_and_beasts:glints}), a star
     * twinkles on one of its faces that is open to the air and lit. Tries are blind, so glints gather where gold is
     * dense (a Gringotts vault, the hall's coin heaps) and stay rare by a single gold block. Gold in the dark does not
     * glint: the face must see light of at least {@link #GLINT_MIN_LIGHT}.
     */
    private static void glints(ClientLevel level, net.minecraft.world.entity.Entity player, RandomSource random,
                               float density) {
        int tries = Math.round(GLINT_TRIES * density);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < tries; i++) {
            pos.set(player.getBlockX() + random.nextInt(GLINT_REACH * 2 + 1) - GLINT_REACH,
                    player.getBlockY() + random.nextInt(GLINT_REACH + 1) - GLINT_REACH / 2,
                    player.getBlockZ() + random.nextInt(GLINT_REACH * 2 + 1) - GLINT_REACH);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.is(GLINTS)) {
                continue;
            }
            var shape = state.getShape(level, pos);
            if (shape.isEmpty()) {
                continue;
            }
            net.minecraft.core.Direction face = GLINT_FACES[random.nextInt(GLINT_FACES.length)];
            BlockPos open = pos.relative(face);
            if (level.getBlockState(open).isSolidRender()
                    || level.getMaxLocalRawBrightness(open) < GLINT_MIN_LIGHT) {
                continue;
            }
            var box = shape.bounds();
            // Anywhere on the face: random across the box, then pinned just outside it on the face's own axis.
            double x = pos.getX() + onFace(box.minX, box.maxX, face.getStepX(), random);
            double y = pos.getY() + onFace(box.minY, box.maxY, face.getStepY(), random);
            double z = pos.getZ() + onFace(box.minZ, box.maxZ, face.getStepZ(), random);
            level.addParticle(new at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions(ModParticles.GLINT.get(),
                    GLINT), x, y, z, 0, 0, 0);
        }
    }

    /** One coordinate of a point on a face: outside the box on the face's axis, anywhere along it otherwise. */
    static double onFace(double min, double max, int step, RandomSource random) {
        if (step > 0) {
            return max + 0.03;
        }
        if (step < 0) {
            return min - 0.03;
        }
        return min + (max - min) * random.nextDouble();
    }

    /** Night by the sky's own darkness, so it follows the moon and the time the world is set to. */
    private static boolean isNight(ClientLevel level) {
        return level.getSkyDarken() >= 4;
    }

    /** Ground where living green grows: grass, flowers, ferns, leaves, moss. */
    private static boolean growing(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.LEAVES)
                || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN)
                || state.is(Blocks.MOSS_BLOCK);
    }
}
