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
    /**
     * The player pose layer: procedural and keyframe passes that pose the vanilla player model
     * from live gameplay state. Gating suppresses the layer, never its registration — a disabled
     * module leaves every pass registered and simply runs none of them.
     */
    PLAYER_ANIMATION,
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
     * with no reachable content, which is why it ships {@code DISABLED}. Generation is gated on this
     * flag by {@code ChamberOfSecretsStructure}, so a default install places no chamber at all.
     */
    CHAMBER_OF_SECRETS,
    /**
     * Heritage: the first-join selection ceremony and the visible expression of what a player is.
     *
     * <p>Added last on purpose, so anything keyed on this enum's ordering keeps its numbering.
     *
     * <p>Heritage was the one subsystem with no module at all, which meant the mod's own front door
     * could not be switched off, could not be marked {@code PREVIEW}, and therefore presented itself
     * as finished. Only three of the ten heritages are alpha-available, so {@code PREVIEW} is the
     * honest state.
     *
     * <p><b>Gates access, never registration</b>, and deliberately gates only two things: whether the
     * first-join ceremony is offered, and whether the appearance layer draws. Stats, size profiles
     * and forms stay ungated on purpose — a player who committed a heritage should not silently lose
     * their body and their stat spread because a server operator turned a flag off mid-save.
     */
    HERITAGE
}
