package at.koopro.wizardsandbeasts.skill;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;

import org.jspecify.annotations.Nullable;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public enum SkillTreeId {
    // Wizard trees — shown to the wizard audience. Region capability requirement gates content WITHIN
    // the web (a sealed region renders locked); it never gates the web itself (audience does that).
    SPELL_MASTERY("spell_mastery", "Spell Mastery", 0xFFAA88FF, Audience.WIZARD, Requirement.CASTING),
    DARK_ARTS("dark_arts", "Dark Arts", 0xFF8B00FF, Audience.WIZARD, Requirement.CASTING),
    MAGIZOOLOGY("magizoology", "Magizoology", 0xFF44CC88, Audience.WIZARD, Requirement.NONE),
    WANDLORE("wandlore", "Wandlore", 0xFFBB8833, Audience.WIZARD, Requirement.WAND),
    HERBOLOGY("herbology", "Herbology", 0xFF44BB44, Audience.WIZARD, Requirement.NONE),
    ALCHEMY("alchemy", "Alchemy", 0xFF66CCCC, Audience.WIZARD, Requirement.NONE),
    // Goblin tree — Gringotts guild craft.
    GOBLIN_CRAFT("goblin_craft", "Guild Ledger", 0xFFD4AF37, Audience.GOBLIN, Requirement.NONE),
    // House-elf tree — bound elf-magic.
    ELF_BOND("elf_bond", "Binding Threads", 0xFFB57EDC, Audience.HOUSE_ELF, Requirement.NONE);

    /**
     * Which heritage group a tree belongs to; drives both UI chart selection and server unlock gating.
     * WIZARD is the tradition-of-origin audience for wizardkind and the lineages that grew out of it
     * (werewolf, obscurial, vampire, half/quarter-veela, half-giant). The remaining peoples own their
     * tradition and get their own audience — several of those webs are not authored yet (guarded, inert).
     */
    public enum Audience { WIZARD, GOBLIN, HOUSE_ELF, VEELA, CENTAUR, MERPEOPLE, GIANT }

    /**
     * Capability a player must hold to allocate inside a region. This is the <b>only</b> gate inside a
     * web; it is data on the closed tree enum (not datapack) deliberately, since the trees are a closed
     * set and the requirement is a fixed property of each region, not content authors tune.
     *
     * <ul>
     *   <li>{@code NONE} — always open (Virgo/Monoceros/Fornax and the goblin/elf trees).</li>
     *   <li>{@code WAND} — needs a usable wand ({@code canUseWand && !no_wand}); Sagitta/wandlore.</li>
     *   <li>{@code CASTING} — needs spellcasting ({@code !no_casting}); Orion/spell_mastery, Serpens/dark_arts.</li>
     * </ul>
     */
    public enum Requirement { NONE, WAND, CASTING }

    /** Serializes as the lowercase tree id (e.g. {@code "spell_mastery"}); unknown ids are a parse error. */
    public static final com.mojang.serialization.Codec<SkillTreeId> CODEC =
            com.mojang.serialization.Codec.STRING.comapFlatMap(
                    raw -> {
                        SkillTreeId tree = byId(raw);
                        return tree != null
                                ? com.mojang.serialization.DataResult.success(tree)
                                : com.mojang.serialization.DataResult.error(() -> "Unknown skill tree id: " + raw);
                    },
                    SkillTreeId::getId);

    private static final Map<String, SkillTreeId> BY_ID = new HashMap<>();

    /**
     * The ruling, verbatim: every (heritage, variant) maps to its tradition-of-origin audience. Keyed on
     * variant because audience can split within a heritage (full-veela vs half-veela, full-giant/clan vs
     * half-giant). No default branch — an unmapped variant is a loud static-init failure, never a silent
     * WIZARD.
     */
    private static final EnumMap<HeritageVariant, Audience> VARIANT_AUDIENCE = new EnumMap<>(HeritageVariant.class);

    /**
     * Degenerate fallback for a heritage with no variant chosen (mid-selection / admin state — normal
     * play always commits both together). Also total across every heritage: no silent default. Ambiguous
     * heritages (veela/giant) fall to their own audience here, never accidentally to WIZARD.
     */
    private static final EnumMap<Heritage, Audience> HERITAGE_FALLBACK = new EnumMap<>(Heritage.class);

    static {
        for (SkillTreeId tree : values()) {
            BY_ID.put(tree.id, tree);
        }

        // ── Variant → audience (the ruling) ──
        // Wizardkind (incl. squib) → WIZARD.
        VARIANT_AUDIENCE.put(HeritageVariant.PURE_BLOOD, Audience.WIZARD);
        VARIANT_AUDIENCE.put(HeritageVariant.HALF_BLOOD, Audience.WIZARD);
        VARIANT_AUDIENCE.put(HeritageVariant.MUGGLE_BORN, Audience.WIZARD);
        VARIANT_AUDIENCE.put(HeritageVariant.SQUIB, Audience.WIZARD);
        VARIANT_AUDIENCE.put(HeritageVariant.ADOPTED_MAGICAL, Audience.WIZARD);
        // Werewolf → WIZARD.
        VARIANT_AUDIENCE.put(HeritageVariant.WEREWOLF_BITTEN, Audience.WIZARD);
        VARIANT_AUDIENCE.put(HeritageVariant.WEREWOLF_BORN, Audience.WIZARD);
        VARIANT_AUDIENCE.put(HeritageVariant.WEREWOLF_SAVAGE, Audience.WIZARD);
        // Obscurial → WIZARD.
        VARIANT_AUDIENCE.put(HeritageVariant.SUPPRESSED, Audience.WIZARD);
        VARIANT_AUDIENCE.put(HeritageVariant.UNLEASHED, Audience.WIZARD);
        // Vampire (all variants) → WIZARD.
        VARIANT_AUDIENCE.put(HeritageVariant.VAMPIRE_TURNED, Audience.WIZARD);
        VARIANT_AUDIENCE.put(HeritageVariant.VAMPIRE_BORN, Audience.WIZARD);
        VARIANT_AUDIENCE.put(HeritageVariant.VAMPIRE_DHAMPIR, Audience.WIZARD);
        // Veela: half/quarter carry the human tradition → WIZARD; full veela owns its tradition → VEELA.
        VARIANT_AUDIENCE.put(HeritageVariant.VEELA_HALF, Audience.WIZARD);
        VARIANT_AUDIENCE.put(HeritageVariant.VEELA_QUARTER, Audience.WIZARD);
        VARIANT_AUDIENCE.put(HeritageVariant.VEELA_FULL, Audience.VEELA);
        // Giant: half-giant is the mixed (human) lineage → WIZARD; full-giant and clan-warden are pure
        // giant lineage (clan_warden's data shows no human parentage — a role within full-giant clans,
        // not a mixed birth) → GIANT.
        VARIANT_AUDIENCE.put(HeritageVariant.GIANT_HALF, Audience.WIZARD);
        VARIANT_AUDIENCE.put(HeritageVariant.GIANT_FULL, Audience.GIANT);
        VARIANT_AUDIENCE.put(HeritageVariant.GIANT_CLAN, Audience.GIANT);
        // Goblin → GOBLIN.
        VARIANT_AUDIENCE.put(HeritageVariant.GOBLIN_COMMON, Audience.GOBLIN);
        VARIANT_AUDIENCE.put(HeritageVariant.GOBLIN_WARRIOR, Audience.GOBLIN);
        VARIANT_AUDIENCE.put(HeritageVariant.GOBLIN_RUNE, Audience.GOBLIN);
        // House-elf → HOUSE_ELF.
        VARIANT_AUDIENCE.put(HeritageVariant.ELF_BOUND, Audience.HOUSE_ELF);
        VARIANT_AUDIENCE.put(HeritageVariant.ELF_FREE, Audience.HOUSE_ELF);
        VARIANT_AUDIENCE.put(HeritageVariant.ELF_HALL, Audience.HOUSE_ELF);
        // Centaur → CENTAUR.
        VARIANT_AUDIENCE.put(HeritageVariant.CENTAUR_FOREST, Audience.CENTAUR);
        VARIANT_AUDIENCE.put(HeritageVariant.CENTAUR_WAR, Audience.CENTAUR);
        VARIANT_AUDIENCE.put(HeritageVariant.CENTAUR_STARGAZER, Audience.CENTAUR);
        // Merpeople → MERPEOPLE.
        VARIANT_AUDIENCE.put(HeritageVariant.MERPEOPLE_MERROW, Audience.MERPEOPLE);
        VARIANT_AUDIENCE.put(HeritageVariant.MERPEOPLE_SELKIE, Audience.MERPEOPLE);
        VARIANT_AUDIENCE.put(HeritageVariant.MERPEOPLE_SIREN, Audience.MERPEOPLE);

        // ── Heritage → audience (null-variant fallback only) ──
        HERITAGE_FALLBACK.put(Heritage.WIZARDKIND, Audience.WIZARD);
        HERITAGE_FALLBACK.put(Heritage.WEREWOLF, Audience.WIZARD);
        HERITAGE_FALLBACK.put(Heritage.OBSCURIAL, Audience.WIZARD);
        HERITAGE_FALLBACK.put(Heritage.VAMPIRE, Audience.WIZARD);
        HERITAGE_FALLBACK.put(Heritage.GOBLIN, Audience.GOBLIN);
        HERITAGE_FALLBACK.put(Heritage.HOUSE_ELF, Audience.HOUSE_ELF);
        HERITAGE_FALLBACK.put(Heritage.VEELA, Audience.VEELA);
        HERITAGE_FALLBACK.put(Heritage.GIANT, Audience.GIANT);
        HERITAGE_FALLBACK.put(Heritage.CENTAUR, Audience.CENTAUR);
        HERITAGE_FALLBACK.put(Heritage.MERPEOPLE, Audience.MERPEOPLE);

        // Totality tripwires: a missing entry is a boot-time crash, never a silent misroute.
        for (HeritageVariant variant : HeritageVariant.values()) {
            if (!VARIANT_AUDIENCE.containsKey(variant)) {
                throw new IllegalStateException("SkillTreeId audience map is missing heritage variant: " + variant);
            }
        }
        for (Heritage heritage : Heritage.values()) {
            if (!HERITAGE_FALLBACK.containsKey(heritage)) {
                throw new IllegalStateException("SkillTreeId audience fallback is missing heritage: " + heritage);
            }
        }
    }

    private final String id;
    private final String displayName;
    private final int color;
    private final Audience audience;
    private final Requirement requirement;

    SkillTreeId(String id, String displayName, int color, Audience audience, Requirement requirement) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.audience = audience;
        this.requirement = requirement;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public int getColor() { return color; }
    public Audience getAudience() { return audience; }
    public Requirement getRequirement() { return requirement; }

    @Nullable
    public static SkillTreeId byId(String id) {
        return BY_ID.get(id);
    }

    /** Audience for a fully-resolved heritage variant. Never null (map totality is asserted at load). */
    public static Audience audienceForVariant(HeritageVariant variant) {
        return VARIANT_AUDIENCE.get(variant);
    }

    /**
     * Tradition-of-origin audience for a player. Resolves per (heritage, variant): when a variant is
     * present (the committed case) it is authoritative; a bare heritage falls back to its group audience;
     * a player with no heritage at all keeps the historical WIZARD default (they are denied the chart
     * upstream regardless).
     */
    public static Audience audienceForHeritage(@Nullable Heritage heritage, @Nullable HeritageVariant variant) {
        if (variant != null) {
            return VARIANT_AUDIENCE.get(variant);
        }
        if (heritage != null) {
            return HERITAGE_FALLBACK.get(heritage);
        }
        return Audience.WIZARD;
    }

    /**
     * Whether a player holding {@code heritage}/{@code variant} meets a region's capability requirement.
     * The capability tags ({@code no_wand}, {@code no_casting}) are the only gate inside a web.
     */
    public static boolean meetsRequirement(Requirement requirement,
                                           @Nullable Heritage heritage,
                                           @Nullable HeritageVariant variant) {
        return switch (requirement) {
            case NONE -> true;
            case WAND -> heritage != null && heritage.canUseWand()
                    && (variant == null || !variant.hasTag("no_wand"));
            case CASTING -> variant == null || !variant.hasTag("no_casting");
        };
    }
}
