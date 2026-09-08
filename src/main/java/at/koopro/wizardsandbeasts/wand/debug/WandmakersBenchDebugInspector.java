package at.koopro.wizardsandbeasts.wand.debug;

import at.koopro.wizardsandbeasts.command.debug.inspect.DebugInspector;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlock;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * The bench's own view of a shaping session.
 *
 * <p>The tier score is the number that decides what a blank may become, and it is computed from
 * blocks placed <em>around</em> the bench. When a wandmaker cannot make the wand they expect, the
 * question is always which enhancers the bench actually counted — and that is a set of positions
 * held in a block entity, invisible from every angle.
 */
@NullMarked
public final class WandmakersBenchDebugInspector implements DebugInspector.OfBlock {

    /** Enhancer positions listed before the panel prints a count instead. */
    private static final int MAX_ENHANCERS = 8;

    @Override
    public String id() {
        return "bench";
    }

    @Override
    public String summary() {
        return "Wandmaker's bench: tier score, counted enhancers, selected flexibility, inventory.";
    }

    @Override
    public boolean matches(ServerLevel level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof WandmakersBenchBlock;
    }

    @Override
    public DebugReport inspect(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer viewer) {
        DebugReport report = DebugReport.of("Wandmaker's Bench @ " + pos.toShortString());
        if (!(level.getBlockEntity(pos) instanceof WandmakersBenchBlockEntity be)) {
            return report.warn("Bench has NO BLOCK ENTITY — that is the bug.");
        }
        report.flag("working", be.isWorking());
        report.row("tier score", String.format("%.2f", be.getCachedTierScore()));

        int ordinal = be.getSelectedFlexibilityOrdinal();
        WandFlexibility[] flexes = WandFlexibility.values();
        report.row("flexibility", ordinal >= 0 && ordinal < flexes.length
                ? flexes[ordinal].name() : "out of range (" + ordinal + ")");

        List<BlockPos> enhancers = be.getDetectedEnhancers();
        report.section("enhancers counted (" + enhancers.size() + ")");
        if (enhancers.isEmpty()) {
            report.row("  —", "none — the bench is scoring bare");
        } else {
            for (int i = 0; i < enhancers.size() && i < MAX_ENHANCERS; i++) {
                BlockPos enhancer = enhancers.get(i);
                report.row("  " + enhancer.toShortString(),
                        level.getBlockState(enhancer).getBlock().getName().getString());
            }
            if (enhancers.size() > MAX_ENHANCERS) {
                report.note("  … " + (enhancers.size() - MAX_ENHANCERS) + " more");
            }
        }

        report.section("inventory");
        var inventory = be.getInventory();
        boolean any = false;
        for (int slot = 0; slot < inventory.size(); slot++) {
            int count = (int) inventory.getAmountAsLong(slot);
            if (count <= 0) continue;
            ItemStack stack = inventory.getResource(slot).toStack(count);
            any = true;
            report.row("  slot " + slot, count + "x " + stack.getHoverName().getString());
        }
        if (!any) {
            report.row("  —", "empty");
        }
        return report;
    }
}
