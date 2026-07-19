package at.koopro.wizardsandbeasts.module;

import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.Map;

/**
 * The state each module starts in, and where that answer comes from.
 *
 * <p>{@link #shipped(Module)} is the value baked into the build — these are exactly the states that used to
 * live in {@code ModuleManager}'s static initialiser. {@link #configuredDefault(Module)} is what a fresh
 * world is seeded with: the config's override if the server operator set one, otherwise the shipped value.
 *
 * <p>Consulted only when seeding a world that has no stored state. Editing the config afterwards does not
 * reach back into worlds that already exist — those are edited with {@code /wandb module set}.
 */
@NullMarked
public final class ModuleDefaults {

    private static final Map<Module, ModuleState> SHIPPED = new EnumMap<>(Module.class);

    static {
        SHIPPED.put(Module.WANDS, ModuleState.ENABLED);
        SHIPPED.put(Module.WANDS_AND_SPELLS, ModuleState.ENABLED);
        SHIPPED.put(Module.SKILL_TREES, ModuleState.ENABLED);
        SHIPPED.put(Module.PROFICIENCY, ModuleState.PREVIEW);
        SHIPPED.put(Module.DARK_ARTS, ModuleState.DISABLED);
        SHIPPED.put(Module.PLAYER_ABILITIES, ModuleState.PREVIEW);
        // Generic data-driven creatures (placeholder GeckoLib mobs). PREVIEW = registered + summonable
        // for verification; access (summon command / spawn-egg) is gated on this, registration is not.
        SHIPPED.put(Module.CREATURES, ModuleState.PREVIEW);
        SHIPPED.put(Module.BESTIARY, ModuleState.PREVIEW);
        SHIPPED.put(Module.BROOM_FLIGHT, ModuleState.ENABLED);
        SHIPPED.put(Module.POCKET_DIMENSIONS, ModuleState.ENABLED);
        SHIPPED.put(Module.OWLS, ModuleState.PREVIEW);
        // Azkaban worldgen (crag + NBT-template fortress) is a Beta v0.3 feature. DISABLED until the real
        // fortress NBT + Dementor spawn table + loot land: the shipped template is a near-empty 225-byte
        // placeholder with no reachable content (see ALPHA_IMPROVEMENT_AUDIT.md §7.1).
        // NOTE: COMING_SOON would arguably fit better, but that would take it out of an operator's reach —
        // a behaviour change this prompt does not authorise. Left exactly as it shipped.
        SHIPPED.put(Module.AZKABAN, ModuleState.DISABLED);
        SHIPPED.put(Module.FLOO_NETWORK, ModuleState.DISABLED);
        SHIPPED.put(Module.CHARACTER_SHEET, ModuleState.ENABLED);
        SHIPPED.put(Module.PLAYER_STATS, ModuleState.PREVIEW);
        // Location decorative blocks ship craftable by default; set DISABLED to hide their recipes.
        SHIPPED.put(Module.STRUCTURES, ModuleState.ENABLED);
        SHIPPED.put(Module.HANDBOOK, ModuleState.ENABLED);
        // Same placeholder-template caution as AZKABAN; likewise left DISABLED rather than COMING_SOON.
        SHIPPED.put(Module.CHAMBER_OF_SECRETS, ModuleState.DISABLED);
        // MINISTRY was never present in the old static table, so it resolved through getOrDefault to
        // DISABLED. Preserved exactly — see MIGRATION_DELTAS.md, this means the Ministry system is off
        // until an operator turns it on.
        SHIPPED.put(Module.MINISTRY, ModuleState.DISABLED);
    }

    private ModuleDefaults() {}

    /** The build's own default. Never null — a module missing from the table is treated as DISABLED. */
    public static ModuleState shipped(Module module) {
        return SHIPPED.getOrDefault(module, ModuleState.DISABLED);
    }

    /** What a fresh world is seeded with: the config override if present, else {@link #shipped}. */
    public static ModuleState configuredDefault(Module module) {
        return ModuleConfig.defaultStateFor(module);
    }
}
