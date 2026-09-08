package at.koopro.wizardsandbeasts.bestiary.debug;

import at.koopro.wizardsandbeasts.bestiary.data.PlayerBestiaryData;
import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.creature.AlphaRoster;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;

/**
 * How much of the Bestiary this wizard has filled in, and what that unlocks.
 *
 * <p>Tier gates harvesting, so an entry sitting at SEEN when the player expects IDENTIFIED is the
 * answer to most "the drop never happens" reports. Counted per tier first, then listed, because the
 * counts are what you actually read and the list is what you check afterwards.
 */
@NullMarked
public final class BestiaryFeatureDebug implements FeatureDebugSection {

    /** Entries listed before the section prints a count instead. */
    private static final int MAX_ENTRIES = 16;

    @Override
    public String id() {
        return "bestiary";
    }

    @Override
    public String title() {
        return "Bestiary";
    }

    @Override
    public String summary() {
        return "Discovery tier per creature, tier counts, and alpha-roster coverage.";
    }

    @Override
    public @Nullable Module module() {
        return Module.BESTIARY;
    }

    @Override
    public void append(DebugReport report, ServerPlayer target, Detail detail) {
        PlayerBestiaryData data = target.getData(ModAttachments.BESTIARY_DATA.get());
        Map<Identifier, ?> tiers = data.tiers();

        // Counted by tier name rather than by iterating the enum, so a tier added later still shows
        // up here without this section knowing about it.
        Map<String, Integer> byTier = new TreeMap<>();
        data.tiers().values().forEach(tier ->
                byTier.merge(String.valueOf(tier), 1, Integer::sum));

        report.row("  entries recorded", tiers.size());
        byTier.forEach((tier, count) -> report.row("    " + tier, count));

        long alphaKnown = data.tiers().keySet().stream().filter(AlphaRoster::isAlpha).count();
        report.state("  alpha roster seen", alphaKnown + " / " + AlphaRoster.SHIPPED.size(),
                alphaKnown >= AlphaRoster.SHIPPED.size() ? ChatPalette.OK : ChatPalette.MUTED);
        if (detail == Detail.BRIEF) {
            return;
        }

        report.section("  entries");
        if (tiers.isEmpty()) {
            report.row("    -", "nothing recorded");
            return;
        }
        int shown = 0;
        long now = target.level().getGameTime();
        for (Identifier id : data.tiers().keySet()) {
            if (shown++ >= MAX_ENTRIES) {
                report.note("    ... " + (tiers.size() - MAX_ENTRIES) + " more");
                break;
            }
            Long lastHarvest = data.lastHarvests().get(id);
            report.row("    " + id, data.tiers().get(id)
                    + (lastHarvest == null ? "" : ", harvested " + (now - lastHarvest) + "t ago"));
        }
    }
}
