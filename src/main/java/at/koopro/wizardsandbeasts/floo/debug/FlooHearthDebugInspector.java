package at.koopro.wizardsandbeasts.floo.debug;

import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlock;
import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlockEntity;
import at.koopro.wizardsandbeasts.block.floo.FlooFlamesBlock;
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
        return "Fireplace / green flames: address, registry entry, charges, your travel cooldown.";
    }

    @Override
    public boolean matches(ServerLevel level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof FlooFireplaceBlock
                || state.getBlock() instanceof FlooFlamesBlock;
    }

    @Override
    public DebugReport inspect(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer viewer) {
        DebugReport report = DebugReport.of("Floo hearth @ " + pos.toShortString());

        if (state.getBlock() instanceof FlooFlamesBlock) {
            report.row("block", "green flames");
            if (state.hasProperty(FlooFlamesBlock.CHARGES)) {
                report.row("charges", state.getValue(FlooFlamesBlock.CHARGES)
                        + " / " + FlooFlamesBlock.MAX_CHARGES);
            }
            if (state.hasProperty(FlooFlamesBlock.FACING)) {
                report.row("facing", state.getValue(FlooFlamesBlock.FACING).getSerializedName());
            }
            report.row("ticks per charge", FlooFlamesBlock.TICKS_PER_CHARGE);
        } else {
            report.row("block", "fireplace");
        }

        // The block entity's own idea of itself. It may sit under either block: the flames burn in
        // the same column as the grate.
        FlooFireplaceBlockEntity be = findFireplace(level, pos);
        if (be == null) {
            report.note("no fireplace block entity at or below this position");
        } else {
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

    /**
     * The grate for this column.
     *
     * <p>Flames are their own block sitting in the hearth, so a look at the fire hits a position the
     * block entity is not at. One step down finds it; anything further would start reporting a
     * different fireplace.
     */
    private static @Nullable FlooFireplaceBlockEntity findFireplace(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof FlooFireplaceBlockEntity here) {
            return here;
        }
        return level.getBlockEntity(pos.below()) instanceof FlooFireplaceBlockEntity below
                ? below : null;
    }
}
