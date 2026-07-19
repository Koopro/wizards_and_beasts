package at.koopro.wizardsandbeasts.ability.trigger.behavior;

import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import at.koopro.wizardsandbeasts.ability.trigger.AbilityBehavior;
import at.koopro.wizardsandbeasts.ability.trigger.AbilityTarget;
import at.koopro.wizardsandbeasts.legilimency.LegilimencyServerLogic;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Thin adapter: unpacks the wheel's entity target and hands it to the untouched
 * {@link LegilimencyServerLogic#handleRequest}. The heritage gate, the 8-block reach re-check, the
 * Occlumency resist roll and the 600-tick bespoke cooldown all stay there; this class adds no checks and
 * consumes no framework cooldown (the definition ships {@code cooldownTicks: 0}).
 */
@NullMarked
public final class LegilimencyAbilityBehavior implements AbilityBehavior {

    /** The server entry point, injectable so the adapter seam is testable without a live player. */
    @FunctionalInterface
    public interface Invoker {
        void legilimise(ServerPlayer caster, int targetEntityId);
    }

    public static final LegilimencyAbilityBehavior INSTANCE =
            new LegilimencyAbilityBehavior(LegilimencyServerLogic::handleRequest);

    private final Invoker invoker;

    public LegilimencyAbilityBehavior(Invoker invoker) {
        this.invoker = invoker;
    }

    @Override
    public boolean onActivate(ServerPlayer player, AbilityDefinition def, AbilityTarget target) {
        if (target.kind() != AbilityTarget.Kind.ENTITY) {
            return false; // no mind picked — soft no-op
        }
        invoker.legilimise(player, target.entityId());
        return true;
    }
}
