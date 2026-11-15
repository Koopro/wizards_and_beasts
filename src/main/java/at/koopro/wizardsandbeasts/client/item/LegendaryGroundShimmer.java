package at.koopro.wizardsandbeasts.client.item;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.wand.WandLookCache;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import at.koopro.wizardsandbeasts.wand.visual.WandLook;
import at.koopro.wizardsandbeasts.wand.visual.WandMetal;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A legendary thing dropped in the grass is not lost there: now and then light catches it, the same still
 * four-point glint gold throws in a vault ({@code AmbientMagic}). Legendary means the item's rarity is the
 * Hallows' ({@code ItemRarities}), or a wand whose fittings are gilt or that is the Elder Wand.
 *
 * <p>Client-side, from the item entities the client already tracks; {@link Config#legendaryGroundShimmer}
 * turns it off. One glint per item about every one and a half seconds, near enough to see.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class LegendaryGroundShimmer {

    private static final double RANGE_SQ = 20.0 * 20.0;
    private static final int EVERY_TICKS = 6;
    /** Chance per check, so about one glint every 30 ticks per item, never in step. */
    private static final float CHANCE = 0.2f;
    private static final int GILT = 0xFFE8A8;
    private static final int HALLOW = 0xE6D8FF;

    private LegendaryGroundShimmer() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Config.legendaryGroundShimmer || mc.level == null || mc.player == null || mc.isPaused()
                || mc.level.getGameTime() % EVERY_TICKS != 0) {
            return;
        }
        RandomSource random = mc.level.random;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity item) || entity.distanceToSqr(mc.player) > RANGE_SQ
                    || random.nextFloat() >= CHANCE) {
                continue;
            }
            int colour = shimmer(mc, item.getItem());
            if (colour == 0) {
                continue;
            }
            // Around the bobbing item, a little above the ground.
            double y = entity.getY() + 0.15 + random.nextDouble() * 0.35;
            mc.level.addParticle(new SpellTintParticleOptions(ModParticles.GLINT.get(), colour),
                    entity.getX() + (random.nextDouble() - 0.5) * 0.4, y,
                    entity.getZ() + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.0, 0.0);
        }
    }

    /** The glint's colour for this stack, or 0 when it is not legendary. */
    private static int shimmer(Minecraft mc, ItemStack stack) {
        if (stack.getItem() instanceof WandItem && mc.level != null) {
            WandLook look = WandLookCache.look(mc.level.registryAccess(), stack);
            return look.elder() ? HALLOW : look.metal() == WandMetal.GOLD ? GILT : 0;
        }
        return stack.getRarity() == Rarity.EPIC ? HALLOW : 0;
    }
}
