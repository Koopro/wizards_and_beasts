package at.koopro.wizardsandbeasts.module;

import at.koopro.wizardsandbeasts.admin.AdminLangKeys;
import at.koopro.wizardsandbeasts.admin.config.ApplyMode;
import at.koopro.wizardsandbeasts.admin.module.ModuleAdminInfo;
import at.koopro.wizardsandbeasts.module.profile.ModuleProfile;
import at.koopro.wizardsandbeasts.module.profile.ModuleProfilePlanner;
import at.koopro.wizardsandbeasts.network.module.ModuleStateSyncPayload;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The module dependency graph, the profile planner, the module sync codec and the text the Modules page needs. */
class ModuleDependenciesTest {

    private static Map<Module, ModuleState> all(ModuleState state) {
        Map<Module, ModuleState> out = new EnumMap<>(Module.class);
        for (Module module : Module.values()) {
            out.put(module, state);
        }
        return out;
    }

    @Test
    void enablingWithoutARequiredModuleIsRefused() {
        Map<Module, ModuleState> states = all(ModuleState.ENABLED);
        states.put(Module.PLAYER_ABILITIES, ModuleState.DISABLED);
        states.put(Module.APPARITION, ModuleState.DISABLED);
        ModuleDependencies.Check check = ModuleDependencies.check(states, Module.APPARITION, ModuleState.ENABLED);
        assertFalse(check.allowed());
        assertEquals(Module.PLAYER_ABILITIES, check.blockedBy().get(0).dependency());
        assertFalse(ModuleDependencies.check(states, Module.APPARITION, ModuleState.PREVIEW).allowed(),
                "preview is reachable too, so it needs its dependency just the same");
        assertTrue(ModuleDependencies.check(states, Module.APPARITION, ModuleState.DISABLED).allowed());
    }

    @Test
    void disablingARequiredModuleCascadesAndSaysSo() {
        Map<Module, ModuleState> states = all(ModuleState.ENABLED);
        ModuleDependencies.Check check = ModuleDependencies.check(states, Module.PLAYER_ABILITIES, ModuleState.DISABLED);
        assertTrue(check.allowed());
        assertEquals(List.of(Module.APPARITION), check.cascade());
        assertTrue(check.hasConsequences());

        states.put(Module.APPARITION, ModuleState.DISABLED);
        assertTrue(ModuleDependencies.check(states, Module.PLAYER_ABILITIES, ModuleState.DISABLED).cascade().isEmpty(),
                "a dependant that is already off is not taken along");
        assertTrue(ModuleDependencies.check(all(ModuleState.ENABLED), Module.PLAYER_ABILITIES, ModuleState.PREVIEW)
                .cascade().isEmpty(), "preview still grants access, so nothing breaks");
    }

    @Test
    void partialDependenciesWarnButNeverBlockOrCascade() {
        Map<Module, ModuleState> states = all(ModuleState.ENABLED);
        ModuleDependencies.Check closing = ModuleDependencies.check(states, Module.GRINGOTTS, ModuleState.DISABLED);
        assertTrue(closing.allowed());
        assertTrue(closing.cascade().isEmpty());
        assertEquals(Module.MINISTRY, closing.weakened().get(0).dependent());

        states.put(Module.GRINGOTTS, ModuleState.DISABLED);
        states.put(Module.MINISTRY, ModuleState.DISABLED);
        ModuleDependencies.Check opening = ModuleDependencies.check(states, Module.MINISTRY, ModuleState.ENABLED);
        assertTrue(opening.allowed());
        assertEquals(Module.GRINGOTTS, opening.weakened().get(0).dependency());
    }

    @Test
    void theRequiresGraphIsAcyclicAndWellFormed() {
        Set<String> seen = new HashSet<>();
        for (ModuleDependencies.Edge edge : ModuleDependencies.all()) {
            assertFalse(edge.dependent() == edge.dependency(), "self edge " + edge);
            assertTrue(seen.add(edge.dependent() + ">" + edge.dependency()), "duplicate edge " + edge);
            assertFalse(edge.evidence().isBlank(), "an edge must cite the gate that enforces it: " + edge);
        }
        for (Module start : Module.values()) {
            assertFalse(reaches(start, start, new HashSet<>()), "REQUIRES cycle through " + start);
        }
    }

