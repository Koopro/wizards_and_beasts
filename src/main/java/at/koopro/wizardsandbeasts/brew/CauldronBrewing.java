package at.koopro.wizardsandbeasts.brew;

import org.jspecify.annotations.Nullable;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.item.brew.BrewItem;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Timed cauldron-brewing flow. This keeps a lightweight in-memory brew queue per
 * cauldron position, so recipes can use {@code heatTimeTicks} without requiring a
 * full BlockEntity migration yet.
 *
 * <p><b>Heat sources:</b> the block directly below the cauldron must be
 * lit fire, lava, magma, or a lit campfire (vanilla or soul). Mirrors
 * vanilla brewing-stand-style "needs heat" semantics.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class CauldronBrewing {
    private static final int MAX_ACTIVE_BREWS = 512;
    private record BrewSite(ResourceKey<Level> dimension, BlockPos pos) {}
    // brewerId + brewPoints captured at start: the brew detaches from the player while it heats, so
    // the Potions OWL credit (complexity-weighted by cauldron tier) is awarded on completion to the
    // original brewer if still online.
    private record ActiveBrew(String brewId, int remainingTicks, UUID brewerId, int brewPoints) {}
    private static final Map<BrewSite, ActiveBrew> ACTIVE_BREWS = new HashMap<>();

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

        BrewSite site = brewKey(level, pos);
        if (ACTIVE_BREWS.containsKey(site)) {
            player.displayClientMessage(Component.literal("\u00A76Cauldron is already brewing."), true);
            event.cancelWithResult(InteractionResult.FAIL);
            return;
        }
        if (ACTIVE_BREWS.size() >= MAX_ACTIVE_BREWS) {
            player.displayClientMessage(Component.literal("\u00A7cToo many active brews. Please wait."), true);
            event.cancelWithResult(InteractionResult.FAIL);
            return;
        }

        recipe.consumeFrom(player.getInventory());
        held.shrink(1);
        ACTIVE_BREWS.put(site, new ActiveBrew(brew.id(), Math.max(1, recipe.heatTimeTicks()),
                player.getUUID(), brewPointsFor(tier)));

        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS,
                0.8f, 1.0f);
        // Both halves are keyed: the sentence, and the brew's own name. Concatenating the
        // name into a literal could only ever produce English, and once the name became a
        // lang key it would have put the raw key in the toast.
        player.displayClientMessage(
                Component.literal("\u00A7a").append(Component.translatable(
                        "brew.wizards_and_beasts.started",
                        Component.translatable(brew.displayName()),
                        recipe.heatTimeTicks())), true);

        event.cancelWithResult(InteractionResult.CONSUME);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE_BREWS.isEmpty()) return;
        Iterator<Map.Entry<BrewSite, ActiveBrew>> iterator = ACTIVE_BREWS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BrewSite, ActiveBrew> entry = iterator.next();
            BrewSite site = entry.getKey();
            Level level = event.getServer().getLevel(site.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }
            BlockPos pos = site.pos();
            if (!hasHeatSource(level, pos.below())) continue;
            if (tierOf(level.getBlockState(pos).getBlock()) == null) {
                iterator.remove();
                continue;
            }

            ActiveBrew active = entry.getValue();
            int next = active.remainingTicks - 1;
            if (next > 0) {
                entry.setValue(new ActiveBrew(active.brewId, next, active.brewerId, active.brewPoints));
                continue;
            }

            Brew brew = Brews.byId(active.brewId);
            if (brew != null) {
                ItemStack result = BrewItem.of(brew);
                Block.popResource(level, pos.above(), result);
                level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8f, 1.1f);
                awardBrewPoints(level, active);
            }
            iterator.remove();
        }
    }

    /** Complexity weight of a successful brew, by cauldron tier — feeds the Potions OWL grade. */
    private static int brewPointsFor(CauldronTier tier) {
        return switch (tier) {
            case BRASS -> 1;
            case COPPER -> 2;
            case PEWTER -> 3;
        };
    }

    /** Credits the original brewer's Potions progress on completion, if they are still online. */
    private static void awardBrewPoints(Level level, ActiveBrew active) {
        if (active.brewPoints() <= 0 || level.getServer() == null) {
            return;
        }
        ServerPlayer brewer = level.getServer().getPlayerList().getPlayer(active.brewerId());
        if (brewer == null) {
            return;
        }
        PlayerSkillData skillData = brewer.getData(ModAttachments.SKILL_DATA.get());
        skillData.addPotionBrewPoints(active.brewPoints());
    }

    private static @Nullable CauldronTier tierOf(Block block) {
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

    private static BrewSite brewKey(Level level, BlockPos pos) {
        return new BrewSite(level.dimension(), pos.immutable());
    }
}
