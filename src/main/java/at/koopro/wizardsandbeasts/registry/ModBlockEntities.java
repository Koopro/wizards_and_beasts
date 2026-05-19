package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.block.ExpansionFocusBlockEntity;
import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlockEntity;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlockEntity;
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExpansionFocusBlockEntity>> POCKET_CONFIGURATOR =
            BLOCK_ENTITY_TYPES.register("pocket_configurator", () ->
                    new BlockEntityType<>(ExpansionFocusBlockEntity::new, Set.of(ModBlocks.POCKET_CONFIGURATOR.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlooFireplaceBlockEntity>> FLOO_FIREPLACE =
            BLOCK_ENTITY_TYPES.register("floo_fireplace", () ->
                    new BlockEntityType<>(FlooFireplaceBlockEntity::new, Set.of(ModBlocks.FLOO_FIREPLACE.get())));

    private ModBlockEntities() {
    }
}
