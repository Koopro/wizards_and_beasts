package at.koopro.wizardsandbeasts.ability;

import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import at.koopro.wizardsandbeasts.ability.def.AbilityDefinitionRegistry;
import at.koopro.wizardsandbeasts.ability.grant.AbilityGrantService;
import at.koopro.wizardsandbeasts.ability.grant.AbilityGrants;
import at.koopro.wizardsandbeasts.ability.grant.AbilityKey;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Server-authoritative resolution of which registered {@link AbilityDefinition}s a player may actually
 * see/use. A definition is <b>usable</b> iff it is (1) present in the registry, (2) granted to the player by
 * some {@link AbilityGrantSource} (mapped by {@link AbilityKey}), and (3) not gated by a module that is
 * disabled. Registration/granting is never blocked by module state — only visibility/usability is (§2.2, §4).
 *
 * <p>This layer intentionally reuses the shipped derived grant layer ({@link AbilityGrantService}) as the
 * resolver rather than introducing a parallel one.
 */
@NullMarked
public final class AbilityResolver {

    /** {@code module} Identifier path → {@link Module}; the path is the lowercased enum name. */
    private static final Map<String, Module> MODULE_BY_PATH = buildModuleIndex();

    private AbilityResolver() {}

    /** The player's full derived grant snapshot (all sources, all keys — not module filtered). */
    public static AbilityGrants grants(ServerPlayer player) {
        return AbilityGrantService.compute(player);
    }

    /** True if the player holds a grant matching {@code def}'s id (regardless of module/type). */
    public static boolean isGranted(ServerPlayer player, AbilityDefinition def) {
        return grants(player).has(keyOf(def));
    }

    /** True if {@code def}'s module gate permits use (no module = always; unknown module = denied). */
    public static boolean moduleAllows(AbilityDefinition def) {
        Identifier module = def.module();
        if (module == null) {
            return true;
        }
        Module resolved = MODULE_BY_PATH.get(module.getPath().toLowerCase(Locale.ROOT));
        return resolved != null && ModuleManager.isEnabled(resolved);
    }

    /** Usable = granted AND module-permitted. Does not consider type. */
    public static boolean isUsable(ServerPlayer player, AbilityDefinition def) {
        return moduleAllows(def) && isGranted(player, def);
    }

    /**
     * Wheel-eligible (ACTIVE/TOGGLE) definitions the player may use, in stable wheel order. This is exactly
     * the set the wheel renders and the trigger layer accepts.
     */
    public static List<AbilityDefinition> usableWheel(ServerPlayer player) {
        AbilityGrants grants = grants(player);
        List<AbilityDefinition> out = new ArrayList<>();
        for (AbilityDefinition def : AbilityDefinitionRegistry.wheelEligibleSorted()) {
            if (moduleAllows(def) && grants.has(keyOf(def))) {
                out.add(def);
            }
        }
        return out;
    }

    /** The ids of {@link #usableWheel} — the unit the wheel-state sync transmits. */
    public static List<Identifier> usableWheelIds(ServerPlayer player) {
        List<AbilityDefinition> defs = usableWheel(player);
        List<Identifier> ids = new ArrayList<>(defs.size());
        for (AbilityDefinition def : defs) {
            ids.add(def.id());
        }
        return ids;
    }

    /** Whether a specific ability id is currently usable by the player. */
    public static boolean isUsable(ServerPlayer player, Identifier abilityId) {
        AbilityDefinition def = AbilityDefinitionRegistry.get(abilityId);
        return def != null && isUsable(player, def);
    }

    @Nullable
    public static AbilityDefinition definition(Identifier abilityId) {
        return AbilityDefinitionRegistry.get(abilityId);
    }

    private static AbilityKey keyOf(AbilityDefinition def) {
        return AbilityKey.of(def.id().toString());
    }

    private static Map<String, Module> buildModuleIndex() {
        Map<String, Module> map = new java.util.HashMap<>();
        for (Module module : Module.values()) {
            map.put(module.name().toLowerCase(Locale.ROOT), module);
        }
        return Map.copyOf(map);
    }
}
