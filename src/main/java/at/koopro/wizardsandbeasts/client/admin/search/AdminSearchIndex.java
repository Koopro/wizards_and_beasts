package at.koopro.wizardsandbeasts.client.admin.search;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The Control Center's global search: one flat list of everything an administrator can open — sections, settings,
 * spells, creatures and their variants, brews and their effects, heritages, wand woods and cores, brooms, beam looks
 * and presets, modules, profiles, players — matched by every word typed.
 *
 * <p>Pure: entries are built elsewhere ({@link AdminSearchSources}) and handed in with a signature of the sources
 * they came from. The list is rebuilt only when that signature changes, and a query is a single pass over a few
 * thousand prepared strings, run when the text changes — never per frame.
 */
@NullMarked
public final class AdminSearchIndex {

    /** What an entry is, in the order results of equal relevance are listed. */
    public enum Kind {
        SECTION, CREATURE, VARIANT, SPELL, BREW, HERITAGE, WAND_PART, BROOM, BEAM, BEAM_PRESET, MODULE, SETTING,
        PROFILE, PLAYER;

        public String labelKey() {
            return "admin.wizards_and_beasts.search.kind." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Where choosing an entry leads. The screen turns each into a navigation. */
    public sealed interface Target {
        record Section(AdminCategory section) implements Target {}

        record Setting(Identifier id, AdminCategory category) implements Target {}

        record Spell(String id) implements Target {}

        record Creature(String id) implements Target {}

        record Brew(String id) implements Target {}

        record Heritage(String id) implements Target {}

        record WandPart(boolean core, String id) implements Target {}

        record Broom(String id) implements Target {}

        record Beam(String spell) implements Target {}

        record Module(String id) implements Target {}

        record Profile(String id) implements Target {}

        record Player(String uuid) implements Target {}
    }

    /**
     * One searchable thing.
     *
     * @param title    what the result list shows first, already translated
     * @param detail   one line of context (the section, the creature a variant belongs to)
     * @param titleKey {@link #normalise normalised} title, matched with the highest weight
     * @param haystack normalised title, detail and keywords together; every query word must occur in it
     */
    public record Entry(Kind kind, String title, String detail, String titleKey, String haystack, Target target) {

        public static Entry of(Kind kind, String title, String detail, Target target, String... keywords) {
            StringBuilder all = new StringBuilder(title).append(' ').append(detail);
            for (String keyword : keywords) {
                all.append(' ').append(keyword);
            }
            return new Entry(kind, title, detail, normalise(title), normalise(all.toString()), target);
        }
    }

    /** A query's answer: the best {@code limit} entries, and how many matched in all. */
    public record Result(List<Entry> entries, int total) {}

    private List<Entry> entries = List.of();
    private long signature = Long.MIN_VALUE;

    /** Whether entries built from sources with {@code sourceSignature} would differ from what is held. */
    public boolean stale(long sourceSignature) {
        return sourceSignature != signature;
    }

    public void rebuild(long sourceSignature, List<Entry> fresh) {
        entries = List.copyOf(fresh);
        signature = sourceSignature;
    }

    public int size() {
        return entries.size();
    }

    public Result query(String text, int limit) {
        return query(entries, text, limit);
    }

    /** Ranks {@code entries} against {@code text}: every word must match; title matches beat detail matches. */
    public static Result query(List<Entry> entries, String text, int limit) {
        String phrase = normalise(text);
        if (phrase.isEmpty()) {
            return new Result(List.of(), 0);
        }
        String[] words = phrase.split(" ");
        List<Scored> hits = new ArrayList<>();
        for (Entry entry : entries) {
            int score = score(entry, phrase, words);
            if (score > 0) {
                hits.add(new Scored(entry, score));
            }
        }
        hits.sort(Comparator.comparingInt(Scored::score).reversed()
                .thenComparing(scored -> scored.entry().kind())
                .thenComparingInt(scored -> scored.entry().title().length())
                .thenComparing(scored -> scored.entry().title()));
        List<Entry> best = new ArrayList<>(Math.min(limit, hits.size()));
        for (int i = 0; i < hits.size() && i < limit; i++) {
            best.add(hits.get(i).entry());
        }
        return new Result(best, hits.size());
    }

    private record Scored(Entry entry, int score) {}

    /** 0 for no match; otherwise higher is better. */
    static int score(Entry entry, String phrase, String[] words) {
        for (String word : words) {
            if (!entry.haystack().contains(word)) {
                return 0;
            }
        }
        String title = entry.titleKey();
        if (title.equals(phrase)) {
            return 1000;
        }
        if (title.startsWith(phrase)) {
            return 600;
        }
        if (title.contains(" " + phrase)) {
            return 400;
        }
        if (title.contains(phrase)) {
            return 250;
        }
        int inTitle = 0;
        for (String word : words) {
            if (title.contains(word)) {
                inTitle++;
            }
        }
        return 50 + 40 * inTitle;
    }

    /** Lower case, ids' separators as spaces, runs of space collapsed: "creature/phoenix_red" → "creature phoenix red". */
    public static String normalise(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean space = true;
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            if (Character.isLetterOrDigit(c) || c == '.' || c == '-') {
                out.append(c);
                space = false;
            } else if (!space) {
                out.append(' ');
                space = true;
            }
        }
        int end = out.length();
        if (end > 0 && out.charAt(end - 1) == ' ') {
            out.setLength(end - 1);
        }
        return out.toString();
    }

    /** "scarlet_red" → "Scarlet Red": how an id reads when nothing translates it. */
    public static String humanise(String id) {
        String spaced = normalise(id);
        StringBuilder out = new StringBuilder(spaced.length());
        boolean start = true;
        for (int i = 0; i < spaced.length(); i++) {
            char c = spaced.charAt(i);
            out.append(start ? Character.toUpperCase(c) : c);
            start = c == ' ';
        }
        return out.toString();
    }
}
