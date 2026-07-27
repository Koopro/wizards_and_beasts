package at.koopro.wizardsandbeasts.module;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * The datapack tags that say which {@link Module} owns a piece of content:
 * {@code wizards_and_beasts:module/<module_name>} on the item, block and entity_type registries.
 *
 * <p>Tags rather than a Java table because that makes the answer editable from a datapack: a pack can
 * attach its own content to an existing module, {@code /reload} picks it up, and JEI and EMI can both read
 * the same tag natively instead of needing a bespoke integration to ask us.
 *
 * <p>The path is the lowercased enum constant, the same spelling {@link ModuleIds} uses, so
 * {@code module/dark_arts} and the module id {@code wizards_and_beasts:dark_arts} always agree.
 */
@NullMarked
public final class ModuleTags {

    private static final String PREFIX = "module/";

    private static final Map<Module, TagKey<Item>> ITEMS = new EnumMap<>(Module.class);
    private static final Map<Module, TagKey<Block>> BLOCKS = new EnumMap<>(Module.class);
    private static final Map<Module, TagKey<EntityType<?>>> ENTITY_TYPES = new EnumMap<>(Module.class);

    static {
        for (Module module : Module.values()) {
            Identifier id = Identifier.fromNamespaceAndPath(
                    at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID,
                    PREFIX + module.name().toLowerCase(Locale.ROOT));
            ITEMS.put(module, TagKey.create(Registries.ITEM, id));
            BLOCKS.put(module, TagKey.create(Registries.BLOCK, id));
            ENTITY_TYPES.put(module, TagKey.create(Registries.ENTITY_TYPE, id));
        }
    }

    private ModuleTags() {}

    public static TagKey<Item> items(Module module) {
        return ITEMS.get(module);
    }

    public static TagKey<Block> blocks(Module module) {
        return BLOCKS.get(module);
    }

    public static TagKey<EntityType<?>> entityTypes(Module module) {
        return ENTITY_TYPES.get(module);
    }
}
