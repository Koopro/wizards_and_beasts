package at.koopro.wizardsandbeasts.event.brew;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Where the Draught of Living Death and Amortentia actually touch the world.
 *
 * <p>Both were vanilla effect lists wearing canon names until the effects behind them existed. Both
 * are now one mod effect plus one rule, which is the cheap half of the {@code BrewEffect} seam: a
 * potion only needs its own component type when it has to hand off to a system with state, the way
 * Felix and Polyjuice do. A potion whose whole behaviour is "while this effect is on you, X does not
 * happen" is better as an effect, because then {@code /effect}, a splash bottle, a cured status and a
 * milk bucket all work on it for free.
 *
 * <p>Follows {@code ButterbeerHandler}: every handler leads with the effect check, so the cost for a
 * player under neither is one lookup on events that were firing anyway.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class SignatureBrewHandler {

    private SignatureBrewHandler() {}

    // ── Draught of Living Death ────────────────────────────────────────────

    /**
     * Nothing hunts a corpse.
     *
     * <p>Unconditional, unlike {@code MELLOW}'s neutral-mob rule and {@code CAMOUFLAGE}'s
     * drops-when-you-attack rule. Neither caveat fits here: the drinker is blind and rooted for the
     * duration and cannot attack anything, so there is no aggression to forgive and no advantage to
     * take. Being overlooked is the only thing they get in exchange for being helpless.
     *
     * <p>Checked on the <em>new</em> target rather than the entity, so this stops something choosing
     * the drinker and leaves everything else about its behaviour alone.
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget != null && newTarget.hasEffect(ModEffects.LIVING_DEATH)) {
            event.setCanceled(true);
        }
    }

    // ── Amortentia ─────────────────────────────────────────────────────────

    /**
     * You cannot bring yourself to hit a person.
     *
     * <p>Cancelled on the <em>attacker's</em> effect, not the victim's — the infatuation belongs to
     * whoever drank it, and a bottle that made the drinker invulnerable would be a very different and
     * much better potion than the fiction describes.
     *
     * <p>Player-on-player only. A drinker can still fight mobs, so a dose is a social catastrophe
     * rather than a death sentence in a cave, and it cannot be used as a grief tool to strand
     * somebody who is being attacked by something.
     *
     * <p>Told, not silent. An attack that simply failed with no explanation reads as a bug or as lag,
     * and the drinker has no other way to discover what was done to them.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player attacker)
                || !attacker.hasEffect(ModEffects.INFATUATION)) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        event.setCanceled(true);
        PlayerFeedback.actionBar(attacker,
                Component.translatable("amortentia.wizards_and_beasts.cannot_strike"));
    }
}
