package at.koopro.wizardsandbeasts.block;

import at.koopro.wizardsandbeasts.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class MandrakeCropBlock extends CropBlock {

    public MandrakeCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModBlocks.MANDRAKE_SEEDS.get();
    }

    @Override
    public int getMaxAge() {
        return 7;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack stack, boolean willHarvest, FluidState fluid) {
        if (!level.isClientSide()) {
            level.playSound(null, pos, SoundEvents.GHAST_SCREAM, SoundSource.BLOCKS, 1.2f,
                    0.75f + level.random.nextFloat() * 0.5f);
        }
        return super.onDestroyedByPlayer(state, level, pos, player, stack, willHarvest, fluid);
    }
}
