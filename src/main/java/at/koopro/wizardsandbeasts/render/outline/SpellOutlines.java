package at.koopro.wizardsandbeasts.render.outline;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;

/**
 * Temporary outlines for gameplay — the whole API a spell needs. Server side only.
 *
 * <pre>
 * OutlineStyle style = OutlineStyle.forSpell(spell.getColor(), durationTicks);
 * SpellOutlines.highlightEntities(entities, style);   // seen by every player
 * SpellOutlines.highlightBlocks(caster, blocks, style); // seen by the caster
 * </pre>
 *
 * <p>Everything here is timed and cleans itself up: expiry, logout and dimension change are handled by the
 * services, so a caller keeps no state and normally never clears. Nothing here can reach a debug outline
 * ({@link DebugOutlines}) — a spell's outline sits on top of one and hands back to it when it ends.
 *
 * <p>Overlap never shortens: casting again over something still outlined keeps it outlined until the later
 * of the two ends, in the newer colour. Empty inputs send nothing. The last second of every outline fades.
 */
@NullMarked
public final class SpellOutlines {

    private SpellOutlines() {}

    /** Outlines these entities for everyone, in one packet. */
    public static void highlightEntities(Collection<? extends Entity> entities, OutlineStyle style) {
        EntityOutlineService.setTimed(entities, style);
    }

    /**
     * Highlights these blocks for {@code viewer} only.
     *
     * @return whether anything was sent
     */
    public static boolean highlightBlocks(ServerPlayer viewer, Iterable<BlockPos> positions, OutlineStyle style) {
        return BlockOutlineService.highlight(viewer, positions, style);
    }

    /** Ends a temporary entity outline early; a debug outline underneath shows again. */
    public static void clearEntity(Entity entity) {
        EntityOutlineService.clearTimed(entity);
    }

    /** Ends every block highlight {@code viewer} has. */
    public static void clearBlocks(ServerPlayer viewer) {
        BlockOutlineService.clearAll(viewer);
    }
}
