package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.block.AmbientParticleBlock;
import at.koopro.wizardsandbeasts.block.FloatingCandleBlock;
import at.koopro.wizardsandbeasts.block.HouseBannerBlock;
import at.koopro.wizardsandbeasts.block.MallowsweetBlock;
import at.koopro.wizardsandbeasts.spell.teacher.SpellTeacherBlock;
import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

final class WizardingWorldBlockRegistry {
    static final DeferredBlock<Block> DEVILS_SNARE =
            ModBlocks.BLOCKS.registerBlock("devils_snare", Block::new,
                    () -> BlockBehaviour.Properties.ofFullCopy(Blocks.COBWEB));

    static final DeferredBlock<MallowsweetBlock> MALLOWSWEET =
            ModBlocks.BLOCKS.registerBlock("mallowsweet", MallowsweetBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.PLANT)
                            .noCollision()
                            .instabreak()
                            .sound(SoundType.GRASS)
                            .offsetType(BlockBehaviour.OffsetType.XZ)
                            .pushReaction(PushReaction.DESTROY));

    static final DeferredBlock<HouseBannerBlock> GRYFFINDOR_BANNER =
            ModBlocks.BLOCKS.registerBlock("gryffindor_banner", HouseBannerBlock::new,
                    () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).instabreak().sound(SoundType.WOOL).noOcclusion());
    static final DeferredBlock<HouseBannerBlock> SLYTHERIN_BANNER =
            ModBlocks.BLOCKS.registerBlock("slytherin_banner", HouseBannerBlock::new,
                    () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).instabreak().sound(SoundType.WOOL).noOcclusion());
    static final DeferredBlock<HouseBannerBlock> RAVENCLAW_BANNER =
            ModBlocks.BLOCKS.registerBlock("ravenclaw_banner", HouseBannerBlock::new,
                    () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).instabreak().sound(SoundType.WOOL).noOcclusion());
    static final DeferredBlock<HouseBannerBlock> HUFFLEPUFF_BANNER =
            ModBlocks.BLOCKS.registerBlock("hufflepuff_banner", HouseBannerBlock::new,
                    () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).instabreak().sound(SoundType.WOOL).noOcclusion());

    static final DeferredBlock<FloatingCandleBlock> FLOATING_CANDLE =
            ModBlocks.BLOCKS.registerBlock("floating_candle", FloatingCandleBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOL)
                            .noCollision()
                            .instabreak()
                            .lightLevel(s -> 14)
                            .sound(SoundType.CANDLE)
                            .noOcclusion());

    /** Brew tint drifting off a cauldron's mouth — one-in-eight so a potions room simmers rather than smokes. */
    private static AmbientParticleBlock cauldron(BlockBehaviour.Properties props, int argb) {
        return new AmbientParticleBlock(props,
                () -> new SpellTintParticleOptions(ModParticles.ARCANE_MOTE.get(), argb),
                AmbientParticleBlock.Emission.ABOVE, 8);
    }

    static final DeferredBlock<AmbientParticleBlock> BRASS_CAULDRON =
            ModBlocks.BLOCKS.registerBlock("brass_cauldron",
                    props -> cauldron(props, 0xFF8FD6B0), RegistryUtils::metalCauldronProps);
    static final DeferredBlock<AmbientParticleBlock> WIZARDING_COPPER_CAULDRON =
            ModBlocks.BLOCKS.registerBlock("wizarding_copper_cauldron",
                    props -> cauldron(props, 0xFFB08FD6), RegistryUtils::metalCauldronProps);
    static final DeferredBlock<AmbientParticleBlock> PEWTER_CAULDRON =
            ModBlocks.BLOCKS.registerBlock("pewter_cauldron",
                    props -> cauldron(props, 0xFF9AB5C2), RegistryUtils::metalCauldronProps);
    /**
     * Decorative grate block — no Floo Network travel logic.
     * LORE: Intended as a visual prop (fireplace surround), not a travel node.
     * @deprecated Superseded by {@link at.koopro.wizardsandbeasts.block.FlooFireplaceBlock}.
     *             Will be removed in beta. Use {@code FLOO_FIREPLACE} for functional travel.
     */
    @Deprecated
    static final DeferredBlock<AmbientParticleBlock> FLOO_GRATE =
            ModBlocks.BLOCKS.registerBlock("floo_grate",
                    props -> new AmbientParticleBlock(props,
                            () -> new SpellTintParticleOptions(ModParticles.ARCANE_MOTE.get(), 0xFF4AC27A),
                            AmbientParticleBlock.Emission.ABOVE, 12),
                    () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.COPPER).noOcclusion());

    static final DeferredBlock<SpellTeacherBlock> SPELL_TEACHER =
            ModBlocks.BLOCKS.registerBlock("spell_teacher", SpellTeacherBlock::new,
                    () -> BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.WOOD));
    static final DeferredBlock<WandmakersBenchBlock> WANDMAKERS_BENCH =
            ModBlocks.BLOCKS.registerBlock("wandmakers_bench", WandmakersBenchBlock::new,
                    () -> BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.WOOD));

    static final DeferredItem<BlockItem> DEVILS_SNARE_ITEM = RegistryUtils.registerSimpleBlockItem("devils_snare", DEVILS_SNARE);
    static final DeferredItem<BlockItem> MALLOWSWEET_ITEM = RegistryUtils.registerSimpleBlockItem("mallowsweet", MALLOWSWEET);
    static final DeferredItem<BlockItem> GRYFFINDOR_BANNER_ITEM = RegistryUtils.registerSimpleBlockItem("gryffindor_banner", GRYFFINDOR_BANNER);
    static final DeferredItem<BlockItem> SLYTHERIN_BANNER_ITEM = RegistryUtils.registerSimpleBlockItem("slytherin_banner", SLYTHERIN_BANNER);
    static final DeferredItem<BlockItem> RAVENCLAW_BANNER_ITEM = RegistryUtils.registerSimpleBlockItem("ravenclaw_banner", RAVENCLAW_BANNER);
    static final DeferredItem<BlockItem> HUFFLEPUFF_BANNER_ITEM = RegistryUtils.registerSimpleBlockItem("hufflepuff_banner", HUFFLEPUFF_BANNER);
    static final DeferredItem<BlockItem> FLOATING_CANDLE_ITEM = RegistryUtils.registerSimpleBlockItem("floating_candle", FLOATING_CANDLE);
    static final DeferredItem<BlockItem> BRASS_CAULDRON_ITEM = RegistryUtils.registerSimpleBlockItem("brass_cauldron", BRASS_CAULDRON);
    static final DeferredItem<BlockItem> WIZARDING_COPPER_CAULDRON_ITEM = RegistryUtils.registerSimpleBlockItem("wizarding_copper_cauldron", WIZARDING_COPPER_CAULDRON);
    static final DeferredItem<BlockItem> PEWTER_CAULDRON_ITEM = RegistryUtils.registerSimpleBlockItem("pewter_cauldron", PEWTER_CAULDRON);
    static final DeferredItem<BlockItem> FLOO_GRATE_ITEM = RegistryUtils.registerSimpleBlockItem("floo_grate", FLOO_GRATE);
    static final DeferredItem<BlockItem> SPELL_TEACHER_ITEM = RegistryUtils.registerSimpleBlockItem("spell_teacher", SPELL_TEACHER);
    static final DeferredItem<BlockItem> WANDMAKERS_BENCH_ITEM = RegistryUtils.registerSimpleBlockItem("wandmakers_bench", WANDMAKERS_BENCH);

    private WizardingWorldBlockRegistry() {
    }
}
