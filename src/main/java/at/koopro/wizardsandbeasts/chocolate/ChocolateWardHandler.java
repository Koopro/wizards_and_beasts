package at.koopro.wizardsandbeasts.chocolate;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Refusing the despair while the chocolate is still working.
 *
 * <p>Hooked on {@link MobEffectEvent.Applicable} rather than on a tick that strips the effect back
 * off: a per-tick strip would let a Dementor's aura land for one frame every frame, which flickers
 * the HUD and — worse — fires every {@code onEffectStarted} in the mod once a tick. Denying the
 * application outright means the effect never exists at all.
 *
 * <p>Scoped to the {@code chocolate_frog_cures} tag, so this cannot accidentally make a wizard
 * immune to anything else while the ward is up.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ChocolateWardHandler {

    private ChocolateWardHandler() {}

    @SubscribeEvent
    public static void onApplicable(MobEffectEvent.Applicable event) {
        if (!event.getEntity().hasEffect(ModEffects.CHOCOLATE_WARD)) {
            return;
        }
        if (ChocolateFrog.isDespair(event.getEffectInstance().getEffect())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }
}
