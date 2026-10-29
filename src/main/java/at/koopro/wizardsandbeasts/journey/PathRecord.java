package at.koopro.wizardsandbeasts.journey;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The only thing the Apprentice's Path stores: which steps are done, which of those still owe their reward, and
 * whether the player walks the path at all. Everything else is read from the systems that own it. A step stays done
 * once recorded, even if its fact later stops holding (the wand lost, the heritage reassigned).
 *
 * <p>Paying is two writes around the hand-over: a step is recorded done <em>and owed</em>, the reward is handed over,
 * then the debt is struck. A reward that throws half-way stays owed and is paid at the next login; a step is never
 * owed twice, so nothing is paid twice.
 *
 * <p>Ids rather than {@link PathStep} values, so a save that holds a step this build no longer has still loads,
 * and keeps it.
 *
 * @param guided false for a player who switched the path off ({@code /wandb player path off}): no hints, no rewards,
 *               and nothing is noticed until it is switched back on. Game-test mock players start unguided, so a
 *               scenario about something else is never paid by the path
 */
@NullMarked
public record PathRecord(Set<String> done, Set<String> owed, boolean guided) {

    public static final PathRecord EMPTY = new PathRecord(Set.of(), Set.of(), true);

    public static final Codec<PathRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().fieldOf("done").forGetter(record -> List.copyOf(record.done())),
            Codec.STRING.listOf().optionalFieldOf("owed", List.of()).forGetter(record -> List.copyOf(record.owed())),
            Codec.BOOL.optionalFieldOf("guided", true).forGetter(PathRecord::guided)
    ).apply(instance, (done, owed, guided) ->
            new PathRecord(new LinkedHashSet<>(done), new LinkedHashSet<>(owed), guided)));

    public PathRecord {
        done = Set.copyOf(done);
        owed = Set.copyOf(owed);
    }

    public boolean has(PathStep step) {
        return done.contains(step.id());
    }

    public boolean owes(PathStep step) {
        return owed.contains(step.id());
    }

    /** Recorded done with nothing owed: a step reconciled from an old save, or one switched off. */
    public PathRecord with(PathStep step) {
        if (has(step)) {
            return this;
        }
        Set<String> next = new LinkedHashSet<>(done);
        next.add(step.id());
        return new PathRecord(next, owed, guided);
    }

    /** Recorded done and owed its reward. A step already done is left as it is, so it can never be owed twice. */
    public PathRecord earned(PathStep step) {
        if (has(step)) {
            return this;
        }
        Set<String> nextDone = new LinkedHashSet<>(done);
        nextDone.add(step.id());
        Set<String> nextOwed = new LinkedHashSet<>(owed);
        nextOwed.add(step.id());
        return new PathRecord(nextDone, nextOwed, guided);
    }

    /** The reward handed over: the debt is struck, the step stays done. */
    public PathRecord paid(PathStep step) {
        if (!owes(step)) {
            return this;
        }
        Set<String> next = new LinkedHashSet<>(owed);
        next.remove(step.id());
        return new PathRecord(done, next, guided);
    }

    public PathRecord guided(boolean on) {
        return new PathRecord(done, owed, on);
    }
}
