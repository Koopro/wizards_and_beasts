package at.koopro.wizardsandbeasts.admin.profile;

import at.koopro.wizardsandbeasts.admin.config.ApplyMode;
import at.koopro.wizardsandbeasts.admin.module.ModuleAdminInfo;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleState;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The profile file format and the validator, headless: malformed and hostile files, versions, values, dependencies. */
class ProfileCodecValidatorTest {

    private static final String NS = "wizards_and_beasts:";

    private static String file(String settings, String modules, int version) {
        return "{\"schema\":\"" + ProfileCodec.SCHEMA_ID + "\",\"schema_version\":" + version
                + ",\"mod_version\":\"test\",\"profile\":{\"id\":\"t\",\"name\":\"Test\",\"created\":1},"
                + "\"mode\":\"replace\",\"settings\":{" + settings + "},\"modules\":{" + modules + "}}";
    }

    // ── codec ──

    @Test
    void roundTripsExactly() {
        ProfileDocument doc = new ProfileDocument(1, "0.1", new ProfileDocument.Meta("hardcore", "Hardcore", "d", "me", 5L,
                ProfileDocument.Kind.CUSTOM), ProfileDocument.Mode.MERGE, Map.of(NS + "spell_damage_multiplier", "1.5"),
                Map.of("dark_arts", "enabled"));
        ProfileCodec.Parsed parsed = ProfileCodec.parse(ProfileCodec.write(doc));
        assertTrue(parsed.ok(), parsed.errors().toString());
        assertEquals(doc, parsed.document());
    }

    @Test
    void malformedAndHostileFilesAreReportedNeverThrown() {
        List<String> bad = List.of("", "{", "[]", "null", "\"text\"", "{\"schema\":\"something_else\"}",
                "{\"schema\":\"" + ProfileCodec.SCHEMA_ID + "\"}",
                "{\"schema\":\"" + ProfileCodec.SCHEMA_ID + "\",\"schema_version\":\"one\"}",
                file("\"x\":{\"nested\":1}", "", 1),
                file("", "", 1).replace("\"id\":\"t\"", "\"id\":\"../../etc\""),
                file("", "", 1).replace("\"mode\":\"replace\"", "\"mode\":\"overwrite_everything\""),
                "[".repeat(100_000));
        for (String text : bad) {
            ProfileCodec.Parsed parsed = ProfileCodec.parse(text);
            assertFalse(parsed.ok(), () -> "accepted: " + text.substring(0, Math.min(80, text.length())));
            assertFalse(parsed.errors().isEmpty());
        }
        String huge = file("\"" + NS + "a\":\"" + "x".repeat(ProfileCodec.MAX_BYTES) + "\"", "", 1);
        assertEquals("too_large", ProfileCodec.parse(huge).errors().get(0).code());
    }

    @Test
    void incompatibleVersionsAreRefusedByName() {
        for (int version : new int[] {0, ProfileCodec.CURRENT_VERSION + 1, 99}) {
            ProfileCodec.Parsed parsed = ProfileCodec.parse(file("", "", version));
            assertEquals("incompatible_version", parsed.errors().get(0).code(), "version " + version);
        }
        assertTrue(ProfileCodec.parse(file("", "", ProfileCodec.CURRENT_VERSION)).ok());
    }

    @Test
    void unknownTopLevelFieldsWarn() {
        ProfileCodec.Parsed parsed = ProfileCodec.parse(file("", "", 1).replace("\"mode\"", "\"future_field\":1,\"mode\""));
        assertTrue(parsed.ok());
        assertEquals("unknown_field", parsed.warnings().get(0).code());
    }

    @Test
    void idsNamesAndFilesAreBounded() {
        assertTrue(ProfileIds.validId("hogwarts_rp"));
        assertFalse(ProfileIds.validId("Hogwarts RP"));
        assertTrue(ProfileIds.validName("Hogwarts RP (2)"));
        assertFalse(ProfileIds.validName("<b>"));
        // A snapshot taken without a label names itself; that name must pass the same check (no ':').
        assertTrue(ProfileIds.validName(ProfileIds.snapshotName(java.time.LocalDateTime.of(2026, 10, 1, 11, 32, 5))));
        assertTrue(ProfileIds.validFileName("hardcore.json"));
        for (String bad : List.of("../x.json", "a/b.json", "x.txt", "..json", "C:x.json")) {
            assertFalse(ProfileIds.validFileName(bad), bad);
        }
        assertEquals("hogwarts_rp_2", ProfileIds.idFor("Hogwarts RP (2)"));
    }

