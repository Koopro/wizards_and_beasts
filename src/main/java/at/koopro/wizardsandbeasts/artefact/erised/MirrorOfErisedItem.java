package at.koopro.wizardsandbeasts.artefact.erised;

import at.koopro.wizardsandbeasts.item.AnimatedItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/**
 * The Mirror of Erised as carried: places the {@link MirrorOfErisedBlock}, and keeps the animated GeckoLib model
 * it had in hand when it was a plain item (same id, same assets).
 */
public class MirrorOfErisedItem extends BlockItem implements AnimatedItem {

    public MirrorOfErisedItem(Block block, Properties properties) {
        super(block, properties);
    }
}
