package at.koopro.wizardsandbeasts.client.apparition;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import org.jspecify.annotations.NullMarked;

/**
 * The lurch of your own vanish, and nobody else's.
 *
 * <p>Every other Apparition cue is driven by a packet about a player, so that a bystander sees the same jump
 * the caster does. This one deliberately is not: a camera effect has exactly one pair of eyes to act on, and
 * widening a spectator's view because somebody else Apparated would be a bug rather than a flourish.
 *
 * <p>On the same {@code ComputeFovModifierEvent} the broom and Butterbeer use, multiplying the player's own
 * FOV setting rather than replacing it, and stepping out of the way entirely once it has decayed — so a
 * player who has not just Apparated is left with exactly the FOV every other system computed. Honours
 * {@code reduceScreenEffects}, like every other camera effect in the mod.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class ApparitionFovHandler {

    private ApparitionFovHandler() {}

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        if (Config.reduceScreenEffects) {
            return;
        }
        float punch = ApparitionResolutionFx.fovPunch();
        if (punch <= 0.0f) {
            return;
        }
        event.setNewFovModifier(event.getNewFovModifier() * (1.0f + punch));
    }
}
