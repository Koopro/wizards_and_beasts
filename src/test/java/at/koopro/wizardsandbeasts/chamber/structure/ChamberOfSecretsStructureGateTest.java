package at.koopro.wizardsandbeasts.chamber.structure;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleDefaults;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The module gate on Chamber of Secrets generation.
 *
 * <p>The blocked cases are proven directly: a generation context made of nulls is enough, because a
 * blocked structure must return before it looks at the world at all. The accessible cases are proven by
 * the opposite — with the gate open the call reaches vanilla jigsaw placement, which dereferences the
 * chunk generator immediately, so the stub context fails fast. That failure <em>is</em> the evidence
 * that the gate let the call through instead of short-circuiting; placing a real chamber needs a real
 * chunk generator and belongs to the in-game pass, not here.
 */
class ChamberOfSecretsStructureGateTest {

    private static HolderSet<Biome> biomes;
    private static Structure.StructureSettings settings;

    @BeforeAll
    static void bootstrapMinecraft() {
        // Structure.StructureSettings' codec reaches BuiltInRegistries through StructureSpawnOverride.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        biomes = HolderSet.direct();
        settings = new Structure.StructureSettings(
                biomes,
                Map.of(),
                GenerationStep.Decoration.UNDERGROUND_DECORATION,
                TerrainAdjustment.BURY);
    }

    @AfterEach
    void restoreShippedDefaults() {
        Map<Module, ModuleState> shipped = new EnumMap<>(Module.class);
        for (Module module : Module.values()) {
            shipped.put(module, ModuleDefaults.shipped(module));
        }
        ModuleManager.acceptAuthoritative(shipped);
    }

    private static void chamberState(ModuleState state) {
        Map<Module, ModuleState> states = ModuleManager.snapshot();
        states.put(Module.CHAMBER_OF_SECRETS, state);
        ModuleManager.acceptAuthoritative(states);
    }

    private static ChamberOfSecretsStructure structure() {
        // No fallback pool: neither path under test gets far enough to resolve one.
        StructureTemplatePool emptyPool = new StructureTemplatePool(null, List.of());
        return new ChamberOfSecretsStructure(new JigsawStructure(
                settings,
                Holder.direct(emptyPool),
                Optional.empty(),
                3,
                ConstantHeight.of(VerticalAnchor.absolute(-40)),
                false,
                Optional.empty(),
                new JigsawStructure.MaxDistance(80),
                List.of(),
                DimensionPadding.ZERO,
                LiquidSettings.APPLY_WATERLOGGING));
    }

    /** Everything a blocked structure is allowed to touch — which is nothing. */
    private static Structure.GenerationContext stubContext() {
        LevelHeightAccessor heightAccessor = new LevelHeightAccessor() {
            @Override
            public int getHeight() {
                return 384;
            }

            @Override
            public int getMinY() {
                return -64;
            }
        };
        return new Structure.GenerationContext(
                null, null, null, null, null, 0L, ChunkPos.ZERO, heightAccessor, holder -> true);
    }

    private static Optional<Structure.GenerationStub> findGenerationPoint() {
        return structure().findGenerationPoint(stubContext());
    }

    @Test
    void disabledModuleYieldsNoGenerationPoint() {
        chamberState(ModuleState.DISABLED);
        assertTrue(findGenerationPoint().isEmpty());
    }

    @Test
    void comingSoonModuleYieldsNoGenerationPoint() {
        chamberState(ModuleState.COMING_SOON);
        assertTrue(findGenerationPoint().isEmpty());
    }

    @Test
    void enabledModuleReachesJigsawGeneration() {
        chamberState(ModuleState.ENABLED);
        assertThrows(NullPointerException.class, ChamberOfSecretsStructureGateTest::findGenerationPoint);
    }

    /** PREVIEW counts as accessible everywhere else in the mod; worldgen must not be the exception. */
    @Test
    void previewModuleReachesJigsawGeneration() {
        chamberState(ModuleState.PREVIEW);
        assertThrows(NullPointerException.class, ChamberOfSecretsStructureGateTest::findGenerationPoint);
    }

    /** The bug this class exists to close: a default install must place no chamber. */
    @Test
    void shippedDefaultPlacesNoChamber() {
        assertEquals(ModuleState.DISABLED, ModuleDefaults.shipped(Module.CHAMBER_OF_SECRETS));
        assertTrue(findGenerationPoint().isEmpty());
    }

    /** Biome placement, generation step, terrain adaptation and spawn overrides are the delegate's. */
    @Test
    void settingsComeFromTheJigsawDelegate() {
        ChamberOfSecretsStructure structure = structure();
        assertSame(settings, structure.modifiableStructureInfo().getOriginalStructureInfo().structureSettings());
        assertSame(GenerationStep.Decoration.UNDERGROUND_DECORATION, structure.step());
        assertSame(TerrainAdjustment.BURY, structure.terrainAdaptation());
        assertSame(biomes, structure.biomes());
        assertEquals(Map.of(), structure.spawnOverrides());
    }
}
