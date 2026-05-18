package at.koopro.wizardsandbeasts.azkaban;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

public final class AzkabanDamageTypes {

    public static final ResourceKey<DamageType> DEMENTOR_KISS = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "dementor_kiss"));

    /** Internal only — lets admin commands dissipate an otherwise immortal Dementor. */
    public static final ResourceKey<DamageType> DEMENTOR_DISSIPATE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "dementor_dissipate"));

    private AzkabanDamageTypes() {}
}
