package at.koopro.wizardsandbeasts.ability.trigger.behavior;

import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import at.koopro.wizardsandbeasts.ability.trigger.AbilityBehavior;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialServerLogic;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Thin adapter over the Obscurial form toggle. Same ownership shape as
 * {@link AnimagusFormAbilityBehavior}: the on/off truth is the player's active form id in
 * {@code PlayerHeritageData}, read by the transition system, the renderers, the spell policy and the
 * resource manager — so {@link #ownsToggleState()} keeps the framework from persisting a second copy that
 * would desync on every transition, lockout or forced revert.
 *
 * <p>{@code nowOn} is therefore only the requested direction; {@link ObscurialServerLogic#toggleForm}
 * decides and may refuse (mid-transition, exhaustion lockout, insufficient control), with its own feedback.
 */
@NullMarked
public final class ObscurialFormAbilityBehavior implements AbilityBehavior {

    /** The server entry point + state read, injectable so the adapter seam is testable. */
    public interface Invoker {
        void toggleForm(ServerPlayer player);

        boolean isDarkForm(ServerPlayer player);
    }

    public static final ObscurialFormAbilityBehavior INSTANCE = new ObscurialFormAbilityBehavior(new Invoker() {
        @Override
        public void toggleForm(ServerPlayer player) {
            ObscurialServerLogic.toggleForm(player);
        }

        @Override
        public boolean isDarkForm(ServerPlayer player) {
            return ObscurialServerLogic.isDarkForm(player);
        }
    });

    private final Invoker invoker;

    public ObscurialFormAbilityBehavior(Invoker invoker) {
        this.invoker = invoker;
    }

    @Override
    public boolean ownsToggleState() {
        return true;
    }

    @Override
    public boolean isToggledOn(ServerPlayer player, AbilityDefinition def) {
        return invoker.isDarkForm(player);
    }

    @Override
    public void onToggle(ServerPlayer player, AbilityDefinition def, boolean nowOn) {
        invoker.toggleForm(player);
    }
}
