package at.koopro.wizardsandbeasts.block;

import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

/**
 * Holds nothing. A block model is baked once and cannot move, so a banner that waves has to be
 * drawn by a block entity renderer, and a renderer needs a block entity to hang off.
 *
 * <p>Only the {@link HouseBannerBlock#HALF lower} half carries one; {@code HouseBannerRenderer}
 * draws both halves from there.
 */
public class HouseBannerBlockEntity extends BlockEntity {

    public HouseBannerBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        super(ModBlockEntities.HOUSE_BANNER.get(), pos, state);
    }
}
