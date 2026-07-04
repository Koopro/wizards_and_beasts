package at.koopro.wizardsandbeasts.event.skill;

import java.util.ArrayList;
import java.util.List;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.skill.GameplayStat;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Consumers for the Herbology tree:
 * <ul>
 *   <li>{@code harvest_bounty} / {@code bountiful_harvest} — {@link GameplayStat#HARVEST_BONUS_CHANCE}
 *       gives a per-harvest roll to double a broken crop's drops.</li>
 *   <li>{@code green_thumb} — ambient growth: nearby crops occasionally advance a stage.</li>
 *   <li>{@code natural_remedy} — slow passive regeneration while fed.</li>
 * </ul>
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class HerbologyAbilityHandler {

    private static final int GROWTH_INTERVAL = 60;   // ticks between green-thumb growth attempts
    private static final int GROWTH_RADIUS = 4;
    private static final int REGEN_INTERVAL = 80;    // ticks between natural-remedy heal ticks
    private static final int REGEN_MIN_FOOD = 6;     // must be reasonably fed to regenerate

    private HerbologyAbilityHandler() {}

    /** Harvest Bounty: roll to duplicate each crop drop. */
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;
        BlockState state = event.getState();
        if (!state.is(BlockTags.CROPS)) return;

        float chance = SkillSystemAPI.getGameplayBonus(player, GameplayStat.HARVEST_BONUS_CHANCE);
        if (at.koopro.wizardsandbeasts.skill.vocation.VocationHelper.hasGrantedAbility(player, "crop_yield")) {
            chance += at.koopro.wizardsandbeasts.skill.vocation.VocationAbilityHooks.CROP_YIELD_BONUS;
        }
        if (chance <= 0f) return;

        ServerLevel level = event.getLevel();
        RandomSource random = level.getRandom();
        BlockPos pos = event.getPos();

        // Snapshot first: we mutate the same list we iterate.
        List<ItemEntity> bonus = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            if (random.nextFloat() < chance) {
                ItemEntity copy = new ItemEntity(level,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        drop.getItem().copy());
                copy.setDefaultPickUpDelay();
                bonus.add(copy);
            }
        }
        event.getDrops().addAll(bonus);
    }

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (player.tickCount % REGEN_INTERVAL == 0 && SkillSystemAPI.hasAbility(player, "natural_remedy")) {
            if (player.getHealth() < player.getMaxHealth() && player.getFoodData().getFoodLevel() >= REGEN_MIN_FOOD) {
                player.heal(1.0f);
            }
        }

        if (player.tickCount % GROWTH_INTERVAL == 0 && SkillSystemAPI.hasAbility(player, "green_thumb")) {
            nudgeNearbyCrop(player);
        }
    }

    /** Green Thumb: advance one random not-yet-grown crop near the herbologist. */
    private static void nudgeNearbyCrop(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        RandomSource random = level.getRandom();
        BlockPos origin = player.blockPosition();

        for (int attempt = 0; attempt < 6; attempt++) {
            BlockPos pos = origin.offset(
                    random.nextInt(GROWTH_RADIUS * 2 + 1) - GROWTH_RADIUS,
                    random.nextInt(3) - 1,
                    random.nextInt(GROWTH_RADIUS * 2 + 1) - GROWTH_RADIUS);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
                level.setBlock(pos, crop.getStateForAge(crop.getAge(state) + 1), 2);
                level.levelEvent(2005, pos, 0); // bonemeal-style particles
                return;
            }
        }
    }
}
