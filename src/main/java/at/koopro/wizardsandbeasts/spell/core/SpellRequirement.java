package at.koopro.wizardsandbeasts.spell.core;

import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.skill.PlayerSkillBonusData;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SpellRequirement {

    public static final SpellRequirement NONE = new SpellRequirement(null, null, List.of());

    @Nullable private final String prerequisiteId;
    @Nullable private final Proficiency minProficiency;
    /** Non-empty for an AND composite; each clause must be met. Leaf requirements leave this empty. */
    private final List<SpellRequirement> clauses;

    private SpellRequirement(@Nullable String prerequisiteId, @Nullable Proficiency minProficiency,
                             List<SpellRequirement> clauses) {
        this.prerequisiteId = prerequisiteId;
        this.minProficiency = minProficiency;
        this.clauses = clauses;
    }

    public static SpellRequirement none() {
        return NONE;
    }

    public static SpellRequirement knows(Spell spell) {
        return new SpellRequirement(spell.getId(), null, List.of());
    }

    public static SpellRequirement proficiency(Spell spell, Proficiency prof) {
        return new SpellRequirement(spell.getId(), prof, List.of());
    }

    /**
     * Conjunction: every supplied requirement must be met. {@link #NONE} clauses are dropped; a single
     * surviving clause is returned unwrapped, and zero clauses collapses to {@link #NONE}.
     */
    public static SpellRequirement allOf(SpellRequirement... requirements) {
        List<SpellRequirement> flat = new ArrayList<>();
        for (SpellRequirement req : requirements) {
            if (req == null || req == NONE) {
                continue;
            }
            if (!req.clauses.isEmpty()) {
                flat.addAll(req.clauses);
            } else {
                flat.add(req);
            }
        }
        if (flat.isEmpty()) {
            return NONE;
        }
        if (flat.size() == 1) {
            return flat.get(0);
        }
        return new SpellRequirement(null, null, List.copyOf(flat));
    }

    /**
     * Id-based prerequisite. Use when the prerequisite is a JSON/datapack spell that has no Java
     * {@code Spells.*} constant (e.g. a migrated spell). Resolved lazily at {@link #isMet} time via
     * the player's known-spell set, so the prerequisite need not be loaded when this is constructed.
     */
    public static SpellRequirement knows(String prerequisiteId) {
        return new SpellRequirement(prerequisiteId, null, List.of());
    }

    /** Id-based proficiency prerequisite; see {@link #knows(String)}. */
    public static SpellRequirement proficiency(String prerequisiteId, Proficiency prof) {
        return new SpellRequirement(prerequisiteId, prof, List.of());
    }

    public boolean isMet(PlayerSpellData data) {
        return isMet(null, data);
    }

    public boolean isMet(@Nullable ServerPlayer player, PlayerSpellData data) {
        if (!clauses.isEmpty()) {
            for (SpellRequirement clause : clauses) {
                if (!clause.isMet(player, data)) {
                    return false;
                }
            }
            return true;
        }
        if (prerequisiteId == null) return true;
        // Known-spell sets and stat maps are keyed by the registered spell's exact id (namespaced for
        // JSON spells), so an authored bare id must be canonicalized before lookup.
        String lookupId = canonicalPrerequisiteId();
        if (!data.knowsSpell(lookupId)) return false;
        if (minProficiency != null) {
            int casts = data.getSuccessfulHits(lookupId);
            int requiredCasts = minProficiency.getCastsRequired();
            if (player != null) {
                Identifier key = prerequisiteId.contains(":")
                        ? Identifier.parse(prerequisiteId)
                        : Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, prerequisiteId);
                requiredCasts = PlayerSkillBonusData.forPlayer(player).spellGateOverrides().getOrDefault(key, requiredCasts);
            }
            return casts >= requiredCasts;
        }
        return true;
    }

    @Nullable
    public String getPrerequisiteId() {
        return prerequisiteId;
    }

    /** The prerequisite's registered id when it resolves, else the authored id verbatim. */
    private String canonicalPrerequisiteId() {
        Spell prereq = Spells.byId(prerequisiteId);
        return prereq != null ? prereq.getId() : prerequisiteId;
    }

    @Nullable
    public Spell getPrerequisite() {
        return prerequisiteId != null ? Spells.byId(prerequisiteId) : null;
    }

    @Nullable
    public Proficiency getMinProficiency() {
        return minProficiency;
    }

    public String getDescription() {
        if (!clauses.isEmpty()) {
            return clauses.stream().map(SpellRequirement::getDescription).collect(Collectors.joining(" and "));
        }
        if (prerequisiteId == null) return "No requirements";
        Spell prereq = Spells.byId(prerequisiteId);
        String name = prereq != null ? prereq.getDisplayName() : prerequisiteId;
        if (minProficiency != null) {
            String profName = minProficiency.name().charAt(0)
                    + minProficiency.name().substring(1).toLowerCase();
            return profName + " in " + name;
        }
        return "Requires " + name;
    }
}
