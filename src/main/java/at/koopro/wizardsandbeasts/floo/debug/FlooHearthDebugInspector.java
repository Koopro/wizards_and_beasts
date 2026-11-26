package at.koopro.wizardsandbeasts.floo.debug;

import at.koopro.wizardsandbeasts.block.floo.FlooGrateBlock;
import at.koopro.wizardsandbeasts.block.floo.FlooGrateBlockEntity;
import at.koopro.wizardsandbeasts.command.debug.inspect.DebugInspector;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.floo.FlooNetworkManager;
import at.koopro.wizardsandbeasts.floo.FlooRegistryEntry;
import at.koopro.wizardsandbeasts.floo.FlooTravelHandler;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A grate, and whether the network agrees it is one.
 *
 * <p>Floo has two records of the same hearth: the block entity beside the fire and the world-scoped
 * {@link FlooNetworkManager} registry. They are written at different moments by different code, and
 * a hearth that is registered in one and not the other is the shape of every "my address does not
 * work" report. Both are reported, on adjacent lines, so the disagreement is the finding rather than
 * something you have to go and cross-check.
 */
@NullMarked
public final class FlooHearthDebugInspector implements DebugInspector.OfBlock {

    @Override
    public String id() {
        return "floo";
    }

    @Override
    public String summary() {
        return "Floo grate: hearth style, fire, address, registry entry, your travel cooldown.";
    }

    @Override
    public boolean matches(ServerLevel level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof FlooGrateBlock;
    }

    @Override
    public DebugReport inspect(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer viewer) {
        DebugReport report = DebugReport.of("Floo hearth @ " + pos.toShortString());

        FlooGrateBlockEntity be = level.getBlockEntity(pos) instanceof FlooGrateBlockEntity g ? g : null;
        report.row("fire", state.getValue(FlooGrateBlock.FIRE).getSerializedName()
                + (state.getValue(FlooGrateBlock.FUEL) ? ", logs laid" : ", no logs"));
        report.row("facing", state.getValue(FlooGrateBlock.FACING).getSerializedName());
        if (be == null) {
            report.note("no grate block entity here");
        } else {
            report.row("hearth", be.style().map(Object::toString).orElse("not a finished hearth"));
            report.row("opening", be.openingWidth() + " x " + be.openingHeight());
            report.row("green ticks left", be.greenTicks());
            report.section("block entity");
            report.row("  address", be.getNetworkAddress().isEmpty() ? "(unset)" : be.getNetworkAddress());
            report.flag("  registered", be.isRegistered());
            report.flag("  enabled", be.isEnabled());
        }

        // And the network's. Looked up by position, not by the address the block claims, so a block
        // pointing at somebody else's address shows up as a mismatch rather than as a match.
        report.section("network registry");
        FlooNetworkManager network = FlooNetworkManager.get(level);
        FlooRegistryEntry entry = network.findByPos(level.dimension().identifier(), pos);
        if (entry == null) {
            report.state("  entry", "none at this position", ChatPalette.MUTED);
            if (be != null && be.isRegistered()) {
                report.warn("MISMATCH — block says registered, network has no entry here");
            }
        } else {
            report.row("  address", entry.networkAddress());
            report.flag("  enabled", entry.isEnabled());
            report.flag("  public", entry.isPublic());
            report.row("  owner", entry.owner().map(java.util.UUID::toString).orElse("none"));
            if (be != null && !entry.networkAddress().equals(be.getNetworkAddress())) {
                report.warn("MISMATCH — block address '" + be.getNetworkAddress()
                        + "' vs registry '" + entry.networkAddress() + "'");
            }
        }
        report.row("hearths on network", network.getAllEntries().size());

        report.section("you");
        long cooldown = FlooTravelHandler.cooldownRemaining(viewer);
        report.row("  travel cooldown", cooldown <= 0 ? "ready"
                : cooldown + " / " + FlooTravelHandler.cooldownTicks() + "t");
        return report;
    }


}
