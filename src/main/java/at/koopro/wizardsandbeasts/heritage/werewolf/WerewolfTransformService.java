package at.koopro.wizardsandbeasts.heritage.werewolf;

import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.form.FormSystemAPI;
import at.koopro.wizardsandbeasts.form.constraint.FormConstraint;
import at.koopro.wizardsandbeasts.form.constraint.FormConstraints;
import at.koopro.wizardsandbeasts.form.sense.FormSenseService;
import at.koopro.wizardsandbeasts.form.TransitionManager;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.TransformationState;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jspecify.annotations.NullMarked;

/**
 * The forced change, start to finish.
 *
 * <p>Three phases, and the middle one is the point of the design:
 * <ol>
 *   <li>{@link #beginForcedTransform} — the moon has taken hold. The state goes
 *       {@link TransformationState#TRANSITIONING}, the particles and the scream start, and a completion
 *       tick is written to persistent state.</li>
 *   <li>{@link #tickPending} — the change lands once that tick arrives. Because the deadline lives in
 *       the flag map rather than in a static queue, a player who logs out mid-change comes back still
 *       mid-change instead of standing in a stalled TRANSITIONING state forever.</li>
 *   <li>{@link #revert} — dawn, a waning moon, a dimension with no sky, or a medicated werewolf's own
 *       decision.</li>
 * </ol>
 *
 * <p>The form is set with {@link FormSystemAPI#setPlayerForm} directly rather than through
 * {@link TransitionManager}. The transition manager hard-caps its duration at 30 ticks, and the whole
 * shape of a forced change is that the delay is configurable and can be longer than that; running the
 * delay here also means the werewolf's own TRANSITIONING window is the one the constraints key on.
 * {@code TransitionManager.isTransitioning} is still respected as a guard, so a change started by some
 * other system is never trampled.
 */
@NullMarked
public final class WerewolfTransformService {

    private WerewolfTransformService() {}

    /**
     * The one place the loss-of-control flag is written.
     *
     * <p>Setting the flag and releasing the controller are two halves of the same act, and keeping them
     * apart is how a werewolf ends up with a released controller that is still flagged, or — much worse
     * — a cleared flag and a {@link FeralController} still holding the last driven velocity and the
     * sprint bit. Every site that changes control goes through here: the onset, the landing, the revert,
     * Wolfsbane arriving mid-night, death, and the heritage module being switched off.
     *
     * <p>Does not sync on its own. Callers batch the sync with whatever else they changed, because a
     * transformation writes four fields and should cost one packet.
     *
     * @return true if the flag actually changed
     */
    public static boolean setLossOfControl(ServerPlayer player, PlayerHeritageData data, boolean feral) {
        if (WerewolfState.isLossOfControl(data) == feral) {
            return false;
        }
        WerewolfState.setLossOfControl(data, feral);
        if (!feral) {
            FeralController.release(player);
        }
        return true;
    }

    // ── phase 1: the moon takes hold ───────────────────────────────────

    /**
     * Starts a forced change. Safe to call every scan — a no-op unless the player is a werewolf in
     * human shape with nothing already in flight.
     *
     * @return true if a change was started
     */
    public static boolean beginForcedTransform(ServerPlayer player, ServerLevel level, PlayerHeritageData data) {
        if (!WerewolfConfig.enableForcedTransform) {
            return false;
        }
        if (WerewolfState.hasPendingTransform(data) || WerewolfRules.inWolfForm(data)) {
            return false;
        }
        if (TransitionManager.isTransitioning(player.getUUID())) {
            return false;
        }
        // Canon is emphatic that Wolfsbane does not stop the change — it preserves the mind, not the
        // shape — so this branch is off by default and exists for servers that want the kinder reading.
        if (WerewolfConfig.wolfsbaneSuppressesTransform && WerewolfRules.hasWolfsbane(player)) {
            return false;
        }

        data.setTransformationState(TransformationState.TRANSITIONING);
        WerewolfState.setTransformAtTick(data, level.getGameTime() + WerewolfConfig.transformDelayTicks);
        WerewolfState.clearExposure(data);
        // Control goes at the onset, not at the landing. The throes are the one window in which a player
        // could otherwise drink, cast or eat their way out of a change that has already started, and
        // "the transformation cannot be cancelled" has to hold from the first tick of it. The controller
        // does not drive yet -- WerewolfControlHandler skips a player with a change still pending, because
        // tickPending is holding them still.
        setLossOfControl(player, data, WerewolfConfig.enableLossOfControl);
        HeritageAPI.syncTransformation(player);

        playChangeOnset(player, level);
        PlayerFeedback.actionBar(player,
                Component.translatable("message.wizards_and_beasts.werewolf.change_begins"));
        return true;
    }

    // ── phase 2: it lands ──────────────────────────────────────────────

