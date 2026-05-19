package at.koopro.wizardsandbeasts.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.function.Supplier;

/**
 * @deprecated Use {@link EntityAttributeBindings}. Kept as compatibility shim while cleanup lands.
 */
@Deprecated(forRemoval = false)
public final class EntityAttributes {
    private EntityAttributes() {}

    public static void queue(
            DeferredHolder<EntityType<?>, ? extends EntityType<? extends LivingEntity>> holder,
            Supplier<AttributeSupplier.Builder> attributes) {
        EntityAttributeBindings.queue(holder, attributes);
    }

    public static void registerAll(EntityAttributeCreationEvent event) {
        EntityAttributeBindings.registerAll(event);
    }
}
