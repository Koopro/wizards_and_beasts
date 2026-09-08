package at.koopro.wizardsandbeasts.trunk.debug;

import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.trunk.TrunkRegistryData;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Pocket dimensions, and the one field that decides whether this player can get back out.
 *
 * <p>The return position is stored per player in a world-scoped registry, not on the player, and it
 * is what a trunk exit reads. A player stuck inside a pocket almost always has no return entry —
 * which looks from every other angle exactly like a broken exit block.
 */
@NullMarked
public final class PocketFeatureDebug implements FeatureDebugSection {

    @Override
    public String id() {
        return "pockets";
    }

    @Override
    public String title() {
        return "Pocket Dimensions";
    }

    @Override
    public String summary() {
        return "Registered pockets, impoundments, and this player's stored return position.";
    }

    @Override
    public @Nullable Module module() {
        return Module.POCKET_DIMENSIONS;
    }

    @Override
    public void append(DebugReport report, ServerPlayer target, Detail detail) {
        TrunkRegistryData registry = TrunkRegistryData.get(target.level());

        // The exit. See the class note.
        var returnPos = registry.getReturnPosition(target.getUUID());
        report.state("  return position",
                returnPos.map(pos -> pos.toShortString()).orElse("NONE - cannot exit a pocket"),
                returnPos.isPresent() ? ChatPalette.OK : ChatPalette.BAD);
        report.row("  return dimension",
                registry.getReturnDimension(target.getUUID()).orElse("(none)"));
        if (detail == Detail.BRIEF) {
            return;
        }

        var pockets = registry.getAllPockets();
        report.section("  registry (" + pockets.size() + " pockets)");
        if (pockets.isEmpty()) {
            report.row("    -", "none created in this world");
            return;
        }
        long impounded = pockets.stream()
                .filter(record -> registry.isImpounded(record.pocketId()))
                .count();
        report.row("    impounded", impounded);
        long uninitialised = pockets.stream()
                .filter(record -> !registry.isInitialized(record.pocketId()))
                .count();
        if (uninitialised > 0) {
            report.state("    uninitialised", String.valueOf(uninitialised), ChatPalette.WARN);
        }
    }
}