    // ── validator over a fake server ──

    /** Three numeric settings, one client preference, one module switch per module. */
    private static final class Fake implements ProfileTarget {
        final Map<Identifier, String> current = new LinkedHashMap<>();
        final Map<Identifier, String> defaults = new LinkedHashMap<>();
        final Map<Module, ModuleState> modules = new EnumMap<>(Module.class);
        final Set<Identifier> client = Set.of(Identifier.parse(NS + "hud"));
        Set<Identifier> forbidden = Set.of();

        Fake() {
            for (String path : List.of("spell_damage_multiplier", "brew_failure_multiplier", "skill_respec_cost_knuts")) {
                current.put(Identifier.parse(NS + path), "1");
                defaults.put(Identifier.parse(NS + path), "1");
            }
            current.put(Identifier.parse(NS + "hud"), "true");
            defaults.put(Identifier.parse(NS + "hud"), "true");
            for (Module module : Module.values()) {
                modules.put(module, ModuleState.ENABLED);
                current.put(ModuleAdminInfo.settingId(module), "ENABLED");
                defaults.put(ModuleAdminInfo.settingId(module), "ENABLED");
            }
        }

        @Override
        public List<Identifier> serverSettings() {
            List<Identifier> out = new ArrayList<>(current.keySet());
            out.removeAll(client);
            return out;
        }

        @Override
        public Facts facts(Identifier id) {
            if (!current.containsKey(id)) {
                return null;
            }
            return new Facts(client.contains(id), !forbidden.contains(id), current.get(id), defaults.get(id),
                    id.getPath().equals("brew_failure_multiplier") ? ApplyMode.RESTART : ApplyMode.RUNTIME,
                    LiveProfileTarget.moduleOf(id));
        }

        @Override
        public Check check(Identifier id, String text) {
            if (LiveProfileTarget.moduleOf(id) != null) {
                ModuleState state = ModuleState.parse(text);
                return state == null ? Check.refused("invalid_value") : Check.ok(state.name());
            }
            try {
                double value = Double.parseDouble(text);
                if (value < 0 || value > 10) {
                    return Check.refused("out_of_range");
                }
                return Check.ok(value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value));
            } catch (NumberFormatException e) {
                return Check.refused("invalid_value");
            }
        }

