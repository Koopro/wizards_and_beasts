package at.koopro.wizardsandbeasts.ability.trigger.behavior;

import at.koopro.wizardsandbeasts.ability.AnimagusTransformService;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import at.koopro.wizardsandbeasts.ability.trigger.AbilityBehavior;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Thin adapter over the existing Animagus form toggle. Declares {@link #ownsToggleState()} so the framework
 * never writes {@code AbilitySelectionState.toggles} for this ability: the single source of truth stays
 * {@code PlayerAbilityHelper.isCurrentlyTransformed}, which the ritual, the death force-revert,
 * {@code /wandb animagus transform}, the interaction locks and the renderers all already read. The wheel's
 * on-state is <b>derived</b> from that same bit at sync time (see {@code AbilityResolver.activeToggles}).
 *
 * <p>Consequently {@code nowOn} is only the requested direction —
 * {@link AnimagusTransformService#toggleTransform} decides and may refuse (mid-transition, no chosen form,
 * ritual incomplete), emitting its own feedback exactly as before.
 */
@NullMarked
public final class AnimagusFormAbilityBehavior implements AbilityBehavior {

    /** The server entry point + state read, injectable so the adapter seam is testable. */
    public interface Invoker {
        void toggleTransform(ServerPlayer player);

        boolean isTransformed(ServerPlayer player);
    }

    public static final AnimagusFormAbilityBehavior INSTANCE = new AnimagusFormAbilityBehavior(new Invoker() {
        @Override
        public void toggleTransform(ServerPlayer player) {
            AnimagusTransformService.toggleTransform(player);
        }

        @Override
        public boolean isTransformed(ServerPlayer player) {
            return PlayerAbilityHelper.isCurrentlyTransformed(player);
        }
    });

    private final Invoker invoker;

    public AnimagusFormAbilityBehavior(Invoker invoker) {
        this.invoker = invoker;
    }

    @Override
    public boolean ownsToggleState() {
        return true;
    }

    @Override
    public boolean isToggledOn(ServerPlayer player, AbilityDefinition def) {
        return invoker.isTransformed(player);
    }

    @Override
    public void onToggle(ServerPlayer player, AbilityDefinition def, boolean nowOn) {
        invoker.toggleTransform(player);
    }
}
