package at.koopro.wizardsandbeasts.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Co-locates entity attribute registration with entity type registration.
 * Call {@link #queue} in ModEntities alongside each entity registration,
 * then wire {@link #registerAll} to the event bus in WizardsAndBeastsMod.java.
 */
public final class EntityAttributeBindings {

    private record Entry(
            Supplier<EntityType<? extends LivingEntity>> entityType,
            Supplier<AttributeSupplier.Builder> attributes
    ) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private EntityAttributeBindings() {}

    /**
     * Queue an entity type + attribute builder pair for registration.
     */
    public static void queue(
            DeferredHolder<EntityType<?>, ? extends EntityType<? extends LivingEntity>> holder,
            Supplier<AttributeSupplier.Builder> attributes) {
        ENTRIES.add(new Entry(holder::get, attributes));
    }

    /**
     * Register all queued attribute builders. Wire to EntityAttributeCreationEvent.
     */
    public static void registerAll(EntityAttributeCreationEvent event) {
        for (Entry entry : ENTRIES) {
            event.put(entry.entityType().get(), entry.attributes().get().build());
        }
    }
}
