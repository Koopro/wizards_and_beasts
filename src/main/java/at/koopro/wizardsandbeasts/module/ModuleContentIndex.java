package at.koopro.wizardsandbeasts.module;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The reverse of {@link ModuleTags}: given a piece of content, which {@link Module} owns it.
 *
 * <p>Rebuilt from the tags on every {@code TagsUpdatedEvent} and published by swapping a {@code volatile}
 * reference to a freshly built immutable snapshot. Readers are on hot paths — interaction events, creative
 * tab builds, loot rolls — so they must never take a lock or see a half-populated map.
 *
 * <p><b>Content with no module tag is accessible.</b> That is deliberate: the index fails open, so
 * forgetting to tag something leaves it exactly as reachable as it is today rather than silently deleting
 * it from the game. Hiding content is always the result of someone having said which module owns it.
 */
@NullMarked
public final class ModuleContentIndex {

    /** One immutable answer for all three registries. Replaced wholesale; never edited in place. */
    private record Snapshot(Map<Item, Module> items,
                            Map<Block, Module> blocks,
                            Map<EntityType<?>, Module> entityTypes) {

        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of());
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Snapshot current = Snapshot.EMPTY;

    private ModuleContentIndex() {}

    // ── reads ────────────────────────────────────────────────────────────────────────────────────

    public static @Nullable Module moduleOf(Item item) {
        return current.items().get(item);
    }

    public static @Nullable Module moduleOf(Block block) {
        return current.blocks().get(block);
    }

    public static @Nullable Module moduleOf(EntityType<?> type) {
        return current.entityTypes().get(type);
    }

    public static boolean isAccessible(Item item) {
        return accessible(moduleOf(item));
    }

    public static boolean isAccessible(Block block) {
        return accessible(moduleOf(block));
    }

    public static boolean isAccessible(EntityType<?> type) {
        return accessible(moduleOf(type));
    }

    /** Convenience for the many call sites holding an {@code ItemLike} or an {@code ItemStack}'s item. */
    public static boolean isAccessible(ItemLike itemLike) {
        return isAccessible(itemLike.asItem());
    }

    /**
     * Whether content owned by {@code module} may be reached. {@code null} — untagged content — is always
     * reachable; see the class note on failing open.
     */
    public static boolean accessible(@Nullable Module module) {
        return module == null || ModuleManager.isEnabled(module);
    }

    // ── rebuild ──────────────────────────────────────────────────────────────────────────────────

    /**
     * Rebuilds from the tags in {@code provider} and publishes the result in one reference swap.
     *
     * <p>Called from the {@code TagsUpdatedEvent} handler on both sides. Missing tags are not an error:
     * a module that owns no content simply contributes nothing.
     */
    public static void rebuild(HolderLookup.Provider provider) {
        Snapshot snapshot = new Snapshot(
                index(module -> members(provider, Registries.ITEM, ModuleTags.items(module))),
                index(module -> members(provider, Registries.BLOCK, ModuleTags.blocks(module))),
                index(module -> members(provider, Registries.ENTITY_TYPE, ModuleTags.entityTypes(module))));
        current = snapshot;
        LOGGER.debug("Module content index rebuilt: {} items, {} blocks, {} entity types",
                snapshot.items().size(), snapshot.blocks().size(), snapshot.entityTypes().size());
    }

    /** Drops the index back to empty — every lookup then answers "untagged", i.e. accessible. */
    public static void clear() {
        current = Snapshot.EMPTY;
    }

    private static <T> List<T> members(HolderLookup.Provider provider,
                                       net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>> registry,
                                       TagKey<T> tag) {
        return provider.lookup(registry)
                .flatMap(lookup -> lookup.get(tag))
                .map(named -> named.stream().map(Holder::value).toList())
                .orElseGet(List::of);
    }

    /**
     * Folds each module's membership into a single content-to-module map.
     *
     * <p>Package-private and taking a plain function so the merge rule can be tested without a live
     * registry. Content tagged into two modules resolves to the first in {@link Module} declaration order
     * and warns: silently picking one would make a datapack authoring mistake invisible.
     */
    static <T> Map<T, Module> index(Function<Module, ? extends Iterable<T>> membersOf) {
        Map<T, Module> map = new LinkedHashMap<>();
        for (Module module : Module.values()) {
            for (T content : membersOf.apply(module)) {
                Module previous = map.putIfAbsent(content, module);
                if (previous != null && previous != module) {
                    LOGGER.warn(
                            "{} is tagged into both module {} and module {}; keeping {}",
                            content, previous, module, previous);
                }
            }
        }
        return Map.copyOf(map);
    }
}
