package at.koopro.wizardsandbeasts.event.floo;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jspecify.annotations.NullMarked;

/**
 * What {@link at.koopro.wizardsandbeasts.effect.FlooProtectedEffect} actually buys.
 *
 * <p>Burning damage only, and read off {@code #minecraft:is_fire} rather than named source by named
 * source. A tag is what makes this finishable: enumerating {@code inFire}, {@code onFire},
 * {@code lava}, {@code hotFloor} and whatever a datapack adds is a list that is wrong the moment
 * anything new is registered, and being wrong here means a wizard burns to death standing in a
 * fireplace the fiction says is safe.
 *
 * <p>Cancelled outright rather than reduced. Half damage from a fire you are lore-immune to is not a
 * softer version of the rule, it is the rule not applying.
 *
 * <p>Deliberately narrow otherwise. A Floo hearth is not a panic room: mobs, falls, drowning,
 * suffocation and magic all still land on someone standing in green fire. The one promise is that the
 * fire does not burn you.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class FlooProtectionEvents {

    private FlooProtectionEvents() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypeTags.IS_FIRE)) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity.hasEffect(ModEffects.FLOO_PROTECTED)) {
            // Clear the burning as well as the hit. Without this the entity keeps its remaining fire
            // ticks, is harmlessly on fire for as long as it stands in the grate, and bursts back
            // into damage on the first tick after it steps out — which reads as the protection
            // having done nothing at all.
            entity.clearFire();
            event.setCanceled(true);
        }
    }
}
