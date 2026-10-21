package at.koopro.wizardsandbeasts.bestiary.journal;

import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntry;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntryRegistry;
import at.koopro.wizardsandbeasts.bestiary.CreatureProfile;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.bestiary.data.EntryHistory;
import at.koopro.wizardsandbeasts.bestiary.data.PlayerBestiaryData;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Completed research, and what it is worth. Checked whenever a page could have moved — a new depth, a study act, a
 * field note — and granted exactly once per creature ({@link EntryHistory#complete()}).
 *
 * <p>The reward is not points. A naturalist who has finished studying a creature <em>is</em> different around it,
 * and every effect below lands in a system that already exists and reads the completion flag:
 * <ul>
 *   <li><b>Handling</b> — the species knows you: a wary creature lets you near without your creeping up
 *       empty-handed ({@code WildlifeRules.letsNear}), so it no longer bolts when you walk up with a lantern — though
 *       a slayer or a darkened soul is still refused;</li>
 *   <li><b>Harvest</b> — its rare material comes back in half the time ({@code BestiaryHarvestLootModifier});</li>
 *   <li><b>O.W.L.</b> — every completed study is a point in Care of Magical Creatures ({@code OWLGradeCalculator});</li>
 *   <li><b>Career</b> — a Magizoologist is paid for the day's work.</li>
 * </ul>
 * The skill web's one point for a creature known stays where it was ({@code SkillEvents.awardForKnownSpecies}); this
 * pays for what knowing it took.
 */
@NullMarked
public final class ResearchRewards {

    private ResearchRewards() {}

    /** Whether research on {@code entryId} is complete for this player now; grants it the first time. */
    public static boolean check(ServerPlayer player, Identifier entryId) {
        BestiaryEntry entry = BestiaryEntryRegistry.get(entryId);
        if (entry == null) {
            return false;
        }
        PlayerBestiaryData data = player.getData(ModAttachments.BESTIARY_DATA.get());
        EntryHistory history = data.historyOf(entryId);
        if (history.complete()) {
            return true;
        }
        ResearchProgress progress = progress(entry, data, JournalFacts.compute(entry));
        if (!progress.complete()) {
            return false;
        }
        BestiaryDataHelper.markResearchComplete(player, entryId);
        PlayerFeedback.toast(player, NoticeKind.DISCOVERY,
                Component.translatable("bestiary.wizards_and_beasts.journal.research.complete.title", entry.displayName()),
                Component.translatable("bestiary.wizards_and_beasts.journal.research.complete.body"));
        at.koopro.wizardsandbeasts.owl.career.CareerWork.record(player,
                at.koopro.wizardsandbeasts.owl.career.CareerWork.Kind.CREATURE_CARE);
        at.koopro.wizardsandbeasts.owl.career.track.CareerProgression.record(player, at.koopro.wizardsandbeasts.owl.career.track.ActivityType.RESEARCH_COMPLETED, entryId);
        return true;
    }

    /** One player's research on one entry. Shared with the client screen, which passes its synced copies. */
    public static ResearchProgress progress(BestiaryEntry entry, PlayerBestiaryData data, JournalFacts facts) {
        DiscoveryTier tier = data.tiers().getOrDefault(entry.id(), DiscoveryTier.UNKNOWN);
        List<CreatureProfile.StudyAct> study = entry.profile().map(CreatureProfile::study)
                .orElse(List.of(CreatureProfile.StudyAct.WATCH));
        return ResearchProgress.of(tier, study, data.historyOf(entry.id()).acts(), facts.ecology(),
                data.notesOn(entry.id()));
    }
}
