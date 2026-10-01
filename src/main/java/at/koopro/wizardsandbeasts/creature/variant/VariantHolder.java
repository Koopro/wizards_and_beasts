package at.koopro.wizardsandbeasts.creature.variant;

import org.jspecify.annotations.NullMarked;

/**
 * An entity that carries one of its creature's {@link CreatureVariant}s. Implemented by the entities whose variant
 * enum is registered in {@link CreatureVariants}; lets the Creature Lab set a test creature's (or a client-side
 * preview's) variant by id without knowing the entity class.
 */
@NullMarked
public interface VariantHolder {

    CreatureVariant variant();

    /**
     * Sets the variant with this id.
     *
     * @return false (and nothing changes) when the id is not one of this creature's variants
     */
    boolean applyVariant(String variantId);
}
