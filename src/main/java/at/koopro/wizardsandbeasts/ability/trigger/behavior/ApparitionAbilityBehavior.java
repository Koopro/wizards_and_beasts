package at.koopro.wizardsandbeasts.ability.trigger.behavior;

import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import at.koopro.wizardsandbeasts.ability.trigger.AbilityBehavior;
import at.koopro.wizardsandbeasts.ability.trigger.AbilityTarget;
import at.koopro.wizardsandbeasts.apparition.ApparitionServerLogic;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Thin adapter: a wheel activation begins a charge through {@link ApparitionServerLogic#handleRequest}.
 * Every gameplay rule — the licence/test/heritage gates, the Three Ds window, splinching, anti-Apparition
 * wards, side-along pickup, the bespoke Apparition cooldown — stays exactly where it was; this class adds
 * no checks and consumes no framework cooldown (the definition ships {@code cooldownTicks: 0}).
 *
 * <h2>The target is deliberately not forwarded</h2>
 *
 * <p>It used to unpack the wheel's block target and pass it on, and the server logic discarded it: the
 * charge re-runs the server's own raycast every tick and resolves it at release, precisely so a client
 * cannot nominate where it lands. Forwarding a value nobody reads made the seam look like a destination
 * channel, and this adapter refused activation whenever that value was absent — which meant beginning a
 * jump while facing open sky did nothing at all, though the charge is built to wait patiently for a spot
 * to appear.
 */
@NullMarked
public final class ApparitionAbilityBehavior implements AbilityBehavior {

    /** The server entry point, injectable so the adapter seam is testable without a live player. */
    @FunctionalInterface
    public interface Invoker {
        void apparate(ServerPlayer caster);
    }

    public static final ApparitionAbilityBehavior INSTANCE =
            new ApparitionAbilityBehavior(ApparitionServerLogic::handleRequest);

    private final Invoker invoker;

    public ApparitionAbilityBehavior(Invoker invoker) {
        this.invoker = invoker;
    }

    @Override
    public boolean onActivate(ServerPlayer player, AbilityDefinition def, AbilityTarget target) {
        invoker.apparate(player);
        return true;
    }
}
