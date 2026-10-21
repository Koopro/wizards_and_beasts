package at.koopro.wizardsandbeasts.owl;

import at.koopro.wizardsandbeasts.ability.data.PlayerAbilityData;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.bestiary.data.PlayerBestiaryData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Reads a candidate's live progression into {@link OWLGradeRules.Evidence} and grades it. The rules
 * — what each subject reads and what each grade costs — live in {@link OWLGradeRules}; this class only
 * knows where the numbers are kept.
 */
public final class OWLGradeCalculator {

    private OWLGradeCalculator() {}

    /** Grades for every examined subject, from the candidate's progression as it stands. */
    public static @NonNull Map<OWLSubject, OWLGrade> calculateAll(@NonNull ServerPlayer player) {
        return OWLGradeRules.gradeAll(evidence(player));
    }

    /** What the candidate brings to the desk. */
    public static OWLGradeRules.@NonNull Evidence evidence(@NonNull ServerPlayer player) {
        PlayerSpellData spells = player.getData(ModAttachments.SPELL_DATA.get());
        int charms = 0;
        int defence = 0;
        for (Map.Entry<String, Integer> entry : spells.getSuccessfulHitCounts().entrySet()) {
            Spell spell = Spells.byId(entry.getKey());
            if (spell == null || !spells.knowsSpell(entry.getKey())) continue;
            int points = OWLGradeRules.tierPoints(entry.getValue(),
                    Proficiency.PROFICIENT.getCastsRequired(), Proficiency.MASTERED.getCastsRequired());
            SpellCategory category = spell.getCategory();
            if (category == SpellCategory.UTILITY) {
                charms += points;
            } else if (category == SpellCategory.COMBAT || category == SpellCategory.DEFENSE) {
                defence += points;
            }
        }

        PlayerAbilityData ability = player.getData(ModAttachments.PLAYER_ABILITY_DATA.get());
        PlayerSkillData skills = player.getData(ModAttachments.SKILL_DATA.get());
        PlayerBestiaryData bestiary = player.getData(ModAttachments.BESTIARY_DATA.get());
        at.koopro.wizardsandbeasts.education.data.PlayerEducationData education =
                player.getData(ModAttachments.EDUCATION_DATA.get());
        int studied = 0;
        int known = 0;
        for (DiscoveryTier tier : bestiary.tiers().values()) {
            if (tier.tierIndex() >= DiscoveryTier.STUDIED.tierIndex()) studied++;
            if (tier == DiscoveryTier.KNOWN) known++;
        }

        return new OWLGradeRules.Evidence(
                charms,
                defence,
                ability.animagusUnlocked(),
                ability.animagusUnlocked() && ability.animagusRegistered(),
                skills.getPotionBrewPoints(),
                skills.getPlantsHarvested(),
                countTreeNodes(skills, SkillTreeId.HERBOLOGY),
                studied,
                known,
                countTreeNodes(skills, SkillTreeId.WANDLORE),
                skills.getLoreItemsRead(),
                at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper.completedResearch(player),
                at.koopro.wizardsandbeasts.education.Astronomy.points(education.moonPhases(), education.constellations()),
                education.lessons());
    }

    /** Coursework grades at {@code level} for every paper sat at it. */
    public static @NonNull Map<OWLSubject, OWLGrade> calculateAll(@NonNull ServerPlayer player, @NonNull Qualification level) {
        return OWLGradeRules.gradeAll(level, evidence(player));
    }

    private static int countTreeNodes(PlayerSkillData skillData, SkillTreeId tree) {
        return (int) SkillTrees.getTree(tree).stream()
                .filter(skill -> skillData.hasSkill(skill.getId()))
                .count();
    }
}
