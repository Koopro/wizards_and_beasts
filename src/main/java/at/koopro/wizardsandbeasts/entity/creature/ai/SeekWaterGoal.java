package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.creature.ability.WaterAffinity;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.level.LevelReader;

/**
 * Aquatic analogue of {@link SeekFireGoal}: drives a dry {@link WaterAffinity} creature toward the nearest
 * water. Self-gates on {@code Module.CREATURES} at {@link #canUse()} and only seeks once meaningfully dry.
 */
public class SeekWaterGoal extends MoveToBlockGoal {

    private final GenericBeastEntity beast;
    private final WaterAffinity affinity;

    public SeekWaterGoal(GenericBeastEntity beast, WaterAffinity affinity, double speedModifier, int searchRange) {
        super(beast, speedModifier, searchRange);
        this.beast = beast;
        this.affinity = affinity;
    }

    @Override
    public boolean canUse() {
        if (!ModuleManager.isEnabled(Module.CREATURES) || beast.isInWater()) {
            return false;
        }
        if (beast.getWaterDryTicks() <= affinity.dryGraceTicks() / 2) {
            return false;
        }
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return ModuleManager.isEnabled(Module.CREATURES) && !beast.isInWater() && super.canContinueToUse();
    }

    @Override
    protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }
}
