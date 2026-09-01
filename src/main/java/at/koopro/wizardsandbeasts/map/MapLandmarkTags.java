package at.koopro.wizardsandbeasts.map;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * The block palettes that make a wizarding landmark, as tags.
 *
 * <p>None of these places are generated. The mod ships the stone and players build with it, so the
 * only honest way for the map to find Hogwarts is to notice that somebody has laid a great deal of
 * Hogwarts stone in one place. These tags are what "a great deal of Hogwarts stone" means, and the
 * {@code map_discovery} rules point at them.
 *
 * <p>Membership is derived from the registry name rather than hand-listed. Every one of these build
 * sets is already named after the place it belongs to — that naming convention <em>is</em> the
 * grouping — and a hand-written list of forty ids is a list that silently stops being right the
 * next time someone adds a marble variant. {@code ModBlockTagsProvider} does the filtering, so the
 * generated tag is exact and a new block joins its landmark for free.
 */
public final class MapLandmarkTags {

    private MapLandmarkTags() {
    }

    private static TagKey<Block> landmark(String path) {
        return TagKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "landmark/" + path));
    }

    public static final TagKey<Block> HOGWARTS = landmark("hogwarts");
    public static final TagKey<Block> HOGSMEADE = landmark("hogsmeade");
    public static final TagKey<Block> DIAGON_ALLEY = landmark("diagon_alley");
    public static final TagKey<Block> GRINGOTTS = landmark("gringotts");
    public static final TagKey<Block> MINISTRY = landmark("ministry");

    /**
     * A landmark tag and the registry-name prefixes whose blocks belong to it.
     *
     * <p>The prefixes are plural because two of these places are named after the shops in them:
     * Hogsmeade's palette includes the Three Broomsticks' timber and Honeydukes' pastels, and the
     * castle's includes its enchanted ceiling. Those are not exceptions to the convention so much
     * as the convention naming a building instead of the village around it.
     */
    public record Group(TagKey<Block> tag, List<String> prefixes) {
    }

    public static final List<Group> GROUPS = List.of(
            new Group(HOGWARTS, List.of("hogwarts_", "enchanted_ceiling_tile")),
            new Group(HOGSMEADE, List.of("hogsmeade_", "three_broomsticks_", "honeydukes_")),
            new Group(DIAGON_ALLEY, List.of("diagon_")),
            new Group(GRINGOTTS, List.of("gringotts_")),
            new Group(MINISTRY, List.of("ministry_")));

    /** True when a block's registry path belongs to this group. */
    public static boolean belongs(Group group, String path) {
        for (String prefix : group.prefixes()) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
