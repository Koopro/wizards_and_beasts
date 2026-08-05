package at.koopro.wizardsandbeasts.apparition.splinch;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import org.jspecify.annotations.NullMarked;

/**
 * The damage a botched Apparition does. Its own type rather than generic magic, because it is the one source
 * in the mod that is clamped so it can never kill — see {@code SplinchDamageClamp}. Sharing
 * {@code damageSources().magic()} would have made every other magical wound unkillable too.
 */
@NullMarked
public final class SplinchDamageTypes {

    /** Tearing yourself getting somewhere. Never lethal on its own; anything else still is. */
    public static final ResourceKey<DamageType> SPLINCH = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "splinch"));

    private SplinchDamageTypes() {}
}
