package at.koopro.wizardsandbeasts.spell.learning;

import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.network.spell.SpellDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.stats.PlayerStatsSyncPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * The one place a spell becomes known.
 *
 * <h2>What this used to be</h2>
 * A till. {@code buildOffers} listed every spell the player was eligible for, priced each one in
 * Knuts, and {@code tryLearnSpell} took the money before writing the spell into
 * {@code PlayerSpellData}. That is a generic RPG trainer, and it undercut everything the mod already
 * had: skill-web keystones that grant spells as earned progression, proficiency as the mastery
 * curve, and heritage, profession and mastery-tier gates that were all reduced to shelf labels on a
 * catalogue.
 *
 * <h2>What it is now</h2>
 * Eligibility and the write are unchanged and still live here — every gate in
 * {@link SpellLearningEligibility} applies to every caller. What is gone is the trigger and the
 * price. Learning is triggered by:
 *
 * <ul>
 *   <li>reading a <em>spell source</em> — {@link SpellSource}, the book/page/notes path;</li>
 *   <li>allocating a skill node carrying a {@code learn_spell} effect, via
 *       {@code SkillSystemAPI.teachSpell}.</li>
 * </ul>
 *
 * <p>Neither charges. Money buys objects — a wand, a broom, a book someone else wrote — and not the
 * contents of your own head.
 *
 * <p>{@code /wandb spell learn} deliberately does <em>not</em> come through here. It writes to
 * {@code PlayerSpellData} directly and is meant to: an operator granting a spell is overriding the
 * gates, and a debug command that could be refused by the rules it exists to step around would be
 * useless for setting up the exact state a test needs.
 */
public final class SpellLearningService {

    private SpellLearningService() {
    }

    /**
     * Teaches a spell if every gate allows it, then syncs.
     *
     * <p>The stats sync is not incidental: KNOWLEDGE derives partly from how many spells are known,
     * so a client that learned a spell without it would show a stale figure until the next full sync.
     */
    public static LearnResult tryLearnSpell(ServerPlayer player, String spellId) {
        Spell spell = Spells.byId(spellId);
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        PlayerHeritageData type = player.getData(ModAttachments.HERITAGE_DATA.get());
        LearnResult validation = validateLearnAttempt(player, spell, data, type);
        if (!validation.success()) {
            return validation;
        }

        data.learnSpell(spell.getId());
        // Here rather than in validateLearnAttempt, which also answers the preview SpellSourceItem
        // runs on the first right-click: a player who picks a book up, reads the refusal and puts it
        // down again has not studied anything and must not be charged for it.
        SpellLawLearningGate.stainForLearning(player, spell);
        SpellDataSyncS2CPayload.syncToPlayer(player);
        PlayerStatsSyncPayload.syncToPlayer(player); // KNOWLEDGE derives from spells learned
        return LearnResult.success(spell.getDisplayName());
    }

    /** Whether this player could learn this spell right now, without learning it. */
    public static LearnResult validateLearnAttempt(ServerPlayer player, @Nullable Spell spell) {
        return validateLearnAttempt(
                player,
                spell,
                player.getData(ModAttachments.SPELL_DATA.get()),
                player.getData(ModAttachments.HERITAGE_DATA.get()));
    }

    public static LearnResult validateLearnAttempt(@Nullable Spell spell, PlayerSpellData data) {
        return validateLearnAttempt(null, spell, data, null);
    }

    public static LearnResult validateLearnAttempt(@Nullable Spell spell, PlayerSpellData data, @Nullable PlayerHeritageData type) {
        return validateLearnAttempt(null, spell, data, type);
    }

    public static LearnResult validateLearnAttempt(@Nullable ServerPlayer player, @Nullable Spell spell,
                                                   PlayerSpellData data, @Nullable PlayerHeritageData type) {
        if (spell == null) {
            return LearnResult.failure("Unknown spell.");
        }
        SpellLearningEligibility.Result eligibility = SpellLearningEligibility.evaluate(player, spell, data, type);
        if (!eligibility.learnable()) {
            return LearnResult.failure(eligibility.reason());
        }
        return LearnResult.success("ok");
    }

    public record LearnResult(boolean success, String message) {
        public static LearnResult success(String message) {
            return new LearnResult(true, message);
        }

        public static LearnResult failure(String message) {
            return new LearnResult(false, message);
        }
    }
}