    /** Completes a pending change once its deadline arrives. No-op when nothing is pending. */
    public static void tickPending(ServerPlayer player, ServerLevel level, PlayerHeritageData data) {
        if (!WerewolfState.hasPendingTransform(data)) {
            return;
        }
        // Drunk in the throes. beginForcedTransform refuses to start a change while the potion is up
        // (when the server has opted into suppression at all), so a change already running must answer
        // the same way -- otherwise the potion's one job depends on beating a 50-tick window rather than
        // on being drunk. This is the "just in time" case, and it is the only thing that stops a change
        // once it has started.
        if (WerewolfConfig.wolfsbaneSuppressesTransform && WerewolfRules.hasWolfsbane(player)) {
            abortPendingTransform(player, level, data);
            return;
        }

        long due = WerewolfState.getTransformAtTick(data);
        if (level.getGameTime() < due) {
            playChangeThroes(player, level);
            return;
        }
        WerewolfState.clearPendingTransform(data);
        completeTransform(player, level, data);
    }

    /**
     * Puts the wolf on: the form, the body, the empty hands, and — unless the werewolf is medicated —
     * the loss of control itself.
     */
    public static void completeTransform(ServerPlayer player, ServerLevel level, PlayerHeritageData data) {
        FormSystemAPI.setPlayerForm(player, WerewolfRules.WOLF_FORM);
        data.setTransformationState(TransformationState.TRANSFORMED);

        if (WerewolfEquipment.stripForTransformation(player) > 0) {
            WerewolfState.setStripped(data, true);
            PlayerFeedback.actionBar(player,
                    Component.translatable("message.wizards_and_beasts.werewolf.equipment_shed"));
        }
        WerewolfAttributes.apply(player);

        boolean medicated = WerewolfRules.hasWolfsbane(player);
        boolean feral = WerewolfConfig.enableLossOfControl && !medicated;
        setLossOfControl(player, data, feral);
        WerewolfState.setVoluntary(data, false);

        HeritageAPI.applyStats(player);
        HeritageAPI.syncTransformation(player);
        // A wolf's nose and eyes arrive with the body. FormSenseService's sweep would get there within
        // 100 ticks on its own; five seconds of human senses in a wolf's head is five seconds too many,
        // and AnimagusTransformService already applies its senses on the same tick as the form.
        FormSenseService.apply(player, WerewolfRules.sensesFor(player));

        level.playSound(null, player.blockPosition(), ModSounds.WEREWOLF_HOWL.get(),
                SoundSource.PLAYERS, 1.2f, 0.85f);
        level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(),
                40, 0.6, 0.9, 0.6, 0.15);

