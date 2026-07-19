package at.koopro.wizardsandbeasts.module;

/**
 * Feature modules gated by {@link ModuleManager}.
 */
public enum Module {
    WANDS,
    WANDS_AND_SPELLS,
    SKILL_TREES,
    PROFICIENCY,
    DARK_ARTS,
    PLAYER_ABILITIES,
    CREATURES,
    BESTIARY,
    BROOM_FLIGHT,
    POCKET_DIMENSIONS,
    OWLS,
    FLOO_NETWORK,
    AZKABAN,
    CHARACTER_SHEET,
    PLAYER_STATS,
    /** Decorative location blocks (Hogwarts, Hogsmeade, Diagon Alley, Gringotts, Ministry) and their crafting recipes. */
    STRUCTURES,
    /** Ministry of Magic handbook item + datapack-driven chapter/page GUI. */
    HANDBOOK,
    /**
     * Chamber of Secrets structure + basilisk dark-breeding ritual. Mirrors {@code Module.AZKABAN}'s
     * caution: the structure's start_pool currently points at a near-empty placeholder NBT template
     * with no reachable content, so this is documentation/config surface only for now — the raw
     * vanilla jigsaw structure generation itself is data-driven and is not actually gated by this flag
     * (no bespoke Java {@code Structure} subclass exists to consult it, unlike Azkaban's).
     */
    CHAMBER_OF_SECRETS
}
