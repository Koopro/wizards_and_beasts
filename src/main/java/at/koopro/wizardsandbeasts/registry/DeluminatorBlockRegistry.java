package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.block.DeluminatorLightBlock;
import at.koopro.wizardsandbeasts.block.UnlitTorchBlock;
import at.koopro.wizardsandbeasts.block.UnlitWallTorchBlock;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

final class DeluminatorBlockRegistry {
    static final DeferredBlock<DeluminatorLightBlock> DELUMINATOR_LIGHT =
            ModBlocks.BLOCKS.registerBlock("deluminator_light", DeluminatorLightBlock::new,
                    BlockBehaviour.Properties.of()
                            .noCollision()
                            .noOcclusion()
                            .replaceable()
                            .instabreak()
                            .lightLevel(s -> 15)
                            .pushReaction(PushReaction.DESTROY));

    static final DeferredBlock<UnlitTorchBlock> UNLIT_TORCH =
            ModBlocks.BLOCKS.registerBlock("unlit_torch",
                    props -> new UnlitTorchBlock(ParticleTypes.FLAME, props),
                    RegistryUtils.unlitTorchProps());
    static final DeferredBlock<UnlitWallTorchBlock> UNLIT_WALL_TORCH =
            ModBlocks.BLOCKS.registerBlock("unlit_wall_torch",
                    props -> new UnlitWallTorchBlock(ParticleTypes.FLAME, props),
                    RegistryUtils.unlitTorchProps());
    static final DeferredBlock<UnlitTorchBlock> UNLIT_SOUL_TORCH =
            ModBlocks.BLOCKS.registerBlock("unlit_soul_torch",
                    props -> new UnlitTorchBlock(ParticleTypes.SOUL_FIRE_FLAME, props),
                    RegistryUtils.unlitTorchProps());
    static final DeferredBlock<UnlitWallTorchBlock> UNLIT_SOUL_WALL_TORCH =
            ModBlocks.BLOCKS.registerBlock("unlit_soul_wall_torch",
                    props -> new UnlitWallTorchBlock(ParticleTypes.SOUL_FIRE_FLAME, props),
                    RegistryUtils.unlitTorchProps());
    static final DeferredBlock<UnlitTorchBlock> UNLIT_COPPER_TORCH =
            ModBlocks.BLOCKS.registerBlock("unlit_copper_torch",
                    props -> new UnlitTorchBlock(ParticleTypes.FLAME, props),
                    RegistryUtils.unlitTorchProps());
    static final DeferredBlock<UnlitWallTorchBlock> UNLIT_COPPER_WALL_TORCH =
            ModBlocks.BLOCKS.registerBlock("unlit_copper_wall_torch",
                    props -> new UnlitWallTorchBlock(ParticleTypes.FLAME, props),
                    RegistryUtils.unlitTorchProps());

    static final DeferredBlock<LanternBlock> UNLIT_LANTERN =
            ModBlocks.BLOCKS.registerBlock("unlit_lantern", LanternBlock::new,
                    RegistryUtils.lanternProps());
    static final DeferredBlock<LanternBlock> UNLIT_SOUL_LANTERN =
            ModBlocks.BLOCKS.registerBlock("unlit_soul_lantern", LanternBlock::new,
                    RegistryUtils.lanternProps());
    static final DeferredBlock<LanternBlock> UNLIT_COPPER_LANTERN =
            ModBlocks.BLOCKS.registerBlock("unlit_copper_lantern", LanternBlock::new,
                    RegistryUtils.lanternProps());

    static final DeferredBlock<Block> UNLIT_GLOWSTONE =
            ModBlocks.BLOCKS.registerBlock("unlit_glowstone", Block::new,
                    BlockBehaviour.Properties.of()
                            .strength(0.3F)
                            .sound(SoundType.GLASS));

    private DeluminatorBlockRegistry() {
    }
}
