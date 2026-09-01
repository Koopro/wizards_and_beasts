package at.koopro.wizardsandbeasts.azkaban.structure;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleDefaults;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The module gate on Azkaban generation, mirroring {@code ChamberOfSecretsStructureGateTest}.
 *
 * <p>Both structures ship {@code DISABLED} for the same reason — their NBT is a placeholder, so
 * generating one would put an empty box in the world — and both claim in a comment to be gated.
 * The Chamber's claim was tested; Azkaban's was not, which meant the two structures with identical
 * risk had one test between them.
 *
 * <p>Same proof shape: a generation context made of nulls is enough for the blocked cases, because a
 * blocked structure must return before it touches the world. The accessible cases are proven by the
 * opposite — with the gate open the call reaches
 * {@code context.structureTemplateManager()}, which is null in the stub, so it fails fast. That
 * failure <em>is</em> the evidence the gate let the call through.
 */
class AzkabanStructureGateTest {

    private static HolderSet<Biome> biomes;
    private static Structure.StructureSettings settings;

    @BeforeAll
    static void bootstrapMinecraft() {
        // StructureSettings' codec reaches BuiltInRegistries through StructureSpawnOverride, and the
        // palette codec reaches BuiltInRegistries.BLOCK directly.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        biomes = HolderSet.direct();
        settings = new Structure.StructureSettings(
                biomes,
                Map.of(),
                GenerationStep.Decoration.SURFACE_STRUCTURES,
                TerrainAdjustment.NONE);
    }

    @AfterEach
    void restoreShippedDefaults() {
        Map<Module, ModuleState> shipped = new EnumMap<>(Module.class);
        for (Module module : Module.values()) {
            shipped.put(module, ModuleDefaults.shipped(module));
        }
        ModuleManager.acceptAuthoritative(shipped);
    }

    private static void azkabanState(ModuleState state) {
        Map<Module, ModuleState> states = ModuleManager.snapshot();
        states.put(Module.AZKABAN, state);
        ModuleManager.acceptAuthoritative(states);
    }

    private static AzkabanStructure structure() {
        return new AzkabanStructure(
                settings,
                Identifier.fromNamespaceAndPath("wizards_and_beasts", "azkaban"),
                10,
                3,
                AzkabanStructure.Palette.DEFAULT);
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
        azkabanState(ModuleState.DISABLED);
        assertTrue(findGenerationPoint().isEmpty());
    }

    @Test
    void comingSoonModuleYieldsNoGenerationPoint() {
        azkabanState(ModuleState.COMING_SOON);
        assertTrue(findGenerationPoint().isEmpty());
    }

    @Test
    void enabledModuleReachesTemplateLookup() {
        azkabanState(ModuleState.ENABLED);
        assertThrows(NullPointerException.class, AzkabanStructureGateTest::findGenerationPoint);
    }

    /** PREVIEW counts as accessible everywhere else in the mod; worldgen must not be the exception. */
    @Test
    void previewModuleReachesTemplateLookup() {
        azkabanState(ModuleState.PREVIEW);
        assertThrows(NullPointerException.class, AzkabanStructureGateTest::findGenerationPoint);
    }

    /** The bug this class exists to close: a default install must place no fortress. */
    @Test
    void shippedDefaultPlacesNoFortress() {
        assertEquals(ModuleState.DISABLED, ModuleDefaults.shipped(Module.AZKABAN));
        assertTrue(findGenerationPoint().isEmpty());
    }

    /**
     * Both placeholder-NBT structures ship off, and neither ships {@code COMING_SOON}.
     *
     * <p>{@code COMING_SOON} would put them out of an operator's reach, which is a different
     * decision from "off by default" and is not the one that was made.
     */
    @Test
    void bothPlaceholderStructuresShipDisabledAndOperatorReachable() {
        assertEquals(ModuleState.DISABLED, ModuleDefaults.shipped(Module.AZKABAN));
        assertEquals(ModuleState.DISABLED, ModuleDefaults.shipped(Module.CHAMBER_OF_SECRETS));
    }

    @Test
    void settingsAreCarriedThrough() {
        AzkabanStructure structure = structure();
        assertSame(GenerationStep.Decoration.SURFACE_STRUCTURES, structure.step());
        assertSame(TerrainAdjustment.NONE, structure.terrainAdaptation());
        assertSame(biomes, structure.biomes());
        assertEquals(Map.of(), structure.spawnOverrides());
    }
}
