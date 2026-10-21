package at.koopro.wizardsandbeasts.equipment;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@code wizards_and_beasts:wearables} item data map — the registry of what can be worn.
 *
 * <p>A NeoForge data map rather than a bespoke reload listener: datapacks can add, override and
 * remove entries with the standard {@code values}/{@code replace}/{@code remove} syntax, and the map
 * is synced to clients on login and reload, which the renderer needs for armour hiding. Sync is not
 * mandatory, so a vanilla client simply sees no definitions.
 *
 * <p>Lookups are a hash read on the item holder, cheap enough for the per-frame render path.
 */
@NullMarked
public final class WearableDefinitions {

    public static final DataMapType<Item, WearableDefinition> WEARABLES = DataMapType
            .builder(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "wearables"),
                    Registries.ITEM, WearableDefinition.CODEC)
            .synced(WearableDefinition.CODEC, false)
            .build();

    private WearableDefinitions() {}

    /** Mod-bus listener. */
    public static void register(RegisterDataMapTypesEvent event) {
        event.register(WEARABLES);
    }

    /** The definition for this stack's item, or {@code null} if it is not a W&amp;B wearable. */
    public static @Nullable WearableDefinition get(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return stack.getItemHolder().getData(WEARABLES);
    }

    /** Whether this stack is defined as the given type. */
    public static boolean is(ItemStack stack, EquipmentType type) {
        WearableDefinition definition = get(stack);
        return definition != null && definition.type() == type;
    }
}
