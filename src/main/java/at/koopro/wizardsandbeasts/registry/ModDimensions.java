package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

public class ModDimensions {
    public static final ResourceKey<Level> EXTENSION_REALM = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "extension_realm"));

    public static final ResourceKey<DimensionType> EXTENSION_REALM_TYPE = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "extension_realm"));

    private ModDimensions() {
    }
}

