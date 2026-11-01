package at.koopro.wizardsandbeasts.settlement.structure;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.NullMarked;

/**
 * Registration for the settlement structure type. Always registered: the module is read per generation attempt inside
 * {@link SettlementStructure}, never here.
 */
@NullMarked
public final class SettlementStructures {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, WizardsAndBeastsMod.MODID);

    /** One type for every settlement: each is a {@code worldgen/structure} file and its pools. */
    public static final DeferredHolder<StructureType<?>, StructureType<SettlementStructure>> SETTLEMENT =
            STRUCTURE_TYPES.register("settlement", () -> () -> SettlementStructure.CODEC);

    private SettlementStructures() {}
}