        @Override
        public Map<Module, ModuleState> moduleStates() {
            return modules;
        }
    }

    private static ProfileDocument doc(ProfileDocument.Mode mode, Map<String, String> settings, Map<String, String> modules) {
        return new ProfileDocument(1, "", new ProfileDocument.Meta("t", "T", "", "", 0, ProfileDocument.Kind.CUSTOM),
                mode, settings, modules);
    }

    @Test
    void aValidProfileBecomesExactlyItsChanges() {
        Fake fake = new Fake();
        ProfileValidator.Plan plan = ProfileValidator.validate(doc(ProfileDocument.Mode.MERGE,
                Map.of(NS + "spell_damage_multiplier", "1.5", "brew_failure_multiplier", "1"), Map.of("dark_arts", "disabled")),
                fake, DeprecatedSettings.CURRENT);
        assertTrue(plan.applicable(), plan.errors().toString());
        assertEquals(2, plan.changes().size(), plan.changes().toString());
        assertTrue(plan.changes().stream().anyMatch(c -> c.id().getPath().equals("spell_damage_multiplier")
                && c.from().equals("1") && c.to().equals("1.5")));
        assertTrue(plan.changes().stream().anyMatch(c -> c.module() == Module.DARK_ARTS && c.to().equals("DISABLED")));
    }

    @Test
    void invalidValuesAndUnauthorisedSettingsRefuseTheWholeProfile() {
        Fake fake = new Fake();
        fake.forbidden = Set.of(Identifier.parse(NS + "skill_respec_cost_knuts"));
        ProfileValidator.Plan plan = ProfileValidator.validate(doc(ProfileDocument.Mode.MERGE, Map.of(
                NS + "spell_damage_multiplier", "lots", NS + "brew_failure_multiplier", "99",
                NS + "skill_respec_cost_knuts", "5"), Map.of("dark_arts", "sideways")), fake, DeprecatedSettings.CURRENT);
        assertFalse(plan.applicable());
        List<String> codes = plan.errors().stream().map(ProfileCodec.Issue::code).sorted().toList();
        assertEquals(List.of("invalid_value", "invalid_value", "out_of_range", "unauthorized"), codes);
    }

    @Test
    void unknownClientDeprecatedAndLockedEntriesWarnAndAreSkipped() {
        Fake fake = new Fake();
        fake.modules.put(Module.OWLS, ModuleState.COMING_SOON);
        DeprecatedSettings deprecated = new DeprecatedSettings(Map.of(NS + "old_damage", NS + "spell_damage_multiplier"),
                Set.of(NS + "gone"));
        ProfileValidator.Plan plan = ProfileValidator.validate(doc(ProfileDocument.Mode.MERGE, Map.of(
                NS + "no_such_thing", "1", NS + "hud", "false", NS + "old_damage", "2", NS + "gone", "1"),
                Map.of("owls", "enabled", "not_a_module", "enabled")), fake, deprecated);
        assertTrue(plan.applicable(), plan.errors().toString());
        List<String> codes = plan.warnings().stream().map(ProfileCodec.Issue::code).sorted().toList();
        assertEquals(List.of("client_setting", "deprecated_removed", "deprecated_renamed", "module_locked",
                "unknown_module", "unknown_setting"), codes);
        assertEquals(1, plan.changes().size(), "only the renamed setting changes");
        assertEquals("2", plan.changes().get(0).to());
    }

    @Test
    void replaceModeReturnsUnnamedSettingsToDefault() {
        Fake fake = new Fake();
        fake.current.put(Identifier.parse(NS + "brew_failure_multiplier"), "3");
        ProfileValidator.Plan merge = ProfileValidator.validate(doc(ProfileDocument.Mode.MERGE, Map.of(), Map.of()),
                fake, DeprecatedSettings.CURRENT);
        assertTrue(merge.changes().isEmpty());
        ProfileValidator.Plan replace = ProfileValidator.validate(doc(ProfileDocument.Mode.REPLACE, Map.of(), Map.of()),
                fake, DeprecatedSettings.CURRENT);
        assertEquals(1, replace.changes().size());
        assertEquals(1, replace.resetToDefault());
        assertTrue(replace.needsRestart(), "a restart-bound change is flagged before applying");
    }

    @Test
    void aBrokenModuleDependencyRefusesTheProfile() {
        Fake fake = new Fake();
        ProfileValidator.Plan plan = ProfileValidator.validate(doc(ProfileDocument.Mode.MERGE, Map.of(),
                Map.of("player_abilities", "disabled")), fake, DeprecatedSettings.CURRENT);
        assertFalse(plan.applicable(), "Apparition stays on without Player abilities");
        assertEquals("dependency", plan.errors().get(0).code());
        ProfileValidator.Plan both = ProfileValidator.validate(doc(ProfileDocument.Mode.MERGE, Map.of(),
                Map.of("player_abilities", "disabled", "apparition", "disabled")), fake, DeprecatedSettings.CURRENT);
        assertTrue(both.applicable(), both.errors().toString());
        ProfileValidator.Plan partial = ProfileValidator.validate(doc(ProfileDocument.Mode.MERGE, Map.of(),
                Map.of("gringotts", "disabled")), fake, DeprecatedSettings.CURRENT);
        assertTrue(partial.applicable());
        assertEquals("dependency_partial", partial.warnings().get(0).code());
    }

    @Test
    void theShippedPresetsAreValidFilesInTheCurrentSchema() throws IOException {
        Path folder = Path.of("src", "main", "resources", "data", "wizards_and_beasts", "admin_profiles");
        List<Path> files;
        try (Stream<Path> stream = Files.list(folder)) {
            files = stream.filter(p -> p.toString().endsWith(".json")).toList();
        }
        assertTrue(files.size() >= 5, "default, hogwarts_rp, sandbox, hardcore, developer");
        for (Path file : files) {
            ProfileCodec.Parsed parsed = ProfileCodec.parse(Files.readString(file));
            assertTrue(parsed.ok(), () -> file + ": " + parsed.errors());
            assertNotNull(parsed.document());
            String id = file.getFileName().toString().replace(".json", "");
            assertEquals(id, parsed.document().meta().id(), file.toString());
            assertTrue(parsed.warnings().isEmpty(), () -> file + ": " + parsed.warnings());
        }
    }
}
