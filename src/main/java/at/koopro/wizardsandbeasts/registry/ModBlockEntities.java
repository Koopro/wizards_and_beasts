package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.block.WandmakersBenchBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WizardsAndBeastsMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WandmakersBenchBlockEntity>> WANDMAKERS_BENCH =
            BLOCK_ENTITY_TYPES.register("wandmakers_bench", () ->
                    new BlockEntityType<>(WandmakersBenchBlockEntity::new, Set.of(ModBlocks.WANDMAKERS_BENCH.get())));

    private ModBlockEntities() {
    }
}
