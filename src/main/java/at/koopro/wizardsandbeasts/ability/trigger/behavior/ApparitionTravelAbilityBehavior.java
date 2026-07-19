package at.koopro.wizardsandbeasts.ability.trigger.behavior;

import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import at.koopro.wizardsandbeasts.ability.trigger.AbilityBehavior;
import at.koopro.wizardsandbeasts.network.apparition.OpenApparitionSelectorS2CPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

/**
 * Opens the memorised-destination selector. Separate from the aimed Apparition ability so both travel
 * modes ride the wheel as ordinary entries — no modifier keys, no special-casing in the input driver.
 *
 * <p>Activating only opens the screen; the actual jump happens when the player picks a destination and the
 * server re-runs every Apparition gate in {@code ApparitionServerLogic.travelTo}. The framework has already
 * confirmed the player holds the ability before this runs, so the screen never opens for someone who would
 * simply be refused.
 */
@NullMarked
public final class ApparitionTravelAbilityBehavior implements AbilityBehavior {

    public static final ApparitionTravelAbilityBehavior INSTANCE = new ApparitionTravelAbilityBehavior();

    private ApparitionTravelAbilityBehavior() {}

    @Override
    public boolean onActivate(ServerPlayer player, AbilityDefinition def) {
        PacketDistributor.sendToPlayer(player, new OpenApparitionSelectorS2CPayload());
        return true;
    }
}
