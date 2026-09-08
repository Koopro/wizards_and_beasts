package at.koopro.wizardsandbeasts.floo.debug;

import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.floo.FlooDeparture;
import at.koopro.wizardsandbeasts.floo.FlooNetworkManager;
import at.koopro.wizardsandbeasts.floo.FlooRegistryEntry;
import at.koopro.wizardsandbeasts.floo.FlooTravelHandler;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Where this wizard can go by fire, and whether they can go right now.
 *
 * <p>The two blocking states — a live arrival cooldown and an in-flight departure windup — are the
 * whole answer to "the fire is not doing anything". Both are transient and neither is visible from
 * the world, so they lead.
 */
@NullMarked
public final class FlooFeatureDebug implements FeatureDebugSection {

    /** Visited addresses listed before the section prints a count instead. */
    private static final int MAX_ADDRESSES = 12;

    @Override
    public String id() {
        return "floo";
    }

    @Override
    public String title() {
        return "Floo Network";
    }

    @Override
    public String summary() {
        return "Arrival cooldown, departure windup, visited addresses, network size.";
    }

    @Override
    public @Nullable Module module() {
        return Module.FLOO_NETWORK;
    }

    @Override
    public void append(DebugReport report, ServerPlayer target, Detail detail) {
        long cooldown = FlooTravelHandler.cooldownRemaining(target);
        boolean departing = FlooDeparture.isDeparting(target.getUUID());
        report.state("  may travel", cooldown <= 0 && !departing ? "yes" : "no",
                cooldown <= 0 && !departing ? ChatPalette.OK : ChatPalette.WARN);
        report.row("  arrival cooldown", cooldown <= 0 ? "clear"
                : cooldown + " / " + FlooTravelHandler.cooldownTicks() + "t");
        report.flag("  departing now", departing);
        if (detail == Detail.BRIEF) {
            return;
        }
        report.row("  departure windup", FlooDeparture.windupTicks() + "t");

        FlooNetworkManager network = FlooNetworkManager.get(target.level());
        report.section("  network");
        report.row("    hearths registered", network.getAllEntries().size());
        long publicCount = network.getAllEntries().stream().filter(FlooRegistryEntry::isPublic).count();
        report.row("    public", publicCount);
        long sealed = network.getAllEntries().stream().filter(e -> !e.isEnabled()).count();
        report.row("    sealed", sealed);

        Set<String> visited = target.getData(ModAttachments.FLOO_VISITED_DESTINATIONS.get());
        report.section("  visited (" + visited.size() + ")");
        if (visited.isEmpty()) {
            report.row("    -", "nowhere yet");
        } else {
            int shown = 0;
            for (String address : visited) {
                if (shown++ >= MAX_ADDRESSES) {
                    report.note("    ... " + (visited.size() - MAX_ADDRESSES) + " more");
                    break;
                }
                report.row("    " + address, network.getEntry(address) == null
                        ? "no longer on the network" : "reachable");
            }
        }

        report.section("  recent travel log");
        var log = network.getRecentLog(5);
        if (log.isEmpty()) {
            report.row("    -", "empty");
        } else {
            log.forEach(line -> report.note("    " + line));
        }
    }
}
