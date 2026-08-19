package at.koopro.wizardsandbeasts.event.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.form.FormSystemAPI;
import at.koopro.wizardsandbeasts.form.TransitionManager;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.TransformationState;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.heritage.HeritageDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The moon's claim on a werewolf.
 *
 * <p>The mod shipped ten heritages, three werewolf variants all tagged {@code moon_sensitive}, a
 * {@code werewolf_wolf} form with its own rig and animations, and a transition config for entering
 * and leaving it — and <b>no moon-phase code anywhere</b>. {@code getMoonPhase} had zero hits across
 * the whole source tree, so the signature mechanic of the mod's signature heritage had no
 * implementation and the form was reachable only through an admin command.
 *
 * <h2>The rules</h2>
 * <ul>
 *   <li><b>Full moon and night, and only then.</b> Vanilla numbers the phases with {@code 0} as the
 *       full moon, so that is the single phase that counts.</li>
 *   <li><b>Natural dimensions only.</b> There is no moon over the Nether or the End, and
 *       {@code getMoonPhase} still returns a number there. Asking whether the dimension is natural is
 *       what stops a werewolf transforming in a place with no sky.</li>
 *   <li><b>It is a compulsion, not a toggle.</b> A player who reverts by hand while the moon is up is
 *       taken again on the next scan. That is the whole point of the affliction, and it is why this
 *       runs on a tick rather than on a one-shot moonrise event.</li>
 *   <li><b>Wolfsbane does not stop it.</b> Canon is emphatic — Lupin still becomes a wolf; the potion
 *       preserves his <em>mind</em>, not his shape. So the potion is read here for what it can
 *       honestly change: an unmedicated change is violent and leaves the werewolf reeling, a medicated
 *       one does not. It never gates the transformation itself.</li>
 * </ul>
 *
 * <p>Reverting happens at dawn, or the moment the moon wanes, or on leaving for a dimension with no
 * sky — whichever comes first.
 *
 * <p>Gated on {@link Module#HERITAGE}: switching heritage off stops <em>new</em> transformations and
 * deliberately does not strip a form a player is already wearing.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class WerewolfMoonHandler {

    /** Vanilla's phase numbering puts the full moon at 0. */
    private static final int FULL_MOON = 0;

    private static final String HUMAN_FORM = "werewolf_human";
    private static final String WOLF_FORM = "werewolf_wolf";

    /**
     * Ticks between scans.
     *
     * <p>A second is far finer than the thing being watched: a Minecraft night is 9000 ticks and the
     * moon changes once a day. Scanning every tick would ask every online player's dimension and
     * heritage 20 times a second to answer a question that changes twice a night.
     */
    private static final int SCAN_INTERVAL_TICKS = 20;

    /** How long the shock of an unmedicated change lasts. */
    private static final int TRANSFORM_SHOCK_TICKS = 100;

    private WerewolfMoonHandler() {}

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
            if (data.getSelectedHeritage() != Heritage.WEREWOLF) {
                continue;
            }
            if (!(player.level() instanceof ServerLevel level)) {
                continue;
            }
            apply(player, data, level);
        }
    }

    private static void apply(ServerPlayer player, PlayerHeritageData data, ServerLevel level) {
        // Mid-transition: leave it alone rather than starting a second one on top.
        if (TransitionManager.isTransitioning(player.getUUID())) {
            return;
        }

        boolean shouldBeWolf = moonIsUp(level);
        boolean isWolf = WOLF_FORM.equals(FormSystemAPI.getPlayerFormId(player));

        if (shouldBeWolf == isWolf) {
            return;
        }

        if (shouldBeWolf) {
            turn(player, data);
        } else {
            revert(player, data);
        }
    }

    /**
     * True when the sky over this player holds a full moon.
     *
     * <p>The dimension guard is the one that matters. The day-time clock keeps ticking in the Nether
     * and the End, so without it a werewolf would transform on schedule under a ceiling of bedrock.
     * {@code DimensionType.natural()} was the obvious way to ask and no longer exists in 1.21.11, so
     * the question is put directly: a sky to see (<em>hasSkyLight</em>) and a clock that actually
     * turns (<em>not hasFixedTime</em>, which excludes the End).
     */
    public static boolean moonIsUp(ServerLevel level) {
        var dimension = level.dimensionType();
        if (!dimension.hasSkyLight() || dimension.hasFixedTime()) {
            return false;
        }
        long dayTime = level.getDayTime();
        return isNight(dayTime) && moonPhase(dayTime) == FULL_MOON;
    }

    /**
     * Vanilla's moon phase for a given world time, 0–7.
     *
     * <p>{@code Level.getMoonPhase()} is gone in 1.21.11. The phase is still the day count modulo
     * eight — the same index vanilla uses into {@code DimensionType.MOON_BRIGHTNESS_PER_PHASE}, whose
     * first entry is {@code 1.0F}. That is what makes {@link #FULL_MOON} zero rather than four.
     *
     * <p>A pure function of the clock, so the phase wheel can be tested without a world.
     */
    public static int moonPhase(long dayTime) {
        return (int) Math.floorMod(dayTime / 24000L, 8L);
    }

    /**
     * Night, using the same boundary the Obscurial rules already use for daylight
     * ({@code ObscurialTierRules.isDaytime}: day is {@code 0..12300}). Kept identical on purpose —
     * two different answers to "is it night" in one mod is a bug waiting for a bug report nobody can
     * reproduce.
     */
    public static boolean isNight(long dayTime) {
        return Math.floorMod(dayTime, 24000L) >= 12300L;
    }

    private static void turn(ServerPlayer player, PlayerHeritageData data) {
        if (!TransitionManager.startTransition(player, WOLF_FORM)) {
            return;
        }
        data.setTransformationState(TransformationState.TRANSFORMED);
        HeritageAPI.applyStats(player);
        HeritageAPI.syncTransformation(player);

        // Wolfsbane's one honest effect here. It does not stop the change — canon is explicit that it
        // never did — it stops the change from leaving the werewolf reeling. This is also the first
        // reader the wolfsbaneActive flag has ever had; it was stored and synced and consulted by
        // nothing.
        boolean medicated = PlayerAbilityHelper.isWolfsbaneActive(player);
        if (!medicated) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.NAUSEA, TRANSFORM_SHOCK_TICKS, 0, true, false, true));
        }

        player.displayClientMessage(Component.translatable(medicated
                ? "message.wizards_and_beasts.werewolf.transform_wolfsbane"
                : "message.wizards_and_beasts.werewolf.transform"), true);
    }

    private static void revert(ServerPlayer player, PlayerHeritageData data) {
        if (!TransitionManager.startTransition(player, HUMAN_FORM)) {
            return;
        }
        data.setTransformationState(TransformationState.NORMAL);
        HeritageAPI.applyStats(player);
        HeritageAPI.syncTransformation(player);

        player.displayClientMessage(
                Component.translatable("message.wizards_and_beasts.werewolf.revert"), true);
    }
}
