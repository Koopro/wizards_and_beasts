package at.koopro.wizardsandbeasts.brew;

import at.koopro.wizardsandbeasts.block.brew.WizardingCauldronBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NullMarked;

/**
 * What is left of the old brewing engine: two questions about a block.
 *
 * <h2>What used to be here</h2>
 * <p>The whole brewing flow. A {@code UseItemOnBlockEvent} handler that scanned the player's
 * inventory for a matching recipe, ate the ingredients out of their backpack, and pushed an entry
 * into a {@code static HashMap<BrewSite, ActiveBrew>} that a server tick counted down. It worked, and
 * every one of its problems came from where the state lived:
 *
 * <ul>
 *   <li>the map was never saved, so a restart destroyed in-progress brews <em>after</em> eating the
 *       ingredients;</li>
 *   <li>it was never cleared on server stop, so entries could outlive the world that made them;</li>
 *   <li>the cold-cauldron branch was {@code continue}, so a brew off the heat sat in the map for the
 *       life of the process;</li>
 *   <li>{@code MAX_ACTIVE_BREWS} was a single global budget — 512 abandoned pots anywhere blocked
 *       brewing for everybody;</li>
 *   <li>and nothing was visible, because the ingredients were never in the cauldron at all.</li>
 * </ul>
 *
 * <p>All of it now lives on {@link at.koopro.wizardsandbeasts.block.brew.CauldronBlockEntity}, which
 * is saved, per-cauldron, synced to clients, and holds the ingredients where a player can see them.
 *
 * <h2>Why this class still exists</h2>
 * <p>Two callers outside the brewing package ask whether a block is one of the mod's cauldrons —
 * the Occamy eggshell, and JEI. Keeping the question here means they do not have to learn about the
 * block class or the tier enum, and it stays the one place that knows what counts as a cauldron.
 */
@NullMarked
public final class CauldronBrewing {

    private CauldronBrewing() {}

    /** Whether {@code block} is one of the mod's brewing cauldrons. */
    public static boolean isCauldron(Block block) {
        return block instanceof WizardingCauldronBlock;
    }

    /**
     * Whether there is a fire under the cauldron at {@code pos}.
     *
     * <p>Delegates to {@link CauldronHeat}, which is where the rule moved when the block entity
     * started asking it every tick.
     */
    public static boolean hasHeatSource(Level level, BlockPos cauldronPos) {
        return CauldronHeat.hasHeatSource(level, cauldronPos.below());
    }
}
