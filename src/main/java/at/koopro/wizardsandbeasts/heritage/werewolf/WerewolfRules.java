package at.koopro.wizardsandbeasts.heritage.werewolf;

import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.TransformationState;
import at.koopro.wizardsandbeasts.form.constraint.FormConstraintSet;
import at.koopro.wizardsandbeasts.form.sense.FormSense;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Every question the lycanthropy layer asks, answered in one place and without side effects.
 *
 * <p>The moon arithmetic is a pure function of the world clock so the phase wheel can be tested
 * without a world, which is why {@link #moonPhase(long)} and {@link #isNight(long)} take a
 * {@code dayTime} rather than a level.
 */
@NullMarked
public final class WerewolfRules {

    /** Vanilla's phase numbering puts the full moon at 0. */
    public static final int FULL_MOON = 0;

    public static final String HUMAN_FORM = "werewolf_human";
    public static final String WOLF_FORM = "werewolf_wolf";

    private WerewolfRules() {}

    // ── the moon ───────────────────────────────────────────────────────

    /**
     * True when the sky over this level holds a full moon and it is night.
     *
     * <p>The dimension guard is the one that matters. The day-time clock keeps ticking in the Nether
     * and the End, so without it a werewolf would transform on schedule under a ceiling of bedrock.
     * {@code DimensionType.natural()} was the obvious way to ask and no longer exists in 1.21.11, so
     * the question is put directly: a sky to see (<em>hasSkyLight</em>) and a clock that actually
     * turns (<em>not hasFixedTime</em>, which excludes the End).
     */
    public static boolean fullMoonNight(ServerLevel level) {
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

    /**
     * True when the moon can actually see the player: open sky above their feet.
     *
     * <p>This is what makes a cellar a defence. It is checked at the player's block rather than their
     * eyes so that standing in a doorway does not flicker between exposed and sheltered every tick.
     */
    public static boolean isMoonExposed(ServerPlayer player, ServerLevel level) {
        return level.canSeeSky(player.blockPosition());
    }

    // ── who is a werewolf, and what shape are they in ──────────────────

    public static boolean isWerewolf(@Nullable PlayerHeritageData data) {
        return data != null && data.getSelectedHeritage() == Heritage.WEREWOLF;
    }

    public static boolean isWerewolf(ServerPlayer player) {
        return isWerewolf(player.getData(ModAttachments.HERITAGE_DATA.get()));
    }

    /** True for any living entity this mod counts as pack — currently only werewolf-heritage players. */
    public static boolean isPackMember(LivingEntity entity) {
        return entity instanceof ServerPlayer player && isWerewolf(player);
    }

    public static boolean isWolfForm(@Nullable String formId) {
        return WOLF_FORM.equals(formId);
    }

    public static boolean inWolfForm(PlayerHeritageData data) {
        return isWolfForm(data.getActiveFormId());
    }

    public static boolean isTransformed(PlayerHeritageData data) {
        return data.getTransformationState() == TransformationState.TRANSFORMED && inWolfForm(data);
    }

    // ── Wolfsbane ──────────────────────────────────────────────────────

    /**
     * True when the player's mind is their own through the change.
     *
     * <p>Two sources, both honoured. The {@link ModEffects#WOLFSBANE} effect is the one a brewed dose
     * grants and the one that expires; {@code PlayerAbilityHelper.isWolfsbaneActive} is the older
     * boolean on {@code PlayerAbilityData}, which predates the effect and is still what an admin
     * command or an addon would set. Either counts, so neither path silently stops working.
     */
    public static boolean hasWolfsbane(Player player) {
        return player.hasEffect(ModEffects.WOLFSBANE) || PlayerAbilityHelper.isWolfsbaneActive(player);
    }

    // ── the loss-of-control switch ─────────────────────────────────────

    /**
     * True while the mod is driving this player's body.
     *
     * <p>The stored flag is the single authority — not "is it a full moon and are they a wolf". Every
     * constraint in {@code WerewolfControlHandler} and every tick of {@link FeralController} asks this
     * and nothing else, so there is exactly one condition to satisfy when the state is entered and
     * exactly one to clear when it is left. Deriving it at each call site is how a werewolf ends up
     * unable to open their inventory an hour after dawn.
     */
    public static boolean isFeral(ServerPlayer player) {
        return WerewolfState.isLossOfControl(player.getData(ModAttachments.HERITAGE_DATA.get()));
    }

    // ── the shared form layer ──────────────────────────────────────────

    /**
     * The werewolf contribution to {@link at.koopro.wizardsandbeasts.form.constraint.FormConstraints}.
     *
     * <p>Two tiers, and the difference between them is the whole of what Wolfsbane buys:
     * <ul>
     *   <li><b>Feral</b> — {@link FormConstraintSet#FERAL}: a beast's hands <em>and</em> no way out. The
     *       loss-of-control flag is the switch, so it also covers the transformation's own onset window,
     *       when the change has started and cannot be talked out of.</li>
     *   <li><b>Medicated, in wolf shape</b> — {@link FormConstraintSet#BEAST_HANDS}: the mind is the
     *       player's again and they may change back whenever they like, but the paws are still paws.
     *       A wolf cannot hold a wand however clear-headed it is, which is the same rule an Animagus
     *       lives under.</li>
     * </ul>
     */
    public static FormConstraintSet constraintsFor(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        if (!isWerewolf(data)) {
            return FormConstraintSet.NONE;
        }
        if (WerewolfState.isLossOfControl(data)) {
            return FormConstraintSet.FERAL;
        }
        return inWolfForm(data) ? FormConstraintSet.BEAST_HANDS : FormConstraintSet.NONE;
    }

    /**
     * The werewolf contribution to {@link at.koopro.wizardsandbeasts.form.sense.FormSenses}.
     *
     * <p>A wolf's nose and a wolf's eyes, granted by the <em>shape</em> and not by the state of mind —
     * so a medicated werewolf keeps both. Werewolves have no {@code AnimagusFormDefinition} and never
     * will, which is why {@link at.koopro.wizardsandbeasts.form.sense.FormSense} is its own small enum
     * rather than the datapack capability vocabulary.
     */
    public static Set<FormSense> sensesFor(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        if (!isWerewolf(data) || !inWolfForm(data)) {
            return Set.of();
        }
        return EnumSet.of(FormSense.NIGHT_EYES, FormSense.SCENT_TRACK);
    }
}
