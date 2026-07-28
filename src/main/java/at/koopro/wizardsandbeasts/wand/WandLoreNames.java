package at.koopro.wizardsandbeasts.wand;

import at.koopro.wizardsandbeasts.wand.registry.WandCoreDefinition;
import at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries;
import at.koopro.wizardsandbeasts.wand.registry.WandWoodDefinition;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * What to call a wand's wood and core in player-facing text.
 *
 * <p>Tooltips used to print the raw component value — {@code Wood: wizards_and_beasts:rowan}. Every wood
 * and core already carries a {@code display_name} in its datapack definition, which is the name the rest
 * of the game means to use; this resolves it.
 *
 * <p><b>Two fallbacks, in order.</b> Registry access is nullable on a tooltip context, and a definition
 * can be missing outright (a datapack naming a wood it never defines). Rather than showing a raw id or a
 * bare translation key in either case, an unresolved id is title-cased from its path: {@code rowan} reads
 * as "Rowan", which is right often enough to beat both. Only a null id falls all the way through to a
 * literal, and that is the caller saying it has nothing.
 */
@NullMarked
public final class WandLoreNames {

    private WandLoreNames() {}

    /** The wood's own name, or a readable form of its id. */
    public static Component wood(HolderLookup.@Nullable Provider registries, @Nullable Identifier id) {
        return resolve(registries, WandDatapackRegistries.WAND_WOOD_REGISTRY, id, WandWoodDefinition::displayName);
    }

    /** The core's own name, or a readable form of its id. */
    public static Component core(HolderLookup.@Nullable Provider registries, @Nullable Identifier id) {
        return resolve(registries, WandDatapackRegistries.WAND_CORE_REGISTRY, id, WandCoreDefinition::displayName);
    }

    private static <T> Component resolve(HolderLookup.@Nullable Provider registries,
                                         ResourceKey<Registry<T>> registry,
                                         @Nullable Identifier id,
                                         java.util.function.Function<T, Component> displayName) {
        if (id == null) {
            return Component.translatable("wandcraft.lore.unknown");
        }
        if (registries != null) {
            Component named = registries.lookup(registry)
                    .flatMap(lookup -> lookup.get(ResourceKey.create(registry, id)))
                    .map(holder -> displayName.apply(holder.value()))
                    .orElse(null);
            if (named != null) {
                return named;
            }
        }
        return Component.literal(titleCase(id.getPath()));
    }

    /** {@code dragon_heartstring} → {@code Dragon Heartstring}. */
    private static String titleCase(String path) {
        StringBuilder out = new StringBuilder(path.length());
        for (String word : path.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.isEmpty() ? path : out.toString();
    }
}
