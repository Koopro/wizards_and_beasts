package at.koopro.neo.item.wizarding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

public class FlooPowderItem extends Item {
    public FlooPowderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        ItemStack stack = context.getItemInHand();

        if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
            if (!level.isClientSide()) {
                BlockState soul = Blocks.SOUL_CAMPFIRE.defaultBlockState()
                        .setValue(CampfireBlock.FACING, state.getValue(CampfireBlock.FACING))
                        .setValue(CampfireBlock.SIGNAL_FIRE, state.getValue(CampfireBlock.SIGNAL_FIRE))
                        .setValue(CampfireBlock.WATERLOGGED, state.getValue(CampfireBlock.WATERLOGGED))
                        .setValue(CampfireBlock.LIT, true);
                level.setBlock(pos, soul, 11);
                stack.shrink(1);
                level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0f, 1.2f);
                if (level instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            32, 0.3, 0.2, 0.3, 0.02);
                }
            }
            return InteractionResult.SUCCESS;
        }

        if (state.is(Blocks.FIRE)) {
            if (!level.isClientSide()) {
                stack.shrink(1);
                if (level instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5,
                            24, 0.4, 0.1, 0.4, 0.02);
                }
                level.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.8f, 1.4f);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }
}
