package at.koopro.wizardsandbeasts.basilisk;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/** The two ways a basilisk kills that are not a plain bite: its eyes, and its venom working afterwards. */
public final class BasiliskDamageTypes {

    /** Meeting its eyes directly. Credited to the basilisk, so the death message names it. */
    public static final ResourceKey<DamageType> GAZE = ResourceKey.create(
            Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "basilisk_gaze"));

    /** The venom a bite or a fang leaves in the blood. */
    public static final ResourceKey<DamageType> VENOM = ResourceKey.create(
            Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "basilisk_venom"));

    private BasiliskDamageTypes() {}
}
