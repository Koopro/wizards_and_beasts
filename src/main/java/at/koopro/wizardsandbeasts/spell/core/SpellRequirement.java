package at.koopro.wizardsandbeasts.spell.core;

import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.skill.PlayerSkillBonusData;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

public class SpellRequirement {

    public static final SpellRequirement NONE = new SpellRequirement(null, null);

    @Nullable private final String prerequisiteId;
    @Nullable private final Proficiency minProficiency;

    private SpellRequirement(@Nullable String prerequisiteId, @Nullable Proficiency minProficiency) {
        this.prerequisiteId = prerequisiteId;
        this.minProficiency = minProficiency;
    }

    public static SpellRequirement none() {
        return NONE;
    }

    public static SpellRequirement knows(Spell spell) {
        return new SpellRequirement(spell.getId(), null);
    }

    public static SpellRequirement proficiency(Spell spell, Proficiency prof) {
        return new SpellRequirement(spell.getId(), prof);
    }

    /**
     * Id-based prerequisite. Use when the prerequisite is a JSON/datapack spell that has no Java
     * {@code Spells.*} constant (e.g. a migrated spell). Resolved lazily at {@link #isMet} time via
     * the player's known-spell set, so the prerequisite need not be loaded when this is constructed.
     */
    public static SpellRequirement knows(String prerequisiteId) {
        return new SpellRequirement(prerequisiteId, null);
    }

    /** Id-based proficiency prerequisite; see {@link #knows(String)}. */
    public static SpellRequirement proficiency(String prerequisiteId, Proficiency prof) {
        return new SpellRequirement(prerequisiteId, prof);
    }

    public boolean isMet(PlayerSpellData data) {
        return isMet(null, data);
    }

    public boolean isMet(@Nullable ServerPlayer player, PlayerSpellData data) {
        if (prerequisiteId == null) return true;
        if (!data.knowsSpell(prerequisiteId)) return false;
        if (minProficiency != null) {
            int casts = data.getSuccessfulHits(prerequisiteId);
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

    @Nullable
    public Spell getPrerequisite() {
        return prerequisiteId != null ? Spells.byId(prerequisiteId) : null;
    }

    @Nullable
    public Proficiency getMinProficiency() {
        return minProficiency;
    }

    public String getDescription() {
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
