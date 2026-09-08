package at.koopro.wizardsandbeasts.event.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.TransformationState;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfConfig;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfRules;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfState;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfTransformService;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The moon's claim on a werewolf: the lifecycle driver for forced lycanthropy.
 *
 * <p>The mod shipped ten heritages, three werewolf variants all tagged {@code moon_sensitive}, a
 * {@code werewolf_wolf} form with its own rig and animations, and a transition config for entering
 * and leaving it — and <b>no moon-phase code anywhere</b>. This class is where that gap was closed;
 * it now also owns the moonlight-exposure counter and hands the actual change to
 * {@link WerewolfTransformService}. The constraints that make a transformed werewolf a passenger in
 * their own body live in {@link WerewolfControlHandler}.
 *
 * <h2>The rules</h2>
 * <ul>
 *   <li><b>Full moon and night, and only then.</b> Vanilla numbers the phases with {@code 0} as the
 *       full moon, so that is the single phase that counts.</li>
 *   <li><b>Natural dimensions only.</b> There is no moon over the Nether or the End, and the day-time
 *       clock keeps ticking there. See {@link WerewolfRules#fullMoonNight(ServerLevel)}.</li>
 *   <li><b>Moonlight has to reach you.</b> A werewolf under open sky banks exposure; one under a roof
 *       loses it faster than they gained it. A cellar is therefore a real defence and a slow walk home
 *       is not — see {@link WerewolfConfig#exposureThreshold}.</li>
 *   <li><b>It is a compulsion, not a toggle.</b> A player who reverts by hand while the moon is up is
 *       taken again as soon as they have soaked up the moonlight for it. That is the whole point of the
 *       affliction, and it is why this runs on a tick rather than on a one-shot moonrise event.</li>
 *   <li><b>Wolfsbane does not stop it.</b> Canon is emphatic — Lupin still becomes a wolf; the potion
 *       preserves his <em>mind</em>, not his shape. So the potion decides whether the wolf keeps its
 *       player, never whether the change happens (unless a server opts into
 *       {@link WerewolfConfig#wolfsbaneSuppressesTransform}).</li>
 * </ul>
 *
 * <p>Reverting happens at dawn, or the moment the moon wanes, or on leaving for a dimension with no
 * sky — whichever comes first. A <em>medicated</em> werewolf is the one exception: at dawn they are
 * offered the shape rather than stripped of it, and keep it until they give it back or the potion runs
 * out.
 *
 * <p>Gated on {@link Module#HERITAGE}: switching heritage off stops <em>new</em> transformations and
 * deliberately does not strip a form a player is already wearing.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class WerewolfMoonHandler {

    /**
     * Ticks between moon/exposure scans.
     *
     * <p>A second is far finer than the thing being watched: a Minecraft night is 9000 ticks and the
     * moon changes once a day. Scanning every tick would ask every online player's dimension and
     * heritage 20 times a second to answer a question that changes twice a night. The exposure rates in
     * {@link WerewolfConfig} are per scan, so this interval is also the counter's unit of time.
     */
    private static final int SCAN_INTERVAL_TICKS = 20;

    private WerewolfMoonHandler() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ModuleManager.isEnabled(Module.HERITAGE)) {
            return;
        }
        boolean scanTick = event.getServer().getTickCount() % SCAN_INTERVAL_TICKS == 0;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
            if (!WerewolfRules.isWerewolf(data)) {
                continue;
            }
            if (!(player.level() instanceof ServerLevel level)) {
                continue;
            }

            // A change already committed finishes on its own clock, moon or no moon: the moon started
            // it, the delay owns it from there.
            if (WerewolfState.hasPendingTransform(data)) {
                WerewolfTransformService.tickPending(player, level, data);
                continue;
            }
            if (scanTick) {
                scan(player, level, data);
            }
        }
    }

    private static void scan(ServerPlayer player, ServerLevel level, PlayerHeritageData data) {
        healStaleTransitioningState(player, data);

        boolean moonUp = WerewolfRules.fullMoonNight(level);
        boolean wolf = WerewolfRules.inWolfForm(data);

        if (moonUp) {
            if (wolf) {
                refreshControl(player, data);
            } else {
                accumulateExposure(player, level, data);
            }
            return;
        }

        if (!wolf) {
            // The moon is down: whatever the werewolf soaked up drains away.
            if (WerewolfState.getExposure(data) > 0) {
                WerewolfState.addExposure(data, -WerewolfConfig.exposureDecay, WerewolfConfig.exposureThreshold);
            }
            return;
        }

        // Wolf shape with no moon behind it. A medicated werewolf owns the shape and keeps it; an
        // unmedicated one never chose it, so dawn takes it back.
        if (WerewolfRules.hasWolfsbane(player)) {
            WerewolfTransformService.markVoluntaryStay(player, data);
            return;
        }
        WerewolfTransformService.revert(player, level, data, true);
    }

    /**
     * Banks moonlight, and starts the change once there is enough of it.
     *
     * <p>Threshold zero is honoured as "the moment the moon rises", which is why the comparison is
     * {@code >=} and the gain is applied first.
     */
    private static void accumulateExposure(ServerPlayer player, ServerLevel level, PlayerHeritageData data) {
        if (!WerewolfConfig.enableForcedTransform) {
            return;
        }
        int threshold = WerewolfConfig.exposureThreshold;
        int delta = WerewolfRules.isMoonExposed(player, level)
                ? WerewolfConfig.exposureGain
                : -WerewolfConfig.exposureDecay;
        int exposure = WerewolfState.addExposure(data, delta, threshold);
        if (exposure >= threshold) {
            WerewolfTransformService.beginForcedTransform(player, level, data);
        }
    }

    /**
     * Keeps the loss-of-control flag honest for the length of a night.
     *
     * <p>Both directions matter and both are one line here: a dose drunk mid-night hands the wolf back
     * to its player, and a dose running out mid-night takes them away again. Deriving the flag once at
     * transformation time would have made Wolfsbane a thing you had to drink before the change, which
     * is not what the potion is.
     */
    private static void refreshControl(ServerPlayer player, PlayerHeritageData data) {
        boolean feral = WerewolfConfig.enableLossOfControl && !WerewolfRules.hasWolfsbane(player);
        if (!WerewolfTransformService.setLossOfControl(player, data, feral)) {
            return;
        }
        if (feral) {
            WerewolfState.setVoluntary(data, false);
        }
        HeritageAPI.syncTransformation(player);
    }

    /**
     * Repairs a werewolf stuck in {@link TransformationState#TRANSITIONING} with nothing pending.
     *
     * <p>A save written by an older build, or a crash between the two writes, would otherwise leave a
     * player in a state that no scan clears and no constraint keys on — visibly human, permanently
     * mid-change.
     */
    private static void healStaleTransitioningState(ServerPlayer player, PlayerHeritageData data) {
        if (data.getTransformationState() != TransformationState.TRANSITIONING) {
            return;
        }
        boolean wolf = WerewolfRules.inWolfForm(data);
        data.setTransformationState(wolf
                ? TransformationState.TRANSFORMED
                : TransformationState.NORMAL);
        if (!wolf) {
            // The onset takes control before the change lands. A change that never landed must not leave
            // a human standing there unable to open their own inventory — nor a controller still driving.
            WerewolfTransformService.setLossOfControl(player, data, false);
        }
        HeritageAPI.syncTransformation(player);
    }

    /**
     * True when the sky over this level holds a full moon.
     *
     * <p>Kept as a delegate rather than deleted: {@code MooncalfEntity} asks this to decide whether to
     * dance, and a mooncalf and a werewolf must never disagree about what night it is.
     */
    public static boolean moonIsUp(ServerLevel level) {
        return WerewolfRules.fullMoonNight(level);
    }

    /** @deprecated use {@link WerewolfRules#moonPhase(long)}. */
    @Deprecated
    public static int moonPhase(long dayTime) {
        return WerewolfRules.moonPhase(dayTime);
    }

    /** @deprecated use {@link WerewolfRules#isNight(long)}. */
    @Deprecated
    public static boolean isNight(long dayTime) {
        return WerewolfRules.isNight(dayTime);
    }
}
