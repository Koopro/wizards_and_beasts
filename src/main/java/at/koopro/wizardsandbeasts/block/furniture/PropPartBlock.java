package at.koopro.wizardsandbeasts.block.furniture;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.NullMarked;

/**
 * Never placed: a holder for the moving parts of clocks, globes and floating books. Each {@link Part} is a baked JSON
 * block model, so {@link at.koopro.wizardsandbeasts.client.furniture.MovingPropRenderer} draws a part with the
 * ordinary block pipeline ({@code submitBlock}) and needs no model loading of its own. No item, no loot.
 */
@NullMarked
public class PropPartBlock extends Block {

    public enum Part implements StringRepresentable {
        CLOCK_HOUR, CLOCK_MINUTE, GLOBE, BOOK, BOOK_PAGE, PENDULUM;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    public PropPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, Part.CLOCK_HOUR));
    }

    @Override
    protected MapCodec<PropPartBlock> codec() {
        return simpleCodec(PropPartBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }
}
