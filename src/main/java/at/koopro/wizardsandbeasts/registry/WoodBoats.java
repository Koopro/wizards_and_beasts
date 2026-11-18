package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.entity.vehicle.boat.ChestBoat;
import net.minecraft.world.item.BoatItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * A boat and a chest boat of every wand wood: vanilla's {@link Boat} and {@link ChestBoat}, one entity type each
 * (as vanilla does it since 1.21.2), drawn by vanilla's boat renderer from {@code textures/entity/boat|chest_boat/<wood>}
 * ({@code tools/wood_fittings_textures.py}).
 */
public final class WoodBoats {

    private WoodBoats() {}

    public record Boats(String wood, String displayName,
                        DeferredHolder<EntityType<?>, EntityType<Boat>> boat,
                        DeferredHolder<EntityType<?>, EntityType<ChestBoat>> chestBoat,
                        DeferredItem<BoatItem> boatItem, DeferredItem<BoatItem> chestBoatItem) {}

    public static final List<Boats> ALL = new ArrayList<>();

    private static ResourceKey<EntityType<?>> key(String name) {
        return ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, name));
    }

    static {
        for (WoodFittings.Fittings fittings : WoodFittings.WOODS) {
            String wood = fittings.wood().name();
            String boatId = wood + "_boat";
            String chestId = wood + "_chest_boat";
            // The entity and its item name each other, so both go through suppliers.
            @SuppressWarnings("unchecked")
            DeferredItem<BoatItem>[] items = new DeferredItem[2];
            DeferredHolder<EntityType<?>, EntityType<Boat>> boat = ModEntities.ENTITY_TYPES.register(boatId,
                    () -> EntityType.Builder.<Boat>of((type, level) -> new Boat(type, level, () -> items[0].get()),
                                    MobCategory.MISC).noLootTable().sized(1.375f, 0.5625f).eyeHeight(0.5625f)
                            .clientTrackingRange(10).build(key(boatId)));
            DeferredHolder<EntityType<?>, EntityType<ChestBoat>> chest = ModEntities.ENTITY_TYPES.register(chestId,
                    () -> EntityType.Builder.<ChestBoat>of((type, level) -> new ChestBoat(type, level, () -> items[1].get()),
                                    MobCategory.MISC).noLootTable().sized(1.375f, 0.5625f).eyeHeight(0.5625f)
                            .clientTrackingRange(10).build(key(chestId)));
            items[0] = ModItems.ITEMS.registerItem(boatId, p -> new BoatItem(boat.get(), p), p -> p.stacksTo(1));
            items[1] = ModItems.ITEMS.registerItem(chestId, p -> new BoatItem(chest.get(), p), p -> p.stacksTo(1));
            ALL.add(new Boats(wood, fittings.displayName(), boat, chest, items[0], items[1]));
        }
    }

    public static void init() { /* force class load, triggering static field registration */ }
}
