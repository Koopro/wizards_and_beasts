package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.block.ExpansionFocusBlockEntity;
import at.koopro.wizardsandbeasts.block.trunk.TentBlockEntity;
import at.koopro.wizardsandbeasts.block.trunk.TrunkBlockEntity;
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrunkBlockEntity>> TRUNK =
            BLOCK_ENTITY_TYPES.register("trunk", () ->
                    new BlockEntityType<>(TrunkBlockEntity::new, Set.of(
                            ModBlocks.ENCHANTED_TRUNK.get(),
                            ModBlocks.EXPANDED_TRUNK.get(),
                            ModBlocks.MASTERS_TRUNK.get(),
                            ModBlocks.MOODYS_TRUNK.get(),
                            ModBlocks.NEWTS_CASE.get())));

    // Tent preview harness — one type per block (model choice lives at the renderer factory, see
    // GeoRendererHelper.simpleBlock), not one shared type across both like TRUNK above.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TentBlockEntity>> TENT_CANVAS =
            BLOCK_ENTITY_TYPES.register("tent_canvas", () ->
                    new BlockEntityType<>((pos, state) -> new TentBlockEntity(ModBlockEntities.TENT_CANVAS.get(), pos, state),
                            Set.of(ModBlocks.TENT_CANVAS.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TentBlockEntity>> TENT_GRAND =
            BLOCK_ENTITY_TYPES.register("tent_grand", () ->
                    new BlockEntityType<>((pos, state) -> new TentBlockEntity(ModBlockEntities.TENT_GRAND.get(), pos, state),
                            Set.of(ModBlocks.TENT_GRAND.get())));

    private ModBlockEntities() {
    }
}
