package at.koopro.wizardsandbeasts.spell.petrify;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.entity.creature.ai.DeathGazeGoal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * Resolves a basilisk gaze windup ({@link at.koopro.wizardsandbeasts.effect.BasiliskGazeLockEffect})
 * on natural expiry — mirrors {@code ProtegoWardManager#onEffectExpired}'s idiom of distinguishing a
 * timed-out {@code MobEffectEvent.Expired} from a manually removed effect (e.g. cured by Bezoar's
 * {@code removeAllEffects}, which does not fire {@code Expired} and therefore correctly aborts the
 * outcome rather than resolving it).
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class BasiliskGazeWindupHandler {

    private BasiliskGazeWindupHandler() {}

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!event.getEffectInstance().is(ModEffects.BASILISK_GAZE_LOCK)) {
            return;
        }
        if (DeathGazeGoal.isGazeImmune(player)) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        int amplifier = event.getEffectInstance().getAmplifier();
        if (amplifier >= 1) {
            player.hurtServer(level, player.damageSources().magic(), Float.MAX_VALUE);
        } else {
            PetrifyServerLogic.beginPetrify(level, player);
        }
    }
}
