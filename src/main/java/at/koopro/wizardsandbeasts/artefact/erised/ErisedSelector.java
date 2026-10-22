package at.koopro.wizardsandbeasts.artefact.erised;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The Mirror's choices, as pure functions: which vision a gazer sees, which clue (if any) comes with it, and what
 * lingering costs. No world access, so every rule is unit-tested; {@link ErisedService} only gathers the inputs and
 * applies the answer.
 *
 * <p>Rules (user decisions, 2026-10-22): three gazes a day. The first is free and may carry a clue; the second and
 * third show a vision but pull at the gazer (Slowness and Hunger, stronger the third time); after that the Mirror
 * shows only their own reflection until the next day.
 */
@NullMarked
public final class ErisedSelector {

    public static final int GAZES_PER_DAY = 3;

    private ErisedSelector() {}

    public record Choice(Identifier id, ErisedVision vision) {}

    /** How hard the Mirror pulls after a gaze: effect seconds and amplifier, or none. */
    public record Pull(int seconds, int amplifier) {
        public static final Pull NONE = new Pull(0, 0);

        public boolean any() {
            return seconds > 0;
        }
    }

    /** True when the gazer has looked their fill today and sees only themselves. */
    public static boolean onlyReflection(int gazesToday) {
        return gazesToday >= GAZES_PER_DAY;
    }

    /** A clue comes only with the day's first gaze: lingering shows more longing, never more answers. */
    public static boolean mayGiveClue(int gazesToday) {
        return gazesToday == 0;
    }

    /** The pull that follows a gaze taken when {@code gazesToday} had already been taken. */
    public static Pull pullAfter(int gazesToday) {
        return switch (gazesToday) {
            case 0 -> Pull.NONE;
            case 1 -> new Pull(30, 0);
            default -> new Pull(60, 1);
        };
    }

    /**
     * A vision the gazer qualifies for, weighted, avoiding the last few they saw. When every vision they qualify
     * for is recent, any but the very last is allowed; with only one, it repeats. Null when nothing applies.
     */
    public static @Nullable Choice pickVision(Gazer gazer, Map<Identifier, ErisedVision> table, List<String> recent,
                                              long seed) {
        List<Choice> eligible = new ArrayList<>();
        for (Map.Entry<Identifier, ErisedVision> entry : table.entrySet()) {
            if (entry.getValue().shownTo(gazer)) {
                eligible.add(new Choice(entry.getKey(), entry.getValue()));
            }
        }
        if (eligible.isEmpty()) {
            return null;
        }
        String last = recent.isEmpty() ? null : recent.get(recent.size() - 1);
        List<Choice> pool = eligible.stream().filter(c -> !recent.contains(c.id().toString())).toList();
        if (pool.isEmpty()) {
            pool = eligible.stream().filter(c -> !c.id().toString().equals(last)).toList();
        }
        if (pool.isEmpty()) {
            pool = eligible;
        }
        int total = 0;
        for (Choice c : pool) {
            total += c.vision().weight();
        }
        int roll = new Random(seed).nextInt(total);
        for (Choice c : pool) {
            roll -= c.vision().weight();
            if (roll < 0) {
                return c;
            }
        }
        return pool.get(pool.size() - 1);
    }

    /**
     * One clue the gazer has not been given before, or null. The candidates are shuffled by {@code seed} first, so
     * which kind of thing the Mirror hints at varies from day to day instead of always being the first kind built.
     */
    public static @Nullable ClueCandidate pickClue(List<ClueCandidate> candidates, ErisedHistory history, long seed) {
        List<ClueCandidate> shuffled = new ArrayList<>(candidates);
        // Sorted first: candidates come from sets and registries whose order is not ours to rely on.
        shuffled.sort(java.util.Comparator.comparing(ClueCandidate::key));
        Collections.shuffle(shuffled, new Random(seed));
        for (ClueCandidate candidate : shuffled) {
            if (!history.gaveClue(candidate.key())) {
                return candidate;
            }
        }
        return null;
    }
}
