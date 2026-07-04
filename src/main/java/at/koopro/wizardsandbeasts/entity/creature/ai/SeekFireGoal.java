package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.creature.ability.FireAffinity;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Drives a dry {@link FireAffinity} creature toward the nearest fire/lava block. Mirrors
 * {@code DragonBreathGoal}'s self-gating: {@link #canUse()} returns {@code false} whenever
 * {@code Module.CREATURES} is disabled, so a runtime module toggle takes effect without re-registering
 * goals. Only seeks once the creature is more than half-way through its grace window, so it lingers in
 * fire normally and only wanders off to re-ignite when genuinely drying out.
 */
public class SeekFireGoal extends MoveToBlockGoal {

    private final GenericBeastEntity beast;
    private final FireAffinity affinity;

    public SeekFireGoal(GenericBeastEntity beast, FireAffinity affinity, double speedModifier, int searchRange) {
        super(beast, speedModifier, searchRange);
        this.beast = beast;
        this.affinity = affinity;
    }

    @Override
    public boolean canUse() {
        if (!ModuleManager.isEnabled(Module.CREATURES)) {
            return false;
        }
        if (FireAffinity.isInFireOrLava(beast)) {
            return false;
        }
        // Only start seeking once meaningfully dry, so the creature isn't constantly path-hunting fire.
        if (beast.getFireDryTicks() <= affinity.dryGraceTicks() / 2) {
            return false;
        }
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return ModuleManager.isEnabled(Module.CREATURES)
                && !FireAffinity.isInFireOrLava(beast)
                && super.canContinueToUse();
    }

    @Override
    protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(BlockTags.FIRE) || state.getFluidState().is(FluidTags.LAVA);
    }
}
