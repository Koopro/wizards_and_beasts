package at.koopro.wizardsandbeasts.admin.profile;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.config.ApplyMode;
import at.koopro.wizardsandbeasts.admin.module.ModuleAdminInfo;
import at.koopro.wizardsandbeasts.admin.profile.ProfileCodec.Issue;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleDependencies;
import at.koopro.wizardsandbeasts.module.ModuleIds;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.module.profile.ModuleProfilePlanner;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Judges a profile against the server before anything is touched, and turns it into the exact list of changes
 * applying it would make. Pure over a {@link ProfileTarget}.
 *
 * <p>Errors (any one refuses the whole profile): a value that does not parse or is out of range, a setting the actor
 * may not change, a module state that does not exist, and a module combination that breaks a REQUIRES dependency.
 * Warnings (reported, skipped): unknown settings and modules, deprecated ids, client preferences (not server-owned),
 * and modules marked coming soon. Cross-setting rules ("the last selectable heritage") depend on the order of
 * changes; they are judged during apply, which is all-or-nothing ({@link ProfileApplier}).
 */
@NullMarked
public final class ProfileValidator {

    /** One change applying the profile would make. {@code module} is set for a module switch. */
    public record Change(Identifier id, String from, String to, ApplyMode applyMode, @Nullable Module module) {}

    /**
     * @param resetToDefault how many of the changes come from REPLACE mode returning an unnamed setting to its default
     */
    public record Plan(List<Change> changes, List<Issue> errors, List<Issue> warnings, int resetToDefault) {

        public boolean applicable() {
            return errors.isEmpty();
        }

        public boolean needsRestart() {
            return changes.stream().anyMatch(c -> c.applyMode() == ApplyMode.RESTART);
        }

        public boolean touchesWorldgen() {
            return changes.stream().anyMatch(c -> c.applyMode() == ApplyMode.NEW_CHUNKS);
        }
    }

    private ProfileValidator() {}

    public static Plan validate(ProfileDocument document, ProfileTarget target, DeprecatedSettings deprecated) {
        List<Issue> errors = new ArrayList<>();
        List<Issue> warnings = new ArrayList<>();
        Map<Identifier, String> wanted = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : document.settings().entrySet()) {
            String key = entry.getKey();
            if (deprecated.removed().contains(key)) {
                warnings.add(new Issue("deprecated_removed", key, ""));
                continue;
            }
            String renamed = deprecated.renamed().get(key);
            if (renamed != null) {
                warnings.add(new Issue("deprecated_renamed", key, renamed));
                key = renamed;
            }
            Identifier id = parseId(key);
            if (id == null) {
                warnings.add(new Issue("unknown_setting", key, ""));
                continue;
            }
            want(id, key, entry.getValue(), target, wanted, errors, warnings);
        }

        for (Map.Entry<String, String> entry : document.modules().entrySet()) {
            Module module = ModuleIds.parse(entry.getKey());
            if (module == null) {
                warnings.add(new Issue("unknown_module", entry.getKey(), ""));
                continue;
            }
            ModuleState state = ModuleState.parse(entry.getValue());
            if (state == null) {
                errors.add(new Issue("invalid_value", entry.getKey(), entry.getValue()));
                continue;
            }
            Identifier id = ModuleAdminInfo.settingId(module);
            ModuleState current = target.moduleStates().getOrDefault(module, ModuleState.DISABLED);
            if (!state.isOperatorSettable() || !current.isOperatorSettable()) {
                if (state != current) {
                    warnings.add(new Issue("module_locked", entry.getKey(), state.getSerializedName()));
                }
                continue;
            }
            want(id, entry.getKey(), state.name(), target, wanted, errors, warnings);
        }

