package at.koopro.wizardsandbeasts.block.furniture;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

/**
 * Furniture with a part that really moves: the still part is the baked block model, the moving part is drawn by
 * {@link at.koopro.wizardsandbeasts.client.furniture.MovingPropRenderer} from a {@link PropPartBlock} model.
 */
@NullMarked
public class MovingPropBlock extends FurnitureBlock implements EntityBlock {

    public enum Kind {
        /** Hour and minute hand, set from the day time. */
        CLOCK,
        /** A sphere turning on a tilted axis. */
        GLOBE,
        /** An open book bobbing in the air, turning slowly, a page lifting and falling. */
        BOOK,
        /** A longcase clock: hands on the dial in its hood, a pendulum swinging in its trunk. */
        GRANDFATHER
    }

    /** Motes of gilt light off a floating book's pages. */
    private static final int GILT_ARGB = 0xFFEBC874;

    private final Kind kind;

    public MovingPropBlock(BlockBehaviour.Properties properties, Spec spec, Kind kind) {
        super(properties, spec);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends MovingPropBlock> codec() {
        return simpleCodec(properties -> new MovingPropBlock(properties, spec(), kind));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MovingPropBlockEntity(pos, state);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (kind == Kind.BOOK && random.nextInt(3) == 0) {
            level.addParticle(new SpellTintParticleOptions(ModParticles.ARCANE_MOTE.get(), GILT_ARGB),
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5, pos.getY() + 0.6 + random.nextDouble() * 0.2,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5, 0, 0.02, 0);
        }
    }
}
