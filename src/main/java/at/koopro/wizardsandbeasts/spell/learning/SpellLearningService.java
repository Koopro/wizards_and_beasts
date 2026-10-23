package at.koopro.wizardsandbeasts.spell.learning;

import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.network.spell.SpellDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.stats.PlayerStatsSyncPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.effect.SpellEffectComponent;
import at.koopro.wizardsandbeasts.spell.effect.SpellEffectEntry;
import at.koopro.wizardsandbeasts.spell.effect.SpellEffectRunner;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
 * price. Learning starts from one of two {@link Route}s:
 *
 * <ul>
 *   <li>reading a <em>spell source</em> — {@link SpellSource}, the book/page/notes path;</li>
 *   <li>allocating a skill node carrying a {@code learn_spell} effect, which owes the player a
 *       lesson.</li>
 * </ul>
 *
 * <p>Neither route writes the spell itself any more. Both open a tracing challenge
 * ({@code SpellTraceService}), and only a judged, passed trace reaches {@link #commit}. That is the
 * whole of the "no normal route bypasses the trace" rule: there is one gameplay caller of
 * {@link #commit}, and an architecture test pins it.
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

    /** Where a lesson came from. Decides the one side effect that differs between them. */
    public enum Route {
        /** A book, page or notes. Reading dark writing stains the reader ({@link SpellLawLearningGate}). */
        BOOK,
        /**
         * A skill-web node. The node already charged its own {@code dark_study}, so no second stain;
         * instead the spell goes in the web ledger, which is what lets a respec take it back.
         */
        WEB_LESSON
    }

    /**
     * The charms every witch or wizard who can hold a wand knows before they find a single book: Lumos (and Nox with
     * it). User decision (2026-10-23): the first spell was a lucky find -- six of the nine village books need another
     * spell first -- and the cheapest web lesson cost all three starting points. Now the first light is free and the
     * three points stay for the web.
     */
    public static final List<String> STARTER_SPELLS = List.of("wizards_and_beasts:lumos");

    /**
     * Teaches the {@link #STARTER_SPELLS} (and their companions) to a player whose heritage can use a wand and cast,
     * if they do not know them yet. Not a lesson: nothing is traced, and nothing is written to the web ledger, so a
     * respec never takes the starter charm away. Runs when a heritage is chosen and at every login (so saves from
     * before this rule catch up).
     *
     * @return the spells newly taught, companions included
     */
    public static List<String> teachStarter(ServerPlayer player) {
        PlayerHeritageData heritage = player.getData(ModAttachments.HERITAGE_DATA.get());
        if (!heritage.canUseWand() || !heritage.canCast()) {
            return List.of();
        }
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        List<String> taught = new ArrayList<>();
        for (String id : STARTER_SPELLS) {
            Spell spell = Spells.byId(id);
            if (spell == null || data.knowsSpell(spell.getId())) {
                continue;
            }
            data.learnSpell(spell.getId());
            taught.add(spell.getId());
            for (String companionId : companionsOf(spell)) {
                Spell companion = Spells.byId(companionId);
                if (companion != null && !data.knowsSpell(companion.getId())) {
                    data.learnSpell(companion.getId());
                    taught.add(companion.getId());
                }
            }
        }
        if (!taught.isEmpty()) {
            SpellDataSyncS2CPayload.syncToPlayer(player);
            PlayerStatsSyncPayload.syncToPlayer(player);
        }
        return taught;
    }

    /** Book-route {@link #commit}, by id. Kept for the gate tests, which exercise the gates directly. */
    public static LearnResult tryLearnSpell(ServerPlayer player, String spellId) {
        return commit(player, Spells.byId(spellId), Route.BOOK);
    }

    /**
     * Teaches a spell if every gate allows it, then syncs. Called once a trace has passed; every gate is
     * re-run here because the player has had the whole challenge to lose a profession, a heritage or a
     * module, or to learn the spell some other way.
     *
     * <p>The spell's <em>companions</em> come with it — the counter-charm its own effects switch to
     * (Lumos teaches Nox). One trace, one lesson: Nox is the second half of Lumos, not a second spell to
     * go and find.
     *
     * <p>The stats sync is not incidental: KNOWLEDGE derives partly from how many spells are known,
     * so a client that learned a spell without it would show a stale figure until the next full sync.
     */
    public static LearnResult commit(ServerPlayer player, @Nullable Spell spell, Route route) {
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        LearnResult validation = validateLearnAttempt(player, spell, route);
        if (!validation.success()) {
            return validation;
        }

        data.learnSpell(spell.getId());
        for (String companionId : companionsOf(spell)) {
            Spell companion = Spells.byId(companionId);
            if (companion != null && validateLearnAttempt(player, companion, route).success()) {
                data.learnSpell(companion.getId());
            }
        }
        if (route == Route.BOOK) {
            // Here rather than in validateLearnAttempt, which also answers the preview SpellSourceItem
            // runs on the first right-click: a player who picks a book up, reads the refusal and puts it
            // down again has not studied anything and must not be charged for it.
            SpellLawLearningGate.stainForLearning(player, spell);
            awardFirstLessons(player);
        } else {
            player.getData(ModAttachments.SKILL_DATA.get()).recordWebTaughtSpell(spell.getId());
        }
        SpellDataSyncS2CPayload.syncToPlayer(player);
        PlayerStatsSyncPayload.syncToPlayer(player); // KNOWLEDGE derives from spells learned
        return LearnResult.success(spell.getDisplayName());
    }

    /** "First Lessons": a spell read out of a book. Declared {@code minecraft:impossible}, awarded here only. */
    private static void awardFirstLessons(ServerPlayer player) {
        net.minecraft.server.MinecraftServer server = player.level().getServer();
        net.minecraft.advancements.AdvancementHolder holder = server == null ? null : server.getAdvancements().get(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("wizards_and_beasts", "progression/spell_teacher"));
        if (holder != null) {
            player.getAdvancements().award(holder, "read");
        }
    }

    /**
     * Teaches a companion at cast time — only for a save that knew the parent before tracing existed and
     * so never had the companion taught alongside it. Reachable only by casting the parent, which the
     * caster already knows, so it is the tail of a lesson already traced rather than a new one.
     */
    public static void teachCompanionOfKnownSpell(ServerPlayer player, Spell companion) {
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        if (data.knowsSpell(companion.getId())) {
            return;
        }
        for (String knownId : data.getKnownSpells()) {
            if (companionsOf(Spells.byId(knownId)).contains(companion.getId())) {
                data.learnSpell(companion.getId());
                return;
            }
        }
    }

    /**
     * The spells a spell teaches alongside itself: the targets of its {@code swap_active_spell} effects
     * marked {@code learn}. Read from data, so a pack can pair its own spells the same way.
     */
    public static List<String> companionsOf(@Nullable Spell spell) {
        List<String> out = new ArrayList<>();
        for (SpellEffectEntry entry : SpellEffectRunner.effectsOf(spell)) {
            if (entry.component() instanceof SpellEffectComponent.SwapActiveSpell swap && swap.learn()) {
                Spell target = Spells.byId(swap.spell());
                out.add(target != null ? target.getId() : swap.spell());
            }
        }
        return out;
    }

    /**
     * Whether this player could learn this spell by {@code route} right now. A web lesson's node stands in
     * for the spell's own progression gates ({@link SpellLearningEligibility#evaluate(ServerPlayer, Spell,
     * PlayerSpellData, PlayerHeritageData, boolean)}).
     */
    public static LearnResult validateLearnAttempt(ServerPlayer player, @Nullable Spell spell, Route route) {
        if (spell == null) {
            return LearnResult.failure("Unknown spell.");
        }
        SpellLearningEligibility.Result eligibility = SpellLearningEligibility.evaluate(player, spell,
                player.getData(ModAttachments.SPELL_DATA.get()), player.getData(ModAttachments.HERITAGE_DATA.get()),
                route == Route.WEB_LESSON);
        return eligibility.learnable() ? LearnResult.success("ok") : LearnResult.failure(eligibility.reason());
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
