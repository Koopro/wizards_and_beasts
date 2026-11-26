package at.koopro.wizardsandbeasts.block.floo;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

/**
 * What burns on a Floo grate. Canon order (CANON.md, books): an ordinary fire burns first ({@link #WARM}); a pinch of
 * Floo Powder thrown into it turns it emerald and makes it roar ({@link #FLOO}) for one traveller.
 */
@NullMarked
public enum HearthFire implements StringRepresentable {
    NONE("none", 0),
    WARM("warm", 15),
    FLOO("floo", 13);

    private final String name;
    private final int light;

    HearthFire(String name, int light) {
        this.name = name;
        this.light = light;
    }

    /** Block light the grate gives off while this fire burns. */
    public int light() {
        return light;
    }

    public boolean burning() {
        return this != NONE;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
