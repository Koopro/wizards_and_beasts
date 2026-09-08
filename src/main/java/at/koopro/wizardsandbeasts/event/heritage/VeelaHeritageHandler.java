package at.koopro.wizardsandbeasts.event.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageTransformService;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * A Veela's fury: {@code veela_harpy} is a shape they can choose, and one that takes them when hurt.
 *
 * <p>"Whose fury can sprout wings of flame" — so the harpy is not purely a toggle. A badly wounded Veela
 * turns whether they meant to or not, which is the half of the mechanic the lore actually describes. The
 * voluntary half lives on the ability wheel ({@code VeelaFormAbilityBehavior}); both routes end at
 * {@link HeritageTransformService}, so there is one implementation of what changing shape means.
 *
 * <p>Only Full and Half Veela reach either route — Quarter-Veela does not carry the
 * {@code "transformation"} tag, and the service checks it.
 *
 * <h2>Control is never taken</h2>
 * Unlike the werewolf, a furious Veela is still driven by their player. The transformation is forced;
 * their hands are not. This heritage registers no {@code FormConstraintSource} at all, which is the
 * whole difference between "you have changed shape" and "you are a passenger".
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class VeelaHeritageHandler {

    /** Scans are cheap but the health check is not urgent; five times a second is far finer than fury. */
    private static final int SCAN_INTERVAL_TICKS = 20;

    /** At or below this fraction of max health, the wings come out whether or not they were asked for. */
    private static final float FURY_HP_THRESHOLD = 0.35f;

    /**
     * The Veela stays a harpy until they are meaningfully out of danger.
     *
     * <p>Higher than {@link #FURY_HP_THRESHOLD} on purpose. If the two matched, a Veela hovering at the
     * threshold would flicker between shapes every scan — the same hysteresis the moonlight exposure
     * counter needs and for the same reason.
     */
    private static final float CALM_HP_THRESHOLD = 0.60f;

    private VeelaHeritageHandler() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ModuleManager.isEnabled(Module.HERITAGE)) {
            return;
        }
        if (event.getServer().getTickCount() % SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
            if (data.getSelectedHeritage() != Heritage.VEELA || !HeritageTransformService.canTransform(data)) {
                continue;
            }
            if (!(player.level() instanceof ServerLevel level)) {
                continue;
            }
            scan(player, level, data);
        }
    }

    private static void scan(ServerPlayer player, ServerLevel level, PlayerHeritageData data) {
        float ratio = player.getHealth() / Math.max(1.0f, player.getMaxHealth());
        boolean transformed = HeritageTransformService.isTransformed(data);

        if (!transformed && ratio <= FURY_HP_THRESHOLD) {
            if (HeritageTransformService.enter(player, data)) {
                onFury(player, level);
            }
            return;
        }

        // Only the *forced* change reverses on its own. A Veela who chose the shape keeps it until they
        // choose otherwise, so recovering past the calm threshold is not on its own a reason to revert —
        // it only is if the wings came out uninvited. That intent is not stored anywhere, so the rule
        // used here is the conservative one: recovery reverts, and a player who wanted to stay simply
        // toggles again. See the note in the class javadoc of HeritageTransformService about state.
        if (transformed && ratio >= CALM_HP_THRESHOLD) {
            HeritageTransformService.exit(player, data);
        }
    }

    private static void onFury(ServerPlayer player, ServerLevel level) {
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT,
                SoundSource.PLAYERS, 0.8f, 1.3f);
        level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(),
                40, 0.5, 0.8, 0.5, 0.06);
        PlayerFeedback.toast(player, NoticeKind.WARN,
                Component.translatable("message.wizards_and_beasts.veela.fury.title"),
                Component.translatable("message.wizards_and_beasts.veela.fury.body"));
    }
}
