package at.koopro.wizardsandbeasts.spell.learning;

import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

public final class SpellLearningEligibility {
    private SpellLearningEligibility() {
    }

    public static Result evaluate(ServerPlayer player, Spell spell) {
        if (spell == null) {
            return Result.deny("Unknown spell.");
        }
        PlayerSpellData spellData = player.getData(ModAttachments.SPELL_DATA.get());
        Heritage heritage = player.getData(ModAttachments.HERITAGE_DATA.get()).getSelectedHeritage();
        return evaluate(player, spell, spellData, heritage);
    }

    public static Result evaluate(@Nullable ServerPlayer player, Spell spell, PlayerSpellData spellData, @Nullable Heritage heritage) {
        if (spell == null) {
            return Result.deny("Unknown spell.");
        }
        if (ObscurialRules.isObscurialAbility(spell)) {
            return Result.deny("This is an Obscurial ability, not a learnable spell.");
        }
        if (spell.getCategory() == SpellCategory.DARK_ARTS
                && !ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return Result.deny("The Dark Arts are sealed away.");
        }
        if (!ObscurialRules.canHeritageUseSpell(heritage, spell)) {
            return Result.deny("Only Obscurials can learn this spell.");
        }
        if (spellData.knowsSpell(spell.getId())) {
            return Result.deny("You already know this spell.");
        }
        if (!spell.getRequirement().isMet(player, spellData)) {
            return Result.deny(spell.getRequirement().getDescription());
        }
        if (player != null) {
            String requiredSkillId = spell.getRequiredSkillId();
            if (requiredSkillId != null && !requiredSkillId.isBlank()) {
                PlayerSkillData skillData = player.getData(ModAttachments.SKILL_DATA.get());
                if (!skillData.hasSkill(requiredSkillId)) {
                    return Result.deny("Requires skill: " + requiredSkillId);
                }
            }

            String requiredProfessionId = spell.getRequiredProfessionId();
            if (requiredProfessionId != null && !requiredProfessionId.isBlank()) {
                PlayerHeritageData heritageData = player.getData(ModAttachments.HERITAGE_DATA.get());
                if (!heritageData.hasUnlockedProfession(requiredProfessionId)) {
                    return Result.deny("Requires profession: " + requiredProfessionId);
                }
            }
        }

        String masterySpellId = spell.getMasterySourceSpellId();
        PlayerSpellData.MasteryTier masteryTier = spell.getRequiredMasteryTier();
        if (masterySpellId != null && !masterySpellId.isBlank() && masteryTier != null
                && !spellData.hasReachedMasteryTier(masterySpellId, masteryTier)) {
            return Result.deny("Requires " + masteryTier.name().toLowerCase() + " mastery in " + masterySpellId);
        }
        return Result.allow();
    }

    public record Result(boolean learnable, String reason) {
        public static Result allow() {
            return new Result(true, "");
        }

        public static Result deny(String reason) {
            return new Result(false, reason);
        }
    }
}
