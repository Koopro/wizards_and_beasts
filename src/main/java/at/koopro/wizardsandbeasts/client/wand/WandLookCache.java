package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.customization.WandConfiguration;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.wand.visual.WandLook;
import at.koopro.wizardsandbeasts.wand.visual.WandLookResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The client's resolved {@link WandLook}s, one per make.
 *
 * <p>A wand is drawn several times a frame (hand, hotbar, every inventory slot it sits in), and each
 * draw asks for its look twice (render state and colour). Resolving is two registry lookups and some
 * arithmetic, but there is no reason to do it hundreds of times a second for the same handful of
 * makes, so looks are kept by the inputs that decide them. Render thread only.
 *
 * <p>The synced wood and core registries are what a look is resolved against, so the cache empties
 * whenever the client is handed a different set (joining a world, a server reload).
 */
@NullMarked
public final class WandLookCache {

    private static final int CAPACITY = 256;

    private record Key(@Nullable Identifier wood, @Nullable Identifier core, @Nullable Float length,
                       @Nullable WandFlexibility flexibility, @Nullable WandConfiguration explicit, boolean elder) {}

    private static final Map<Key, WandLook> LOOKS = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, WandLook> eldest) {
            return size() > CAPACITY;
        }
    };
    private static final Map<Identifier, Boolean> GRAIN_SHEETS = new HashMap<>();
    private static HolderLookup.@Nullable Provider lastRegistries;

    private WandLookCache() {}

    static WandLook look(HolderLookup.@Nullable Provider registries, ItemStack stack) {
        if (registries != lastRegistries) {
            LOOKS.clear();
            lastRegistries = registries;
        }
        Key key = new Key(WandComponents.getWood(stack), WandComponents.getCore(stack),
                WandComponents.getLength(stack), WandComponents.getFlexibility(stack),
                stack.get(WandComponents.WAND_CONFIGURATION.get()), ModDataComponents.isElderWand(stack));
        WandLook cached = LOOKS.get(key);
        if (cached == null) {
            cached = WandLookResolver.resolve(registries, stack);
            LOOKS.put(key, cached);
        }
        return cached;
    }

    /**
     * The sheet for a grain, or null when no resource pack ships one -- a datapack wood may name a grain
     * nobody painted, and that wand gets the plain sheet rather than the missing-texture checkerboard.
     */
    static @Nullable Identifier grainSheet(@Nullable Identifier grain) {
        if (grain == null) {
            return null;
        }
        Identifier sheet = grain.withPath(path -> "textures/item/wand/grain_" + path + ".png");
        boolean present = GRAIN_SHEETS.computeIfAbsent(sheet,
                id -> Minecraft.getInstance().getResourceManager().getResource(id).isPresent());
        return present ? sheet : null;
    }

    /** Resource packs changed: a grain sheet may have appeared or gone. */
    public static final class Reload implements ResourceManagerReloadListener {
        @Override
        public void onResourceManagerReload(ResourceManager resourceManager) {
            GRAIN_SHEETS.clear();
        }
    }
}
