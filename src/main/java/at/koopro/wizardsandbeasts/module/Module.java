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
     * Ministry of Magic law enforcement: the Trace on illegal magic, notoriety and criminal records,
     * Auror dispatch, Azkaban sentencing, the licence/registration registry and Ministry ranks.
     * Disabling it makes magic legal again — nothing is detected, no one is sent, no sentence ticks.
     */
    MINISTRY,
    /**
     * Gringotts: the wizarding currency (Galleon/Sickle/Knut and the forgeries), the player's vault
     * balance and the vault commands. The decorative Gringotts <em>blocks</em> are not here — those are
     * a build set and stay under {@link #STRUCTURES}.
     */
    GRINGOTTS,
    /**
     * The four wandwood trees (elder, holly, rowan, yew) and every block cut from them. Owns the trees
     * themselves, not the wands: {@link #WANDS} is the wandmaking bench and the finished wand.
     */
    WANDWOOD,
    /**
     * Magizoological materials: beast drops, magical herbs and the potion brew. Distinct from
     * {@link #CREATURES}, which owns the beasts; this owns what they leave behind, because the
     * ingredients feed wandmaking and brewing whether or not the creature roster is switched on.
     */
    MAGIZOOLOGY,
    /** Wizarding food and drink — Butterbeer, Chocolate Frogs, Every Flavour Beans and the rest. */
    WIZARDING_FOOD,
    /**
     * Portable magical gear that is not a wand, broom or Dark artefact: the Marauder's Map, Remembrall,
     * Omnioculars, Time-Turner, Portkey, the cloaks and the Deluminator with its paired light blocks.
     */
    ARTEFACTS,
    /** Wizarding furniture and décor: house banners, cauldrons, floating candles, magical plants. */
    FURNISHINGS,
    /** Study materials — parchment, ink and the lore tomes that grant KNOWLEDGE / OWL credit. */
    SCHOLARSHIP,
    /**
     * Chamber of Secrets structure + basilisk dark-breeding ritual. Mirrors {@code Module.AZKABAN}'s
     * caution: the structure's start_pool currently points at a near-empty placeholder NBT template
     * with no reachable content, so this is documentation/config surface only for now — the raw
     * vanilla jigsaw structure generation itself is data-driven and is not actually gated by this flag
     * (no bespoke Java {@code Structure} subclass exists to consult it, unlike Azkaban's).
     */
    CHAMBER_OF_SECRETS
}