    private static boolean reaches(Module from, Module target, Set<Module> visited) {
        for (ModuleDependencies.Edge edge : ModuleDependencies.dependenciesOf(from)) {
            if (edge.kind() != ModuleDependencies.Kind.REQUIRES) {
                continue;
            }
            if (edge.dependency() == target) {
                return true;
            }
            if (visited.add(edge.dependency()) && reaches(edge.dependency(), target, visited)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void theProfilePlannerOrdersChangesSafely() {
        ModuleProfilePlanner.Plan opening = ModuleProfilePlanner.plan(all(ModuleState.DISABLED),
                Map.of(Module.APPARITION, ModuleState.ENABLED, Module.PLAYER_ABILITIES, ModuleState.ENABLED));
        assertEquals(List.of(Module.PLAYER_ABILITIES, Module.APPARITION),
                opening.steps().stream().map(ModuleProfilePlanner.Step::module).toList(), "dependencies open first");
        assertTrue(opening.applicable());

        ModuleProfilePlanner.Plan closing = ModuleProfilePlanner.plan(all(ModuleState.ENABLED),
                Map.of(Module.PLAYER_ABILITIES, ModuleState.DISABLED, Module.APPARITION, ModuleState.DISABLED));
        assertEquals(List.of(Module.APPARITION, Module.PLAYER_ABILITIES),
                closing.steps().stream().map(ModuleProfilePlanner.Step::module).toList(), "dependants close first");

        ModuleProfilePlanner.Plan broken = ModuleProfilePlanner.plan(all(ModuleState.ENABLED),
                Map.of(Module.PLAYER_ABILITIES, ModuleState.DISABLED));
        assertFalse(broken.applicable(), "a profile that leaves Apparition without its base is refused, not applied");

        Map<Module, ModuleState> roadmap = all(ModuleState.ENABLED);
        roadmap.put(Module.OWLS, ModuleState.COMING_SOON);
        ModuleProfilePlanner.Plan locked = ModuleProfilePlanner.plan(roadmap, Map.of(Module.OWLS, ModuleState.ENABLED));
        assertEquals(List.of(Module.OWLS), locked.locked());
        assertTrue(locked.steps().isEmpty());
    }

    @Test
    void onlyTheDefaultProfileIsDefinedAndItIsTheShippedStates() {
        for (ModuleProfile profile : ModuleProfile.values()) {
            assertEquals(profile == ModuleProfile.DEFAULT, profile.defined(), profile.id());
        }
        Map<Module, ModuleState> states = ModuleProfile.DEFAULT.states();
        assertEquals(Module.values().length, states.size());
        for (Module module : Module.values()) {
            assertEquals(ModuleDefaults.shipped(module), states.get(module), module.name());
        }
        assertTrue(ModuleProfilePlanner.plan(ModuleProfile.DEFAULT.states(), ModuleProfile.DEFAULT.states()).applicable(),
                "the shipped states must themselves respect every REQUIRES edge");
    }

    @Test
    void theSyncCarriesEveryModule() {
        Map<Identifier, ModuleState> states = new LinkedHashMap<>();
        for (Module module : Module.values()) {
            states.put(ModuleIds.of(module), ModuleState.values()[module.ordinal() % ModuleState.values().length]);
        }
        ByteBuf buf = Unpooled.buffer();
        ModuleStateSyncPayload.STREAM_CODEC.encode(buf, new ModuleStateSyncPayload(states, Map.of()));
        Map<Module, ModuleState> resolved = ModuleStateSyncPayload.STREAM_CODEC.decode(buf).resolvedStates();
        assertEquals(Module.values().length, resolved.size());
        for (Module module : Module.values()) {
            assertEquals(states.get(ModuleIds.of(module)), resolved.get(module), module.name());
        }
    }

    @Test
    void everyModuleHasItsSettingApplyModeAndText() throws IOException {
        Set<String> keys = new HashSet<>();
        for (String file : List.of("src/main/resources/assets/wizards_and_beasts/lang/en_us.json",
                "src/generated/resources/assets/wizards_and_beasts/lang/en_us.json")) {
            try (Reader reader = Files.newBufferedReader(Path.of(file))) {
                keys.addAll(JsonParser.parseReader(reader).getAsJsonObject().keySet());
            }
        }
        Set<String> missing = new TreeSet<>();
        for (Module module : Module.values()) {
            String path = ModuleAdminInfo.settingId(module).getPath();
            assertEquals("module_" + module.name().toLowerCase(java.util.Locale.ROOT), path);
            String name = "module.wizards_and_beasts." + ModuleIds.of(module).getPath() + ".name";
            for (String key : List.of(name, AdminLangKeys.settingName(path), AdminLangKeys.settingDescription(path),
                    AdminLangKeys.settingWarning(path))) {
                if (!keys.contains(key)) {
                    missing.add(key);
                }
            }
            boolean worldgen = module == Module.AZKABAN || module == Module.CHAMBER_OF_SECRETS;
            assertEquals(worldgen ? ApplyMode.NEW_CHUNKS : ApplyMode.RUNTIME, ModuleAdminInfo.applyMode(module), module.name());
        }
        for (ModuleDependencies.Edge edge : ModuleDependencies.all()) {
            for (String key : List.of(edge.blockedKey(), edge.effectKey())) {
                if (!keys.contains(key)) {
                    missing.add(key);
                }
            }
        }
        for (ModuleProfile profile : ModuleProfile.values()) {
            for (String key : List.of(profile.nameKey(), profile.descriptionKey())) {
                if (!keys.contains(key)) {
                    missing.add(key);
                }
            }
        }
        for (ModuleState state : ModuleState.values()) {
            if (!keys.contains("module.wizards_and_beasts.state." + state.getSerializedName())) {
                missing.add(state.getSerializedName());
            }
        }
        assertTrue(missing.isEmpty(), () -> "module lang keys missing:\n  " + String.join("\n  ", missing));
    }
}
