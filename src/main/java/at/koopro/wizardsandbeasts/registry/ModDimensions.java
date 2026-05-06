package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

public class ModDimensions {
    public static final ResourceKey<Level> POCKET_REALM = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "pocket_realm"));

    public static final ResourceKey<DimensionType> POCKET_REALM_TYPE = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "pocket_realm"));

    private ModDimensions() {
    }
}

