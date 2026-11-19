package at.koopro.wizardsandbeasts.client.journey.gui;

import at.koopro.wizardsandbeasts.chamber.ChamberStage;
import at.koopro.wizardsandbeasts.client.career.ClientCareerState;
import at.koopro.wizardsandbeasts.client.journey.ClientApprenticePathState;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.journey.PathStep;
import at.koopro.wizardsandbeasts.journey.PathText;
import at.koopro.wizardsandbeasts.owl.career.track.CareerView;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What the quest journal lists (UI renaissance P6, 2026-11-19), built from state the server already syncs: the
 * Apprentice's Path and the Chamber of Secrets after it, the career's contracts in hand and on offer, and what the
 * next rank asks for. Read-only: nothing here asks the server for anything, so the journal adds no new door.
 */
@NullMarked
public final class QuestJournal {

    private static final String KEY = "gui.wizards_and_beasts.journal.";

    public enum Section {
        PATH("path"), ERRANDS("errands"), GOALS("goals");

        private final String id;

        Section(String id) {
            this.id = id;
        }

        public Component title() {
            return Component.translatable(KEY + "section." + id);
        }
    }

    /** DONE ticked, CURRENT the quill, OPEN an empty box (later), OFFERED a coin (not taken up). */
    public enum State { DONE, CURRENT, OPEN, OFFERED }

    /**
     * One line of the journal.
     *
     * @param target 0 when the entry has no progress to show
     */
    public record Entry(Section section, Component title, Component body, State state, int progress, int target,
                        @Nullable Component reward, @Nullable Component note) {}

    private QuestJournal() {}

    public static List<Entry> entries(Section section) {
        return switch (section) {
            case PATH -> path();
            case ERRANDS -> errands();
            case GOALS -> goals();
        };
    }

    /** Entries done and in total, for a section chip. */
    public static int[] tally(List<Entry> entries) {
        int done = 0;
        for (Entry e : entries) {
            if (e.state() == State.DONE) {
                done++;
            }
        }
        return new int[]{done, entries.size()};
    }

    private static List<Entry> path() {
        List<Entry> out = new ArrayList<>();
        PathStep current = ClientApprenticePathState.current();
        for (ClientApprenticePathState.Line line : ClientApprenticePathState.lines()) {
            State state = line.done() ? State.DONE : line.step() == current ? State.CURRENT : State.OPEN;
            out.add(new Entry(Section.PATH, PathText.title(line.step()), PathText.hint(line.step()), state, 0, 0,
                    null, null));
        }
        ChamberStage next = ClientApprenticePathState.walked() ? ClientApprenticePathState.chamberNext() : null;
        if (next != null) {
            out.add(new Entry(Section.PATH, Component.translatable("chamber.wizards_and_beasts.title"),
                    Component.translatable(next.hintKey()), State.CURRENT, 0, 0, null, null));
        }
        return out;
    }

    private static List<Entry> errands() {
        CareerView view = ClientCareerState.get();
        List<Entry> out = new ArrayList<>();
        for (CareerView.ContractLine line : view.active()) {
            out.add(contract(line, line.ready() ? State.DONE : State.CURRENT));
        }
        for (CareerView.ContractLine line : view.offers()) {
            out.add(contract(line, State.OFFERED));
        }
        return out;
    }

    private static Entry contract(CareerView.ContractLine line, State state) {
        Component body = Component.empty().append(line.brief()).append("\n\n").append(line.objective());
        Component reward = Component.translatable(KEY + "reward",
                CurrencyHelper.formatFromKnuts(line.knuts()), line.xp());
        Component note = line.ready() ? Component.translatable(KEY + "ready")
                : state == State.OFFERED ? Component.translatable(KEY + "offered")
                : line.daysLeft() >= 0 ? Component.translatable(KEY + "days_left", line.daysLeft()) : null;
        return new Entry(Section.ERRANDS, line.title(), body, state, line.progress(), line.target(), reward, note);
    }

    private static List<Entry> goals() {
        CareerView view = ClientCareerState.get();
        List<Entry> out = new ArrayList<>();
        if (view.state() == CareerView.NO_CAREER) {
            return out;
        }
        for (CareerView.GoalLine goal : view.goals()) {
            boolean met = goal.target() > 0 && goal.progress() >= goal.target();
            out.add(new Entry(Section.GOALS, goal.text(),
                    Component.translatable(KEY + "goal.body", view.nextRank(), view.profession()),
                    met ? State.DONE : State.CURRENT, goal.progress(), goal.target(), null, null));
        }
        return out;
    }

    /** One line on how a section stands: steps done, errands in hand and on offer, goals met. */
    public static Component caption(Section section, List<Entry> entries) {
        int done = 0;
        int offered = 0;
        for (Entry e : entries) {
            if (e.state() == State.DONE) {
                done++;
            } else if (e.state() == State.OFFERED) {
                offered++;
            }
        }
        return switch (section) {
            case PATH -> Component.translatable(KEY + "caption.path", done, entries.size());
            case ERRANDS -> Component.translatable(KEY + "caption.errands", entries.size() - offered, offered);
            case GOALS -> Component.translatable(KEY + "caption.goals", done, entries.size());
        };
    }

    /** The empty page's title and hint for a section with nothing in it. */
    public static Component emptyTitle(Section section) {
        return Component.translatable(KEY + "empty." + section.name().toLowerCase(java.util.Locale.ROOT));
    }

    public static Component emptyHint(Section section) {
        return Component.translatable(KEY + "empty." + section.name().toLowerCase(java.util.Locale.ROOT) + ".hint");
    }

    public static Component stateLabel(State state) {
        return Component.translatable(KEY + "state." + state.name().toLowerCase(java.util.Locale.ROOT));
    }
}
