package at.koopro.wizardsandbeasts.wand.bench;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * The tools a wandmaker keeps on the bench: one per stage of a making (owner, 2026-11-26: quality comes from skill,
 * tools and affinity together). A tool on the bench widens its own stage's minigame window. A missing tool does not
 * forbid the stage; it only makes it harder.
 *
 * <p>Each tool is drawn on the bench by its own rig bone ({@link #bone}), hidden while the tool is not there. The
 * vise, the awl and the calipers are the bench's own and always shown.
 */
@NullMarked
public enum BenchTool implements StringRepresentable {
    /** Carving the blank against its log. */
    KNIFE("carving_knife", "tool_knife"),
    /** Turning the blank on the lathe. */
    CHISEL("lathe_chisel", "tool_chisel"),
    /** Boring the channel the core lies in. */
    BRACE("boring_brace", "tool_brace"),
    /** Laying the core in and marrying it to the wood. Leather grips; a dragon core is "somewhat temperamental". */
    TONGS("core_tongs", "tool_tongs"),
    /** Sealing the finished wand. */
    VARNISH("varnish_pot", "tool_varnish");

    private final String itemId;
    private final String bone;

    BenchTool(String itemId, String bone) {
        this.itemId = itemId;
        this.bone = bone;
    }

    /** The item's registry path. */
    public String itemId() {
        return itemId;
    }

    /** The rig bone that draws it on the bench. */
    public String bone() {
        return bone;
    }

    public int bit() {
        return 1 << ordinal();
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
