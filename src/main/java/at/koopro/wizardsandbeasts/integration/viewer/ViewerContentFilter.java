package at.koopro.wizardsandbeasts.integration.viewer;

import at.koopro.wizardsandbeasts.module.ModuleContentIndex;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.List;

/**
 * Decides what a recipe viewer must not show. Knows nothing about any particular viewer.
 *
 * <p>This is the whole of the "what"; a viewer integration supplies only the "how". Keeping the decision
 * here means the rule is stated once and cannot drift between viewers, and that adding a second viewer is
 * an adapter rather than a reimplementation. There are deliberately no JEI types in this file — if a JEI
 * import ever appears here, the seam has been put in the wrong place.
 *
 * <p>The rule itself is {@link ModuleContentIndex}'s, unchanged: content whose module is switched off is
 * hidden, {@code PREVIEW} counts as on, and untagged content is always shown. A viewer must not invent a
 * stricter or looser rule than the creative menu and the recipe book already apply, or players get two
 * different answers about what exists.
 */
@NullMarked
public final class ViewerContentFilter {

    private ViewerContentFilter() {}

    public static boolean isHidden(Item item) {
        return !ModuleContentIndex.isAccessible(item);
    }

    public static boolean isHidden(ItemStack stack) {
        return !stack.isEmpty() && isHidden(stack.getItem());
    }

    /**
     * Every registered item currently hidden.
     *
     * <p>Walks the item registry rather than reading a precomputed set: the index is keyed by content, not
     * by module, and this runs once per module flip rather than per frame. A registry walk that is
     * obviously correct beats a cache that can go stale against the thing it mirrors.
     */
    public static List<Item> hiddenItems() {
        return BuiltInRegistries.ITEM.stream()
                .filter(ViewerContentFilter::isHidden)
                .toList();
    }

    /** The complement of {@link #hiddenItems()} — what a viewer should put back when a module returns. */
    public static List<Item> visibleItems() {
        return BuiltInRegistries.ITEM.stream()
                .filter(item -> !isHidden(item))
                .toList();
    }

    /**
     * Whether a recipe should be hidden, given the stacks it names.
     *
     * <p>True if <em>any</em> of them is hidden, inputs included. A recipe whose output is still reachable
     * but whose ingredient is not is a recipe the player cannot perform, and showing it is worse than
     * showing nothing: it reads as a hint to go and find an item that no longer exists.
     */
    public static boolean hidesRecipe(Collection<ItemStack> stacks) {
        return stacks.stream().anyMatch(ViewerContentFilter::isHidden);
    }
}
