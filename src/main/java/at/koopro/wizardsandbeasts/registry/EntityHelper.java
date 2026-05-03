package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Static factory methods for entity registration.
 * Wraps the verbose EntityType.Builder + ResourceKey.create() ceremony.
 */
public final class EntityHelper {

    private EntityHelper() {}

    private static ResourceKey<EntityType<?>> key(String name) {
        return ResourceKey.create(Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, name));
    }

    /**
     * Register an entity with default tracking (range=10, interval=3).
     */
    public static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> register(
            DeferredRegister<EntityType<?>> registry,
            String name,
            EntityType.EntityFactory<T> factory,
            MobCategory category,
            float width,
            float height) {
        return registry.register(name, () -> EntityType.Builder.of(factory, category)
                .sized(width, height)
                .clientTrackingRange(10)
                .updateInterval(3)
                .build(key(name)));
    }

    /**
     * Register an entity with custom tracking range and update interval.
     */
    public static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> register(
            DeferredRegister<EntityType<?>> registry,
            String name,
            EntityType.EntityFactory<T> factory,
            MobCategory category,
            float width,
            float height,
            int trackingRange,
            int updateInterval) {
        return registry.register(name, () -> EntityType.Builder.of(factory, category)
                .sized(width, height)
                .clientTrackingRange(trackingRange)
                .updateInterval(updateInterval)
                .build(key(name)));
    }

    /**
     * Register a MISC-category entity with noSummon (e.g., brooms, projectiles).
     */
    public static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> registerMisc(
            DeferredRegister<EntityType<?>> registry,
            String name,
            EntityType.EntityFactory<T> factory,
            float width,
            float height) {
        return registry.register(name, () -> EntityType.Builder.<T>of(factory, MobCategory.MISC)
                .sized(width, height)
                .clientTrackingRange(10)
                .updateInterval(3)
                .noSummon()
                .build(key(name)));
    }

    /**
     * Register a MISC-category entity with noSummon and custom tracking.
     */
    public static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> registerMisc(
            DeferredRegister<EntityType<?>> registry,
            String name,
            EntityType.EntityFactory<T> factory,
            float width,
            float height,
            int trackingRange,
            int updateInterval) {
        return registry.register(name, () -> EntityType.Builder.<T>of(factory, MobCategory.MISC)
                .sized(width, height)
                .clientTrackingRange(trackingRange)
                .updateInterval(updateInterval)
                .noSummon()
                .build(key(name)));
    }
}
