package at.koopro.wizardsandbeasts.entity.debug;

import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSections;
import at.koopro.wizardsandbeasts.command.debug.inspect.DebugInspector;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;

/**
 * A wizard, summarised by every feature at once.
 *
 * <p>Looking at somebody is the natural gesture for "what is going on with them", and it is the one
 * case where the panel and the command genuinely want different amounts: the full dump is several
 * hundred rows across twenty subsystems, which is a chat report, not something to float over a
 * player's head. So this asks every section for its {@link FeatureDebugSection.Detail#BRIEF} answer
 * — the two or three rows each subsystem would lead with — and leaves
 * {@code /wandb debug feature all} to print the rest.
 */
@NullMarked
public final class PlayerDebugInspector implements DebugInspector.OfEntity {

    @Override
    public String id() {
        return "player";
    }

    @Override
    public String summary() {
        return "A player: the headline row from every feature section at once.";
    }

    @Override
    public boolean matches(Entity entity) {
        return entity instanceof ServerPlayer;
    }

    @Override
    public DebugReport inspect(Entity entity, ServerPlayer viewer) {
        ServerPlayer target = (ServerPlayer) entity;
        return FeatureDebugSections.reportAll(target, FeatureDebugSection.Detail.BRIEF);
    }
}