        int resetToDefault = 0;
        if (document.mode() == ProfileDocument.Mode.REPLACE) {
            for (Identifier id : target.serverSettings()) {
                if (wanted.containsKey(id)) {
                    continue;
                }
                ProfileTarget.Facts facts = target.facts(id);
                if (facts == null || facts.clientOnly()) {
                    continue;
                }
                if (facts.module() != null && !moduleSettable(target, facts.module(), facts.defaultText())) {
                    continue;
                }
                if (!facts.current().equals(facts.defaultText())) {
                    if (!facts.authorised()) {
                        errors.add(new Issue("unauthorized", id.toString(), ""));
                        continue;
                    }
                    resetToDefault++;
                }
                wanted.put(id, facts.defaultText());
            }
        }

        List<Change> changes = new ArrayList<>();
        Map<Module, ModuleState> finalModules = new EnumMap<>(Module.class);
        finalModules.putAll(target.moduleStates());
        for (Map.Entry<Identifier, String> entry : wanted.entrySet()) {
            ProfileTarget.Facts facts = target.facts(entry.getKey());
            if (facts == null || facts.current().equals(entry.getValue())) {
                continue;
            }
            changes.add(new Change(entry.getKey(), facts.current(), entry.getValue(), facts.applyMode(), facts.module()));
            if (facts.module() != null) {
                ModuleState state = ModuleState.parse(entry.getValue());
                if (state != null) {
                    finalModules.put(facts.module(), state);
                }
            }
        }

        // The finished module combination must respect every dependency.
        ModuleProfilePlanner.Plan modules = ModuleProfilePlanner.plan(target.moduleStates(), finalModules);
        for (ModuleDependencies.Edge edge : modules.broken()) {
            errors.add(new Issue("dependency", edge.blockedKey(), ModuleIds.of(edge.dependent()).getPath()));
        }
        for (ModuleDependencies.Edge edge : ModuleDependencies.all()) {
            if (edge.kind() == ModuleDependencies.Kind.PARTIAL
                    && finalModules.getOrDefault(edge.dependent(), ModuleState.DISABLED).grantsAccess()
                    && !finalModules.getOrDefault(edge.dependency(), ModuleState.DISABLED).grantsAccess()) {
                warnings.add(new Issue("dependency_partial", edge.effectKey(), ModuleIds.of(edge.dependent()).getPath()));
            }
        }
        return new Plan(List.copyOf(changes), List.copyOf(errors), List.copyOf(warnings), resetToDefault);
    }

    private static boolean moduleSettable(ProfileTarget target, Module module, String defaultText) {
        ModuleState current = target.moduleStates().getOrDefault(module, ModuleState.DISABLED);
        ModuleState wanted = ModuleState.parse(defaultText);
        return current.isOperatorSettable() && (wanted == null || wanted.isOperatorSettable());
    }

    private static void want(Identifier id, String key, String text, ProfileTarget target, Map<Identifier, String> wanted,
                             List<Issue> errors, List<Issue> warnings) {
        ProfileTarget.Facts facts = target.facts(id);
        if (facts == null) {
            warnings.add(new Issue("unknown_setting", key, ""));
            return;
        }
        if (facts.clientOnly()) {
            warnings.add(new Issue("client_setting", key, ""));
            return;
        }
        ProfileTarget.Check check = target.check(id, text);
        if (check.canonical() == null) {
            errors.add(new Issue(check.problem() == null ? "invalid_value" : check.problem(), id.toString(), clip(text)));
            return;
        }
        if (!facts.authorised() && !facts.current().equals(check.canonical())) {
            errors.add(new Issue("unauthorized", id.toString(), ""));
            return;
        }
        wanted.put(id, check.canonical());
    }

    /** A bare path means this mod's namespace, as in commands. */
    static @Nullable Identifier parseId(String key) {
        String trimmed = key.trim();
        return trimmed.indexOf(':') >= 0 ? Identifier.tryParse(trimmed)
                : Identifier.tryBuild(WizardsAndBeastsMod.MODID, trimmed);
    }

    private static String clip(String text) {
        return text.length() <= 80 ? text : text.substring(0, 80) + "…";
    }
}
