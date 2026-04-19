package at.koopro.neo.brew;

import at.koopro.neo.Neo;
import at.koopro.neo.item.wizarding.BrewItem;
import at.koopro.neo.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;

/**
 * MVP cauldron-brewing flow. Listens for "player sneak-right-clicks a
 * Neo cauldron block while holding an empty glass bottle" and, if a heat
 * source sits directly below the cauldron and the player's inventory
 * contains a complete recipe, consumes the ingredients + the empty bottle
 * and gives back a {@link BrewItem} loaded with the resulting brew.
 *
 * <p><b>Why this design:</b> a fully BlockEntity-backed timed brewing
 * cauldron is the right destination, but it is significantly more code
 * (BlockEntity registry, NBT codec, tick logic, particle pipeline,
 * client renderer for floating ingredients). This event-driven flow
 * proves the data layer end-to-end with one screenful of code and lets
 * us validate Brew/BrewingRecipe/BrewItem in-game today; the BE upgrade
 * can replace this listener later without touching the data layer.
 *
 * <p><b>Heat sources:</b> the block directly below the cauldron must be
 * lit fire, lava, magma, or a lit campfire (vanilla or soul). Mirrors
 * vanilla brewing-stand-style "needs heat" semantics.
 */
@EventBusSubscriber(modid = Neo.MODID)
public final class CauldronBrewing {

    private CauldronBrewing() {}

    @SubscribeEvent
    public static void onUseItemOnBlock(UseItemOnBlockEvent event) {
        // Only handle the BLOCK phase to avoid double-firing per click.
        if (event.getUsePhase() != UseItemOnBlockEvent.UsePhase.BLOCK) return;

        Player player = event.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return;

        Level level = event.getLevel();
        if (level.isClientSide()) return;

        BlockPos pos = event.getPos();
        BlockState clicked = level.getBlockState(pos);
        CauldronTier tier = tierOf(clicked.getBlock());
        if (tier == null) return;

        ItemStack held = event.getItemStack();
        if (!held.is(Items.GLASS_BOTTLE)) return;

        if (!hasHeatSource(level, pos.below())) {
            player.displayClientMessage(
                    Component.literal("\u00A77The cauldron is cold."), true);
            event.cancelWithResult(InteractionResult.FAIL);
            return;
        }

        BrewingRecipe recipe = BrewingRecipes.findMatch(player.getInventory(), tier);
        if (recipe == null) {
            player.displayClientMessage(
                    Component.literal("\u00A77No matching brewing recipe in your inventory."), true);
            event.cancelWithResult(InteractionResult.FAIL);
            return;
        }

        Brew brew = Brews.byId(recipe.outputBrewId());
        if (brew == null) {
            player.displayClientMessage(
                    Component.literal("\u00A7cRecipe references unknown brew '"
                            + recipe.outputBrewId() + "'."), true);
            event.cancelWithResult(InteractionResult.FAIL);
            return;
        }

        recipe.consumeFrom(player.getInventory());
        held.shrink(1);

        ItemStack result = BrewItem.of(brew);
        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }

        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS,
                0.8f, 1.0f);
        player.displayClientMessage(
                Component.literal("\u00A7aBrewed: " + brew.displayName()), true);

        event.cancelWithResult(InteractionResult.CONSUME);
    }

    private static CauldronTier tierOf(Block block) {
        if (block == ModBlocks.BRASS_CAULDRON.get()) return CauldronTier.BRASS;
        if (block == ModBlocks.WIZARDING_COPPER_CAULDRON.get()) return CauldronTier.COPPER;
        if (block == ModBlocks.PEWTER_CAULDRON.get()) return CauldronTier.PEWTER;
        return null;
    }

    private static boolean hasHeatSource(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos);
        if (below.is(Blocks.FIRE) || below.is(Blocks.SOUL_FIRE)) return true;
        if (below.is(Blocks.LAVA)) return true;
        if (below.is(Blocks.MAGMA_BLOCK)) return true;
        if (below.getBlock() instanceof CampfireBlock && below.getValue(CampfireBlock.LIT)) return true;
        return false;
    }
}
