package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.cache.GeckoLibResources;
import software.bernie.geckolib.loading.object.BakedAnimations;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which clips a creature rig's animation file really defines, read from GeckoLib's own baked animations — so the
 * activity controller ({@link AnimHelper#activityController}) only ever asks for a clip that exists. GeckoLib throws
 * inside the render pass on an unknown clip; the server never asks this (it answers "no" there).
 *
 * <p>Clips are named {@code animation.<asset>.<clip>}, the convention {@link AnimHelper#loop} builds. The file for an
 * asset is found once by its baked-cache key ({@code wizards_and_beasts:entity/<asset>}) and looked up again in the
 * cache on every ask, so a resource reload is picked up with no listener of its own.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class RigClips {

    private static final Map<String, Identifier> FILE_OF = new ConcurrentHashMap<>();
    /** Assets with no file, and the cache size when that was found: not searched again until the cache changes. */
    private static final Map<String, Integer> MISSING = new ConcurrentHashMap<>();

    private RigClips() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        AnimHelper.installRigLookup(RigClips::has);
    }

    public static boolean has(String asset, String clip) {
        BakedAnimations baked = baked(asset);
        return baked != null && baked.animations().containsKey("animation." + asset + "." + clip);
    }

    private static @Nullable BakedAnimations baked(String asset) {
        Map<Identifier, BakedAnimations> cache = GeckoLibResources.getBakedAnimations().cache();
        Identifier file = FILE_OF.computeIfAbsent(asset, a -> Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "entity/" + a));
        BakedAnimations baked = cache.get(file);
        if (baked != null) {
            return baked;
        }
        if (MISSING.getOrDefault(asset, -1) == cache.size()) {
            return null;
        }
        // Not under the conventional key: find the file once by its name, and remember where it was.
        for (Map.Entry<Identifier, BakedAnimations> e : cache.entrySet()) {
            String path = e.getKey().getPath();
            if (e.getKey().getNamespace().equals(WizardsAndBeastsMod.MODID)
                    && (path.equals(asset) || path.endsWith("/" + asset))) {
                FILE_OF.put(asset, e.getKey());
                return e.getValue();
            }
        }
        MISSING.put(asset, cache.size());
        return null;
    }
}