        if (feral) {
            // The unmedicated change is violent and it costs the werewolf the night. The Nausea is the
            // shock; the toast is the only warning a player gets that their keys have stopped working.
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, true, false, true));
            PlayerFeedback.toast(player, NoticeKind.WARN,
                    Component.translatable("message.wizards_and_beasts.werewolf.feral.title"),
                    Component.translatable("message.wizards_and_beasts.werewolf.feral.body"));
        } else {
            PlayerFeedback.toast(player, NoticeKind.WARN,
                    Component.translatable("message.wizards_and_beasts.werewolf.transform_wolfsbane"),
                    Component.translatable("message.wizards_and_beasts.werewolf.wolfsbane.body"));
        }
    }

    /**
     * Stops a change that had already begun, and hands the body straight back.
     *
     * <p>Leaves the exposure counter cleared, so the moon has to soak in again before the next attempt
     * rather than the change re-firing on the very next scan.
     */
    private static void abortPendingTransform(ServerPlayer player, ServerLevel level, PlayerHeritageData data) {
        WerewolfState.clearPendingTransform(data);
        WerewolfState.clearExposure(data);
        setLossOfControl(player, data, false);
        data.setTransformationState(TransformationState.NORMAL);
        HeritageAPI.syncTransformation(player);

        level.playSound(null, player.blockPosition(), ModSounds.WEREWOLF_REVERT.get(),
                SoundSource.PLAYERS, 0.7f, 1.2f);
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, player.getX(),
                player.getY() + player.getBbHeight() * 0.6, player.getZ(), 20, 0.4, 0.5, 0.4, 0.0);
        PlayerFeedback.actionBar(player,
                Component.translatable("message.wizards_and_beasts.werewolf.wolfsbane_averted"));
    }

    // ── phase 3: giving it back ────────────────────────────────────────

    /**
     * Returns the werewolf to their human shape and their own hands.
     *
     * <p>Safe to call unconditionally: everything it undoes is idempotent, so a revert on a player who
     * is already human costs a form set and nothing else.
     *
     * @param exhausted whether to leave the after-effects of a night spent as a wolf
     */
    public static void revert(ServerPlayer player, ServerLevel level, PlayerHeritageData data, boolean exhausted) {
        boolean wasWolf = WerewolfRules.inWolfForm(data);

        WerewolfState.clearPendingTransform(data);
        setLossOfControl(player, data, false);
        WerewolfState.setVoluntary(data, false);
        WerewolfState.setStripped(data, false);
        WerewolfState.clearExposure(data);
        // Belt and braces: setLossOfControl only releases when the flag was actually set, and a revert
        // must leave nothing driving whatever the flag happened to say.
        FeralController.release(player);

        WerewolfAttributes.remove(player);
        FormSenseService.clear(player);
        FormSystemAPI.setPlayerForm(player, WerewolfRules.HUMAN_FORM);
        data.setTransformationState(TransformationState.NORMAL);
        HeritageAPI.applyStats(player);
        HeritageAPI.syncTransformation(player);

        if (!wasWolf) {
            return;
        }

        level.playSound(null, player.blockPosition(), ModSounds.WEREWOLF_REVERT.get(),
                SoundSource.PLAYERS, 0.9f, 1.0f);
        level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 1.0, player.getZ(),
                24, 0.5, 0.8, 0.5, 0.02);

        if (exhausted && WerewolfConfig.postTransformDebuffTicks > 0) {
            int ticks = WerewolfConfig.postTransformDebuffTicks;
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 0, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, ticks, 0, false, true, true));
        }
        PlayerFeedback.actionBar(player,
                Component.translatable("message.wizards_and_beasts.werewolf.revert"));
    }

    /**
     * A medicated werewolf choosing to give the shape back before the potion runs out.
     *
     * <p>Refused while feral, and that refusal is the whole loss-of-control contract in one line: an
     * unmedicated wolf cannot cancel the transformation, by any route, including this one.
     *
     * @return true if the player is now human
     */
    public static boolean requestVoluntaryRevert(ServerPlayer player) {
        PlayerHeritageData data = WerewolfState.data(player);
        if (!WerewolfRules.isWerewolf(data) || !WerewolfRules.inWolfForm(data)) {
            PlayerFeedback.refuse(player,
                    Component.translatable("message.wizards_and_beasts.werewolf.revert_denied.title"),
                    Component.translatable("message.wizards_and_beasts.werewolf.revert_denied.not_wolf"));
            return false;
        }
        // Asked of the shared constraint layer, not of the flag directly, so NO_VOLUNTARY_EXIT is a real
        // enforced rule rather than a documented one — and so any future forced form gets this refusal
        // without editing this method.
        if (FormConstraints.denies(player, FormConstraint.NO_VOLUNTARY_EXIT)) {
            PlayerFeedback.refuse(player,
                    Component.translatable("message.wizards_and_beasts.werewolf.revert_denied.title"),
                    Component.translatable("message.wizards_and_beasts.werewolf.revert_denied.feral"));
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        revert(player, level, data, true);
        return true;
    }

    /**
     * Marks a medicated werewolf as staying wolf past the moon's claim on them, so the dawn scan leaves
     * them alone. Cleared by the revert, by the potion expiring, and by death.
     */
    public static void markVoluntaryStay(ServerPlayer player, PlayerHeritageData data) {
        if (WerewolfState.isVoluntary(data)) {
            return;
        }
        WerewolfState.setVoluntary(data, true);
        HeritageAPI.syncTransformation(player);
        PlayerFeedback.actionBar(player,
                Component.translatable("message.wizards_and_beasts.werewolf.voluntary_stay"));
    }

    /**
     * Hard reset with no ceremony — death, and any other state wipe where an animation would be absurd.
     * Deliberately does not apply the exhaustion debuffs: the player is already paying.
     *
     * <p>Resets the <em>form</em> as well as the flags, and that is not optional. The heritage
     * attachment is {@code copyOnDeath} and {@code FormLifecycleHandler.onRespawn} re-applies whatever
     * form it finds, so clearing only the flags left a player respawning still shaped like a wolf with
     * {@code TransformationState.NORMAL} underneath — a mismatch {@code restoreWolfBody} then refused to
     * put attributes back on, because it asks {@code isTransformed} and that reads the state. The same
     * gap {@code AnimagusEvents.onDeath} had.
     *
     * <p>A werewolf who dies during their own full moon is simply taken again: they respawn human, the
     * moon is still up, and the exposure counter starts filling from zero.
     */
    public static void forceReset(ServerPlayer player, PlayerHeritageData data) {
        boolean wasWolf = WerewolfRules.inWolfForm(data);
        WerewolfState.clearAll(data);
        FeralController.release(player);
        WerewolfAttributes.remove(player);
        FormSenseService.clear(player);
        data.setTransformationState(TransformationState.NORMAL);
        if (wasWolf) {
            FormSystemAPI.setPlayerForm(player, WerewolfRules.HUMAN_FORM);
        }
    }

    // ── presentation ───────────────────────────────────────────────────

    private static void playChangeOnset(ServerPlayer player, ServerLevel level) {
        level.playSound(null, player.blockPosition(), ModSounds.WEREWOLF_TRANSFORM.get(),
                SoundSource.PLAYERS, 1.0f, 0.8f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(),
                30, 0.4, 0.8, 0.4, 0.02);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY() + 1.0, player.getZ(),
                12, 0.4, 0.6, 0.4, 0.05);
    }

    /** The seconds between onset and completion: bones still moving. Cheap enough to run every tick. */
    private static void playChangeThroes(ServerPlayer player, ServerLevel level) {
        if (level.getGameTime() % 4L != 0L) {
            return;
        }
        level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(),
                4, 0.35, 0.6, 0.35, 0.08);
        // Frozen in place while the change runs — the same freeze TransitionManager applies, for the
        // same reason: a body being remade is not a body that walks.
        player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
        player.hurtMarked = true;
    }
}
