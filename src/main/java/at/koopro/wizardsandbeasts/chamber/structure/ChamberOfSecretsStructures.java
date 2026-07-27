package at.koopro.wizardsandbeasts.chamber.structure;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.NullMarked;

/**
 * Registration for the Chamber of Secrets structure type. Always registered — the module flag is read
 * per generation attempt inside {@link ChamberOfSecretsStructure}, never here.
 */
@NullMarked
public final class ChamberOfSecretsStructures {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, WizardsAndBeastsMod.MODID);

    /** Module-gated wrapper around vanilla jigsaw generation. No bespoke pieces — vanilla places them. */
    public static final DeferredHolder<StructureType<?>, StructureType<ChamberOfSecretsStructure>>
            CHAMBER_OF_SECRETS = STRUCTURE_TYPES.register(
                    "chamber_of_secrets", () -> () -> ChamberOfSecretsStructure.CODEC);

    private ChamberOfSecretsStructures() {}
}
