package at.koopro.wizardsandbeasts.artefact.erised;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Mirror has already shown one player: how often they looked today, the last few visions (so the next
 * one is not the same), and every clue it has given (so a clue is never repeated). Kept on the player and across
 * death -- the Mirror remembers who has stood before it.
 *
 * @param day    the in-game day of {@code gazes} ({@code -1}: never looked)
 * @param recent vision ids, newest last, at most {@link #MAX_RECENT}
 * @param clues  clue keys ({@link ClueCandidate#key()}), oldest first, at most {@link #MAX_CLUES}
 */
@NullMarked
public record ErisedHistory(long day, int gazes, List<String> recent, List<String> clues) {

    public static final int MAX_RECENT = 4;
    public static final int MAX_CLUES = 256;

    public static final ErisedHistory EMPTY = new ErisedHistory(-1L, 0, List.of(), List.of());

    public static final Codec<ErisedHistory> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("day", -1L).forGetter(ErisedHistory::day),
            Codec.intRange(0, 1024).optionalFieldOf("gazes", 0).forGetter(ErisedHistory::gazes),
            Codec.STRING.listOf().optionalFieldOf("recent", List.of()).forGetter(ErisedHistory::recent),
            Codec.STRING.listOf().optionalFieldOf("clues", List.of()).forGetter(ErisedHistory::clues)
    ).apply(i, ErisedHistory::new));

    public ErisedHistory {
        recent = tail(recent, MAX_RECENT);
        clues = tail(clues, MAX_CLUES);
    }

    /** Gazes already taken on {@code today}. */
    public int gazesOn(long today) {
        return day == today ? gazes : 0;
    }

    public boolean gaveClue(String key) {
        return clues.contains(key);
    }

    /** After one more gaze today that showed {@code vision} (null: only a reflection) and {@code clue} (null: none). */
    public ErisedHistory gazed(long today, @Nullable String vision, @Nullable String clue) {
        List<String> nextRecent = new ArrayList<>(recent);
        if (vision != null) {
            nextRecent.add(vision);
        }
        List<String> nextClues = new ArrayList<>(clues);
        if (clue != null && !clues.contains(clue)) {
            nextClues.add(clue);
        }
        return new ErisedHistory(today, gazesOn(today) + 1, nextRecent, nextClues);
    }

    private static List<String> tail(List<String> list, int max) {
        return List.copyOf(list.size() <= max ? list : list.subList(list.size() - max, list.size()));
    }
}
