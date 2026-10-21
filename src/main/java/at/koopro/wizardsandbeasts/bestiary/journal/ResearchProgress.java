package at.koopro.wizardsandbeasts.bestiary.journal;

import at.koopro.wizardsandbeasts.bestiary.CreatureProfile;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyCapability;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * How far a player's research into one creature has got, and whether it is complete. Pure.
 *
 * <p>Research is the whole page, not a number: knowing the creature (tier {@code KNOWN}), having done with it every
 * hands-on thing its profile says a naturalist does (fed it, handled it, taken what it shed, earned its trust), and
 * having watched it show at least half of the behaviours its ecology can show. Each step is something a player can
 * see on the page and go and do; none is "kill more".
 *
 * @param steps    every step, done or not, in the order the page lists them
 * @param notesSeen behaviours of this creature the player has noted
 * @param notesNeeded how many noted behaviours complete the research
 */
@NullMarked
public record ResearchProgress(List<Step> steps, int notesSeen, int notesNeeded) {

    /** One line of the research checklist. {@code key} is a lang key; {@code arg} its argument, or empty. */
    public record Step(String key, String arg, boolean done) {}

    public static final String KEY = "bestiary.wizards_and_beasts.journal.research.";

    /**
     * @param tier     the player's depth of knowledge
     * @param study    the profile's study acts
     * @param acts     the study acts this player has performed on this creature (serialized names)
     * @param ecology  the creature's ecology capabilities (serialized names)
     * @param notes    the player's field notes on it
     */
    public static ResearchProgress of(DiscoveryTier tier, Collection<CreatureProfile.StudyAct> study, Set<String> acts,
                                      Collection<String> ecology, Set<String> notes) {
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(KEY + "seen", "", tier.atLeast(DiscoveryTier.ENCOUNTERED)));
        steps.add(new Step(KEY + "watched", "", tier.atLeast(DiscoveryTier.OBSERVED)));
        steps.add(new Step(KEY + "studied", "", tier.atLeast(DiscoveryTier.STUDIED)));
        steps.add(new Step(KEY + "known", "", tier.atLeast(DiscoveryTier.KNOWN)));
        for (CreatureProfile.StudyAct act : study) {
            if (act != CreatureProfile.StudyAct.WATCH) {
                steps.add(new Step(KEY + "act", act.getSerializedName(), acts.contains(act.getSerializedName())));
            }
        }
        List<String> witnessable = witnessable(ecology);
        int seen = (int) witnessable.stream().filter(notes::contains).count();
        int needed = (witnessable.size() + 1) / 2;
        if (needed > 0) {
            steps.add(new Step(KEY + "behaviours", seen + "/" + needed, seen >= needed));
        }
        return new ResearchProgress(List.copyOf(steps), seen, needed);
    }

    /** The behaviours a player can actually watch this creature show: its driven ecology. */
    public static List<String> witnessable(Collection<String> ecology) {
        List<String> out = new ArrayList<>();
        for (String name : ecology) {
            EcologyCapability capability = EcologyCapability.fromNote(name);
            if (capability != null && capability.kind() == EcologyCapability.Kind.DRIVEN) {
                out.add(name);
            }
        }
        return out;
    }

    public boolean complete() {
        return steps.stream().allMatch(Step::done);
    }

    public int done() {
        return (int) steps.stream().filter(Step::done).count();
    }

    public int total() {
        return steps.size();
    }
}
