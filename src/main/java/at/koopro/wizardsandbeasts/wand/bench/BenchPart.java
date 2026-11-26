package at.koopro.wizardsandbeasts.wand.bench;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

/**
 * Which part of the two-block bench a block is.
 *
 * <ul>
 *   <li>{@link #MAIN}: the turning end, with the lathe. It carries the block entity and the drop, and it draws the
 *       whole bench.</li>
 *   <li>{@link #SIDE}: the marrying end, with the rack, the vise and the lantern. Nothing but a place to stand.
 *       Clicks and breaks go through to {@link #MAIN}.</li>
 *   <li>{@link #SOLO}: a bench with no room for its second half. This is the default state, so every bench saved
 *       before the bench was two blocks wide, and every bench a structure template places without properties, loads
 *       as one. It grows its side half as soon as there is room ({@code WandmakersBenchBlockEntity.serverTick}). Until
 *       then it works cramped, drawn without its rack.</li>
 * </ul>
 */
@NullMarked
public enum BenchPart implements StringRepresentable {
    SOLO("solo"),
    MAIN("main"),
    SIDE("side");

    private final String name;

    BenchPart(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Has a block entity: the part that does the work. */
    public boolean isWorking() {
        return this != SIDE;
    }
}
