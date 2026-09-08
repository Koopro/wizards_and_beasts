package at.koopro.wizardsandbeasts.standing.debug;

import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.standing.MagicalStanding;
import at.koopro.wizardsandbeasts.standing.StandingAxis;
import at.koopro.wizardsandbeasts.standing.StandingService;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Where this wizard sits on the three axes, and which of them is actually stored.
 *
 * <p>Only TRADITION is a field. ALIGNMENT and MINISTRY are derived every time they are asked, from
 * corruption and rank respectively, which means they cannot be set and will not persist — and a
 * report that printed all three identically would hide that. The stored/derived split is printed
 * beside each value for exactly that reason.
 */
@NullMarked
public final class StandingFeatureDebug implements FeatureDebugSection {

    @Override
    public String id() {
        return "standing";
    }

    @Override
    public String title() {
        return "Magical Standing";
    }

    @Override
    public String summary() {
        return "The three alignment axes, their bands, and which are stored versus derived.";
    }

    @Override
    public void append(DebugReport report, ServerPlayer target, Detail detail) {
        float bound = StandingService.bound();
        for (StandingAxis axis : StandingAxis.values()) {
            float value = StandingService.valueOf(target, axis);
            // Axes run -bound..+bound, so the bar is the value mapped into 0..1 with the midpoint
            // at neutral. Printing the raw number beside it keeps the sign, which the bar loses.
            report.bar("  " + axis.name().toLowerCase(java.util.Locale.ROOT),
                    bound <= 0f ? 0.5f : (value + bound) / (2f * bound));
            report.row("    value", String.format("%+.2f of %.0f", value, bound));
            if (detail == Detail.BRIEF) {
                continue;
            }
            report.row("    band", StandingService.bandOf(target, axis).name());
            report.row("    poles", axis.negativePole().getString()
                    + " .. " + axis.positivePole().getString());
            report.flag("    stored", axis.isStored());
        }
        if (detail == Detail.BRIEF) {
            return;
        }
        MagicalStanding standing = StandingService.get(target);
        report.section("  raw attachment");
        report.row("    tradition", String.format("%+.3f", standing.tradition()));
        report.row("    light", String.format("%+.3f", standing.light()));
        report.flag("    neutral", standing.isNeutral());
    }
}
