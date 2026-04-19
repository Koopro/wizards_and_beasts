package at.koopro.neo.spell;

import at.koopro.neo.data.PlayerSpellData;

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

    public boolean isMet(PlayerSpellData data) {
        if (prerequisiteId == null) return true;
        if (!data.knowsSpell(prerequisiteId)) return false;
        if (minProficiency != null) {
            int casts = data.getCastCount(prerequisiteId);
            return Proficiency.fromCastCount(casts).ordinal() >= minProficiency.ordinal();
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
