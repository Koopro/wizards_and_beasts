package at.koopro.wizardsandbeasts.entity.niffler.ai;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

public class NifflerSeekShinyBlockGoal extends Goal {

    public static final TagKey<Block> NIFFLER_SHINY_BLOCKS = TagKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "niffler_shiny_blocks"));

    private static final int SEARCH_RADIUS = 12;
    private static final int DIG_DURATION = 60; // 3 seconds

    private final NifflerEntity niffler;
    @Nullable private BlockPos targetBlock;
    private int digTicks;
    private boolean digging;

    public NifflerSeekShinyBlockGoal(NifflerEntity niffler) {
        this.niffler = niffler;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (niffler.isCarried() || niffler.isPouchFull()) return false;
        targetBlock = findNearestShinyBlock();
        return targetBlock != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (niffler.isCarried() || niffler.isPouchFull()) return false;
        return targetBlock != null;
    }

    @Override
    public void start() {
        digging = false;
        digTicks = 0;
    }

    @Override
    public void tick() {
        if (targetBlock == null) return;
        Vec3 center = Vec3.atCenterOf(targetBlock);
        niffler.getLookControl().setLookAt(center.x, center.y, center.z, 30f, 30f);

        if (!digging) {
            niffler.getNavigation().moveTo(center.x, center.y, center.z, 1.1);
            double distSq = niffler.position().distanceToSqr(center);
            if (distSq < 4.0) {
                digging = true;
                niffler.getNavigation().stop();
                niffler.level().playSound(null, niffler.blockPosition(),
                        ModSounds.NIFFLER_DIG.get(), SoundSource.NEUTRAL, 0.8f, 1.0f);
            }
        } else {
            digTicks++;
            if (digTicks >= DIG_DURATION) {
                spawnDroppedItem();
                targetBlock = null;
                digging = false;
            }
        }
    }

    @Override
    public void stop() {
        targetBlock = null;
        digging = false;
        digTicks = 0;
        niffler.getNavigation().stop();
    }

    private void spawnDroppedItem() {
        if (!(niffler.level() instanceof ServerLevel serverLevel)) return;
        if (targetBlock == null) return;
        var blockState = serverLevel.getBlockState(targetBlock);
        java.util.Optional<net.minecraft.resources.ResourceKey<LootTable>> lootKeyOpt = blockState.getBlock().getLootTable();
        if (lootKeyOpt.isEmpty()) return;
        LootTable lootTable = serverLevel.getServer().reloadableRegistries().getLootTable(lootKeyOpt.get());

        LootParams params = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(targetBlock))
                .withParameter(LootContextParams.BLOCK_STATE, blockState)
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .create(LootContextParamSets.BLOCK);

        List<ItemStack> drops = lootTable.getRandomItems(params);
        if (!drops.isEmpty()) {
            ItemStack drop = drops.get(0).copyWithCount(1);
            Vec3 pos = Vec3.atCenterOf(targetBlock);
            ItemEntity ie = new ItemEntity(serverLevel, pos.x, pos.y + 0.5, pos.z, drop);
            ie.setDefaultPickUpDelay();
            serverLevel.addFreshEntity(ie);
        }
    }

    @Nullable
    private BlockPos findNearestShinyBlock() {
        BlockPos origin = niffler.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS))) {
            if (niffler.level().getBlockState(pos).is(NIFFLER_SHINY_BLOCKS)) {
                double d = niffler.distanceToSqr(Vec3.atCenterOf(pos));
                if (d < bestDist) {
                    bestDist = d;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }
}
