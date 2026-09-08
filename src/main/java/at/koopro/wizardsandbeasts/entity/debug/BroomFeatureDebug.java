package at.koopro.wizardsandbeasts.entity.debug;

import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import at.koopro.wizardsandbeasts.module.Module;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The broom this wizard is on, if any.
 *
 * <p>There is no flight attachment on the player: riding a broom <em>is</em> the state, and the
 * numbers live on the entity. So this is a thin bridge to {@link BroomDebugInspector} for the case
 * where you are asking about a player rather than looking at the broom — which is the usual case,
 * because while somebody is flying you cannot easily put a crosshair on their broom.
 */
@NullMarked
public final class BroomFeatureDebug implements FeatureDebugSection {

    @Override
    public String id() {
        return "brooms";
    }

    @Override
    public String title() {
        return "Broom Flight";
    }

    @Override
    public String summary() {
        return "The broom this player is riding: definition, live speed, boost, durability.";
    }

    @Override
    public @Nullable Module module() {
        return Module.BROOM_FLIGHT;
    }

    @Override
    public void append(DebugReport report, ServerPlayer target, Detail detail) {
        if (!(target.getVehicle() instanceof BroomEntity broom)) {
            report.row("  riding", "not on a broom");
            return;
        }
        report.row("  riding", broom.getDefinitionId());
        float top = broom.getTopSpeed();
        report.bar("  of ceiling", top <= 0f ? 0f : broom.getCurrentSpeed() / top);
        if (detail == Detail.BRIEF) {
            return;
        }
        report.row("  speed", String.format("%.3f (cruise %.3f, top %.3f)",
                broom.getCurrentSpeed(), broom.getCruiseSpeed(), top));
        report.row("  vertical", String.format("%.3f", broom.getVerticalVelocity()));
        report.flag("  steering", broom.isSteering());
        report.flag("  boosting", broom.isBoosting());
        report.row("  boost", broom.getBoostTicksRemaining() + "t left, "
                + broom.getBoostCooldownTicks() + "t cooldown");
        report.row("  durability", broom.getCurrentDurability());
        report.flag("  polished", broom.isPolished());
    }
}
