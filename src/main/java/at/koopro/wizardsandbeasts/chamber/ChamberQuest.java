package at.koopro.wizardsandbeasts.chamber;

import com.mojang.serialization.Codec;
import org.jspecify.annotations.NullMarked;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One player's Chamber of Secrets quest: the stages reached, by id. Kept through death and logout. Ids rather than
 * enum values, so a stage a later build drops does not fail the load.
 */
@NullMarked
public record ChamberQuest(Set<String> reached) {

    public static final ChamberQuest EMPTY = new ChamberQuest(Set.of());

    public static final Codec<ChamberQuest> CODEC = Codec.STRING.listOf()
            .xmap(list -> new ChamberQuest(new LinkedHashSet<>(list)), quest -> List.copyOf(quest.reached()));

    public ChamberQuest {
        reached = Set.copyOf(reached);
    }

    public boolean has(ChamberStage stage) {
        return reached.contains(stage.id());
    }

    public ChamberQuest with(ChamberStage stage) {
        if (has(stage)) {
            return this;
        }
        Set<String> next = new LinkedHashSet<>(reached);
        next.add(stage.id());
        return new ChamberQuest(next);
    }

    /** The stages this build knows, reached. */
    public Set<ChamberStage> stages() {
        Set<ChamberStage> out = EnumSet.noneOf(ChamberStage.class);
        for (String id : reached) {
            ChamberStage stage = ChamberStage.byId(id);
            if (stage != null) {
                out.add(stage);
            }
        }
        return out;
    }

    /**
     * Worthy of the sword's recall: has slain a Chamber's basilisk. The Sorting Hat returns a lost sword only to such a
     * wearer (user decision 2026-10-29: the Chamber earns the sword, the Hat recalls it).
     */
    public boolean worthy() {
        return has(ChamberStage.BASILISK_SLAIN);
    }
}
