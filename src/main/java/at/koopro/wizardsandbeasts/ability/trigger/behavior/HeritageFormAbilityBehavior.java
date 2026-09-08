package at.koopro.wizardsandbeasts.ability.trigger.behavior;

import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import at.koopro.wizardsandbeasts.ability.trigger.AbilityBehavior;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageTransformService;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * The wheel adapter for every heritage whose transformation is a plain two-form toggle.
 *
 * <p>One class rather than one per heritage: Veela and Vampire differ in what their shape <em>is</em> and
 * in what else happens around it, not in what toggling means, and {@link HeritageTransformService}
 * already resolves the target form from the heritage. The instance is parameterised by the heritage it
 * speaks for purely so the wheel cannot offer a Veela the vampire's button.
 *
 * <p>Declares {@link #ownsToggleState()} for the same reason
 * {@code AnimagusFormAbilityBehavior} does: the single source of truth for "am I transformed" is the
 * player's {@code TransformationState}, which the renderers, the form bridge and the heritage handlers
 * all already read. The framework must not keep a second copy in {@code AbilitySelectionState.toggles}.
 */
@NullMarked
public final class HeritageFormAbilityBehavior implements AbilityBehavior {

    public static final HeritageFormAbilityBehavior VEELA =
            new HeritageFormAbilityBehavior(Heritage.VEELA);
    public static final HeritageFormAbilityBehavior VAMPIRE =
            new HeritageFormAbilityBehavior(Heritage.VAMPIRE);

    private final Heritage heritage;

    public HeritageFormAbilityBehavior(Heritage heritage) {
        this.heritage = heritage;
    }

    @Override
    public boolean ownsToggleState() {
        return true;
    }

    @Override
    public boolean isToggledOn(ServerPlayer player, AbilityDefinition def) {
        PlayerHeritageData data = HeritageTransformService.data(player);
        return data.getSelectedHeritage() == heritage && HeritageTransformService.isTransformed(data);
    }

    /**
     * {@code nowOn} is only the requested direction. The service decides and may refuse — wrong heritage,
     * a variant with no {@code "transformation"} tag, or a change already in flight — exactly as the
     * Animagus adapter behaves.
     */
    @Override
    public void onToggle(ServerPlayer player, AbilityDefinition def, boolean nowOn) {
        if (HeritageTransformService.data(player).getSelectedHeritage() == heritage) {
            HeritageTransformService.toggle(player);
        }
    }
}
