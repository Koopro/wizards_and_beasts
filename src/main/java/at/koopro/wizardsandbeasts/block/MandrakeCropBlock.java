package at.koopro.wizardsandbeasts.block;

import at.koopro.wizardsandbeasts.mandrake.MandrakeScream;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

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
            if (isMaxAge(state)) {
                // Full grown. The cry reaches everything within earshot, the puller included — see
                // MandrakeScream, which owns the radius, the effects and who earmuffs spare.
                MandrakeScream.adult(level, Vec3.atCenterOf(pos), player);
                // Pulling a mature Mandrake is the harvest event that counts toward the Herbology OWL.
                if (player instanceof ServerPlayer serverPlayer) {
                    PlayerSkillData skillData = serverPlayer.getData(ModAttachments.SKILL_DATA.get());
                    skillData.incrementPlantsHarvested();
                }
            } else {
                // A seedling only squeals. Loud enough to be a warning about what a grown one does,
                // and harmless, so clearing a half-grown bed is not a self-inflicted debuff.
                level.playSound(null, pos, SoundEvents.GHAST_SCREAM, SoundSource.BLOCKS, 0.45f,
                        1.6f + level.random.nextFloat() * 0.4f);
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, stack, willHarvest, fluid);
    }
}
