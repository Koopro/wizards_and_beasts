package at.koopro.wizardsandbeasts.apparition.licence;

import at.koopro.wizardsandbeasts.ability.AbilityIds;
import at.koopro.wizardsandbeasts.ability.AbilityProficiency;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.apparition.ApparitionServerLogic;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The Ministry Apparition Test: the third step of learning to Apparate, and the only way a player can
 * come to hold a licence.
 *
 * <p>Before this existed {@code apparitionLicensed} was set in exactly two places, both of them admin
 * commands. Nothing in survival could grant it — while the flag was fully live in play, because
 * {@code SplinchResolver} taxes an unlicensed wizard's miss and {@code TraceService} files the jump as
 * {@code UNLICENSED_APPARITION}. A mechanic that costs the player something they cannot obtain is a
 * mechanic that only ever punishes.
 *
 * <p>The three steps mirror Twycross's three D's rather than inventing a new economy:
 *
 * <ol>
 *   <li><b>Training</b> — the {@code apparition_training} skill node, which is what lets a wizard
 *       Apparate at all. Unchanged by this class.</li>
 *   <li><b>Practice</b> — {@code ApparitionServerLogic} already grows Apparition proficiency on every
 *       clean arrival and gives nothing for a bad one, so the practice ledger was already being kept.
 *       This reads it; it does not add a second counter.</li>
 *   <li><b>The test</b> — below. Passing is not a die roll or a minigame: it is whether the wizard has
 *       actually been arriving in one piece, which is the thing an examiner would be judging.</li>
 * </ol>
 *
 * <p><b>Deliberately not gated on {@link Module#MINISTRY}.</b> That module ships {@code DISABLED}, and
 * the licence's mechanical bite — the miss multiplier in {@code SplinchResolver} — carries no Ministry
 * check at all. Hanging the only way to earn a licence off a module that is off by default would have
 * left the tax in place and the remedy out of reach, which is the bug this class exists to close.
 * Ministry, when enabled, is what <em>cares</em> that you hold one.
 */
@NullMarked
public final class ApparitionLicence {

    /**
     * Proficiency a wizard must reach before an examiner will pass them, on the same 0–1 scale the rest
     * of the Apparition system uses.
     *
     * <p>0.25 against {@code ApparitionPresentationBroadcaster.MUFFLED_THRESHOLD}'s 0.75: a clean jump
     * grants at most 0.01 and only past fifteen blocks, so this is roughly twenty-five real journeys
     * arrived at whole. An apprenticeship, not an endgame — the licence is the start of Apparating in
     * earnest, not a reward for having finished.
     */
    public static final float REQUIRED_PROFICIENCY = 0.25f;

    private ApparitionLicence() {}

    /** Whether the player may sit the test, and if not, what to tell them. */
    public record Eligibility(boolean eligible, Component reason) {

        static Eligibility yes() {
            return new Eligibility(true, Component.translatable("apparition.wizards_and_beasts.test.ready"));
        }

        static Eligibility no(String key, @Nullable Object... args) {
            return new Eligibility(false, Component.translatable(key, args));
        }
    }

    /** How far along the practice requirement this player is, clamped to 0–1 for display. */
    public static float progress(ServerPlayer player) {
        float held = AbilityProficiency.get(player, AbilityIds.APPARITION);
        return Math.max(0.0f, Math.min(1.0f, held / REQUIRED_PROFICIENCY));
    }

    /**
     * The gate stack, in the order an examiner would apply it. Read-only — {@link #takeTest} re-runs it
     * rather than trusting a cached answer, so this is safe to call from display code.
     */
    public static Eligibility evaluate(ServerPlayer player) {
        if (!ModuleManager.isEnabled(Module.PLAYER_ABILITIES)) {
            return Eligibility.no("apparition.wizards_and_beasts.test.deny.module");
        }
        if (PlayerAbilityHelper.isApparitionLicensed(player)) {
            return Eligibility.no("apparition.wizards_and_beasts.test.deny.already_licensed");
        }
        // Elf-magic Apparates without a test, a licence or regard for wizard wards, so there is nothing
        // here for it to certify. Checked before training: an elf has no wizard training to lack.
        if (ApparitionServerLogic.isElfMagic(player)) {
            return Eligibility.no("apparition.wizards_and_beasts.test.deny.elf_magic");
        }
        if (!ApparitionServerLogic.hasTraining(player)) {
            return Eligibility.no("apparition.wizards_and_beasts.test.deny.untrained");
        }
        if (ApparitionServerLogic.isSplinched(player)) {
            return Eligibility.no("apparition.wizards_and_beasts.test.deny.splinched");
        }
        float held = AbilityProficiency.get(player, AbilityIds.APPARITION);
        if (held < REQUIRED_PROFICIENCY) {
            return Eligibility.no("apparition.wizards_and_beasts.test.deny.practice",
                    Math.round(progress(player) * 100.0f));
        }
        return Eligibility.yes();
    }

    /**
     * Sits the test. Grants the licence on a pass and changes nothing on a fail, so a player may come
     * back and try again once they have practised more.
     *
     * @return the same {@link Eligibility} the attempt was judged against; {@code eligible} is whether
     *         the licence was granted
     */
    public static Eligibility takeTest(ServerPlayer player) {
        Eligibility verdict = evaluate(player);
        if (!verdict.eligible()) {
            return verdict;
        }
        PlayerAbilityHelper.setApparitionLicensed(player, true);
        return verdict;
    }
}
