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


/**
 * The bench's own view of a shaping session.
 *
 * <p>A making is held in the block entity, act by act (stage, wood, core, the running score), and a
 * started act waits there for its player's performance. None of it shows on the block.
 */
@NullMarked
public final class WandmakersBenchDebugInspector implements DebugInspector.OfBlock {

    @Override
    public String id() {
        return "bench";
    }

    @Override
    public String summary() {
        return "Wandmaker's bench: the making under way, tools, selected flexibility, inventory.";
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
        report.row("stage", be.stage().name());
        if (be.jobWood() != null) {
            report.row("wood", be.jobWood().toString());
        }
        if (be.jobCore() != null) {
            report.row("core", be.jobCore().toString());
        }
        report.row("mean score", String.format("%.2f", be.meanScore()));
        report.flag("act pending", be.pending() != null);
        report.row("tools", Integer.toBinaryString(be.toolMask()));

        int ordinal = be.getSelectedFlexibilityOrdinal();
        WandFlexibility[] flexes = WandFlexibility.values();
        report.row("flexibility", ordinal >= 0 && ordinal < flexes.length
                ? flexes[ordinal].name() : "out of range (" + ordinal + ")");

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
