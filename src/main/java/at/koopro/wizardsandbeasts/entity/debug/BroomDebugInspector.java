package at.koopro.wizardsandbeasts.entity.debug;

import at.koopro.wizardsandbeasts.command.debug.inspect.DebugInspector;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;

/**
 * A broom's flight numbers while it is flying.
 *
 * <p>Broom handling is a pile of per-broom constants resolved from a definition sheet, and the whole
 * point of per-broom identity is that a Nimbus should not feel like a Cleansweep. Whether it
 * <em>does</em> is a question about {@code getTopSpeed} against {@code getCurrentSpeed} in the
 * moment, which no chat command can answer while you are in the air.
 */
@NullMarked
public final class BroomDebugInspector implements DebugInspector.OfEntity {

    @Override
    public String id() {
        return "broom";
    }

    @Override
    public String summary() {
        return "Broom: definition, live speed against its own ceiling, boost, tilt, durability.";
    }

    @Override
    public boolean matches(Entity entity) {
        return entity instanceof BroomEntity;
    }

    @Override
    public DebugReport inspect(Entity entity, ServerPlayer viewer) {
        BroomEntity broom = (BroomEntity) entity;
        DebugReport report = DebugReport.of("Broom · " + broom.getDefinitionId());

        report.row("rider", broom.getControllingPassenger() == null
                ? "none" : broom.getControllingPassenger().getName().getString());
        report.flag("steering", broom.isSteering());

        report.section("speed");
        float top = broom.getTopSpeed();
        report.row("  current", String.format("%.3f", broom.getCurrentSpeed()));
        report.row("  cruise / top", String.format("%.3f / %.3f", broom.getCruiseSpeed(), top));
        report.bar("  of ceiling", top <= 0f ? 0f : broom.getCurrentSpeed() / top);
        report.row("  vertical", String.format("%.3f", broom.getVerticalVelocity()));

        report.section("boost");
        report.flag("  boosting", broom.isBoosting());
        report.flag("  firing", broom.isBoostFiring());
        report.row("  ticks left", broom.getBoostTicksRemaining());
        report.row("  cooldown", broom.getBoostCooldownTicks());

        report.section("attitude");
        report.row("  pitch / roll", String.format("%.2f / %.2f",
                broom.getPitchTilt(), broom.getRollTilt()));
        report.row("  forward lean", String.format("%.2f", broom.getForwardLean()));

        report.section("condition");
        report.row("  durability", broom.getCurrentDurability());
        report.flag("  polished", broom.isPolished());
        return report;
    }
}
