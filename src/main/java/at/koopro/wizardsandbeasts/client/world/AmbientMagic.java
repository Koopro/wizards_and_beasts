package at.koopro.wizardsandbeasts.client.world;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Quiet magic in the open world, outside any built location: on clear nights, fireflies blink over grass, flowers and
 * leaves near the player. The world felt dead between the mod's places; this is the first, smallest piece of making it
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

    private AmbientMagic() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        float density = Config.ambientMagicDensity;
        if (level == null || player == null || mc.isPaused() || density <= 0f) {
            return;
        }
        if (!isNight(level) || level.isRaining() || !level.dimensionType().hasSkyLight()) {
            return;
        }
        RandomSource random = level.random;
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
