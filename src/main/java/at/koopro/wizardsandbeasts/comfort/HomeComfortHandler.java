package at.koopro.wizardsandbeasts.comfort;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Making fear pass sooner.
 *
 * <p>Vanilla has no hook that says "apply this effect, but shorter" — {@code Applicable} can only
 * refuse. So the trick is to refuse the original and immediately apply a scaled copy, using vanilla's
 * own {@link MobEffectInstance#withScaledDuration}.
 *
 * <p><b>Which means guarding against itself.</b> The replacement fires {@code Applicable} again, and
 * without the re-entrancy flag it would scale the copy, and the copy of the copy, until the duration
 * bottomed out at one tick — a fear effect that vanished instantly rather than being shortened by a
 * third. The flag is a plain field because effect application is server-thread only.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class HomeComfortHandler {

    /** True while this handler is re-applying a shortened copy of an effect it just refused. */
    private static boolean reapplying;

    private HomeComfortHandler() {}

    @SubscribeEvent
    public static void onApplicable(MobEffectEvent.Applicable event) {
        if (reapplying) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !victim.hasEffect(ModEffects.HOME_COMFORT)) {
            return;
        }
        MobEffectInstance incoming = event.getEffectInstance();
        if (!incoming.getEffect().is(HomeComfort.FEAR) || incoming.isInfiniteDuration()) {
            return;
        }

        event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        reapplying = true;
        try {
            victim.addEffect(incoming.withScaledDuration(HomeComfort.FEAR_SCALE));
        } finally {
            // finally, not a plain assignment: an exception from a listener downstream of addEffect
            // would otherwise leave the guard stuck true and silently disable the whole mechanic for
            // the rest of the session.
            reapplying = false;
        }
    }
}
