package at.koopro.wizardsandbeasts.module;

import at.koopro.wizardsandbeasts.module.settings.ModuleSettingsSchema;
import at.koopro.wizardsandbeasts.module.settings.ModuleSettingsValues;
import at.koopro.wizardsandbeasts.module.settings.SettingDefinition;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The module state model and the settings framework. The framework ships with every schema empty, so it is
 * proven here against a schema registered only for the duration of a test.
 */
class ModuleStateTest {

    private static final Gson GSON = new Gson();
    private static final Identifier FLAG = Identifier.fromNamespaceAndPath("wizards_and_beasts", "test_flag");
    private static final Identifier COUNT = Identifier.fromNamespaceAndPath("wizards_and_beasts", "test_count");
    private static final Identifier RATIO = Identifier.fromNamespaceAndPath("wizards_and_beasts", "test_ratio");
    private static final Identifier CHOICE = Identifier.fromNamespaceAndPath("wizards_and_beasts", "test_choice");

    enum Choice implements StringRepresentable {
        LOW("low"), HIGH("high");

        private final String name;

        Choice(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    private static final SettingDefinition.BoolSetting BOOL =
            new SettingDefinition.BoolSetting(FLAG, "k", true);
    private static final SettingDefinition.IntRangeSetting INT =
            new SettingDefinition.IntRangeSetting(COUNT, "k", 5, 0, 10);
    private static final SettingDefinition.DoubleRangeSetting DOUBLE =
            new SettingDefinition.DoubleRangeSetting(RATIO, "k", 0.5, 0.0, 1.0);
    private static final SettingDefinition.EnumSetting<Choice> ENUM =
            new SettingDefinition.EnumSetting<>(CHOICE, "k", Choice.LOW, Choice.class);

    private void useTestSchema() {
        ModuleSettingsSchema.register(Module.WANDS, List.of(BOOL, INT, DOUBLE, ENUM));
    }

    @AfterEach
    void dropTestSchema() {
        ModuleSettingsSchema.clear(Module.WANDS);
    }

    // ── states ──

    @Test
    void onlyEnabledAndPreviewGrantAccess() {
        assertTrue(ModuleState.ENABLED.grantsAccess());
        assertTrue(ModuleState.PREVIEW.grantsAccess());
        assertFalse(ModuleState.DISABLED.grantsAccess());
        assertFalse(ModuleState.COMING_SOON.grantsAccess(),
                "COMING_SOON must gate exactly like DISABLED so adding it changed no call site");
    }

    @Test
    void comingSoonIsTheOnlyStateOperatorsCannotSet() {
        assertTrue(ModuleState.DISABLED.isOperatorSettable());
        assertTrue(ModuleState.ENABLED.isOperatorSettable());
        assertTrue(ModuleState.PREVIEW.isOperatorSettable());
        assertFalse(ModuleState.COMING_SOON.isOperatorSettable());
    }

    @Test
    void statesParseFromBothSpellings() {
        assertEquals(ModuleState.COMING_SOON, ModuleState.parse("coming_soon"));
        assertEquals(ModuleState.COMING_SOON, ModuleState.parse("COMING_SOON"));
        assertEquals(ModuleState.ENABLED, ModuleState.parse(" enabled "));
        assertNull(ModuleState.parse("nonsense"));
    }

    // ── ids ──

    @Test
    void everyModuleHasAStableRoundTrippingId() {
        for (Module module : Module.values()) {
            Identifier id = ModuleIds.of(module);
            assertNotNull(id, module.name());
            assertEquals("wizards_and_beasts", id.getNamespace());
            assertEquals(module, ModuleIds.byId(id));
            assertEquals(module, ModuleIds.parse(id.getPath()), "bare path must resolve");
            assertEquals(module, ModuleIds.parse(id.toString()), "full id must resolve");
        }
        assertNull(ModuleIds.parse("wizards_and_beasts:not_a_module"));
        assertNull(ModuleIds.parse(""));
    }

    // ── defaults are preserved verbatim ──

    @Test
    void shippedDefaultsMatchThePreRefactorTable() {
        // These are the exact values the old ModuleManager static initialiser held. If this test fails,
        // the refactor moved a gate.
        assertEquals(ModuleState.ENABLED, ModuleDefaults.shipped(Module.WANDS));
        assertEquals(ModuleState.ENABLED, ModuleDefaults.shipped(Module.WANDS_AND_SPELLS));
        assertEquals(ModuleState.ENABLED, ModuleDefaults.shipped(Module.SKILL_TREES));
        assertEquals(ModuleState.PREVIEW, ModuleDefaults.shipped(Module.PROFICIENCY));
        assertEquals(ModuleState.DISABLED, ModuleDefaults.shipped(Module.DARK_ARTS));
        assertEquals(ModuleState.PREVIEW, ModuleDefaults.shipped(Module.PLAYER_ABILITIES));
        assertEquals(ModuleState.PREVIEW, ModuleDefaults.shipped(Module.CREATURES));
        assertEquals(ModuleState.PREVIEW, ModuleDefaults.shipped(Module.BESTIARY));
        assertEquals(ModuleState.ENABLED, ModuleDefaults.shipped(Module.BROOM_FLIGHT));
        assertEquals(ModuleState.ENABLED, ModuleDefaults.shipped(Module.POCKET_DIMENSIONS));
        assertEquals(ModuleState.PREVIEW, ModuleDefaults.shipped(Module.OWLS));
        assertEquals(ModuleState.DISABLED, ModuleDefaults.shipped(Module.AZKABAN));
        // Deliberate divergence from the pre-refactor table: Floo is complete and craftable end to end
        // (fireplace, grate, powder), so it now ships ENABLED. While it was DISABLED those recipes led
        // nowhere for any player without an operator to flip the module.
        assertEquals(ModuleState.ENABLED, ModuleDefaults.shipped(Module.FLOO_NETWORK));
        assertEquals(ModuleState.ENABLED, ModuleDefaults.shipped(Module.CHARACTER_SHEET));
        assertEquals(ModuleState.PREVIEW, ModuleDefaults.shipped(Module.PLAYER_STATS));
        assertEquals(ModuleState.ENABLED, ModuleDefaults.shipped(Module.STRUCTURES));
        assertEquals(ModuleState.ENABLED, ModuleDefaults.shipped(Module.HANDBOOK));
        assertEquals(ModuleState.DISABLED, ModuleDefaults.shipped(Module.CHAMBER_OF_SECRETS));
        // Absent from the old table, so it resolved through getOrDefault to DISABLED.
        assertEquals(ModuleState.DISABLED, ModuleDefaults.shipped(Module.MINISTRY));
    }

    @Test
    void everyModuleHasADefault() {
        for (Module module : Module.values()) {
            assertNotNull(ModuleDefaults.shipped(module), module.name());
        }
    }

    // ── settings framework ──

    @Test
    void unsetSettingsReadAsTheirDefault() {
        useTestSchema();
        ModuleSettingsValues values = ModuleSettingsValues.EMPTY;
        assertEquals(true, values.get(BOOL));
        assertEquals(5, values.get(INT));
        assertEquals(0.5, values.get(DOUBLE), 1e-9);
        assertEquals(Choice.LOW, values.get(ENUM));
    }

    @Test
    void valuesRoundTripThroughTheirOwnCodec() {
        useTestSchema();
        ModuleSettingsValues values = ModuleSettingsValues.EMPTY
                .with(BOOL, false)
                .with(INT, 7)
                .with(DOUBLE, 0.25)
                .with(ENUM, Choice.HIGH);

        JsonElement encoded = ModuleSettingsValues.CODEC.encodeStart(JsonOps.INSTANCE, values)
                .getOrThrow(msg -> new AssertionError(msg));
        ModuleSettingsValues restored = ModuleSettingsValues.CODEC
                .parse(JsonOps.INSTANCE, GSON.fromJson(GSON.toJson(encoded), JsonElement.class))
                .getOrThrow(msg -> new AssertionError(msg));

        assertEquals(false, restored.get(BOOL));
        assertEquals(7, restored.get(INT));
        assertEquals(0.25, restored.get(DOUBLE), 1e-9);
        assertEquals(Choice.HIGH, restored.get(ENUM));
    }

    @Test
    void outOfRangeValuesAreClamped() {
        useTestSchema();
        assertEquals(10, ModuleSettingsValues.EMPTY.with(INT, 999).get(INT));
        assertEquals(0, ModuleSettingsValues.EMPTY.with(INT, -50).get(INT));
        assertEquals(1.0, ModuleSettingsValues.EMPTY.with(DOUBLE, 9.9).get(DOUBLE), 1e-9);
        assertEquals(0.0, ModuleSettingsValues.EMPTY.with(DOUBLE, -9.9).get(DOUBLE), 1e-9);
    }

    @Test
    void validationDropsKeysTheSchemaDoesNotDefine() {
        useTestSchema();
        ModuleSettingsValues withUnknown = ModuleSettingsValues.EMPTY.with(BOOL, false);
        ModuleSettingsSchema emptySchema = ModuleSettingsSchema.of(Module.BESTIARY); // no schema registered

        ModuleSettingsValues cleaned = withUnknown.validated(emptySchema);
        assertTrue(cleaned.isEmpty(), "a value with no definition must not survive load");
    }

    @Test
    void validationClampsStoredOutOfRangeValues() {
        useTestSchema();
        // Simulates a hand-edited save: encode an out-of-range value directly, bypassing with()'s clamp.
        JsonElement raw = GSON.fromJson("{\"wizards_and_beasts:test_count\": 9999}", JsonElement.class);
        ModuleSettingsValues stored = ModuleSettingsValues.CODEC.parse(JsonOps.INSTANCE, raw)
                .getOrThrow(msg -> new AssertionError(msg));

        assertEquals(10, stored.validated(ModuleSettingsSchema.of(Module.WANDS)).get(INT));
    }

    @Test
    void everyModuleHasAnEmptySchemaByDefault() {
        for (Module module : Module.values()) {
            assertTrue(ModuleSettingsSchema.of(module).isEmpty(),
                    module.name() + " ships no settings in this prompt");
        }
    }

    @Test
    void aSchemaExposesItsDefinitionsInDeclarationOrder() {
        useTestSchema();
        ModuleSettingsSchema schema = ModuleSettingsSchema.of(Module.WANDS);
        assertEquals(4, schema.definitions().size());
        assertSame(BOOL, schema.definitions().get(0));
        assertSame(ENUM, schema.definitions().get(3));
        assertTrue(schema.contains(FLAG));
        assertNull(schema.get(Identifier.fromNamespaceAndPath("wizards_and_beasts", "absent")));
    }
}
