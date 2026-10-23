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
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class SpellLearningEligibility {
    private SpellLearningEligibility() {
    }

    public static Result evaluate(ServerPlayer player, Spell spell) {
        if (spell == null) {
            return Result.deny("unknown", "Unknown spell.");
        }
        PlayerSpellData spellData = player.getData(ModAttachments.SPELL_DATA.get());
        PlayerHeritageData heritage = player.getData(ModAttachments.HERITAGE_DATA.get());
        return evaluate(player, spell, spellData, heritage);
    }

    public static Result evaluate(@Nullable ServerPlayer player, Spell spell, PlayerSpellData spellData, @Nullable PlayerHeritageData heritage) {
        return evaluate(player, spell, spellData, heritage, false);
    }

    /**
     * @param nodeIsTheRequirement true for a skill-web lesson: the allocated node stands in for the spell's
     *        own progression gates (prerequisite spell, required skill, profession, mastery), because the web's
     *        layout is the progression the player paid points for and may not match the spell's authored
     *        prerequisite. Every gate on whether the spell may exist for this player at all — module,
     *        implementation, heritage, already known, Spell Law — still applies.
     */
    public static Result evaluate(@Nullable ServerPlayer player, Spell spell, PlayerSpellData spellData,
                                  @Nullable PlayerHeritageData heritage, boolean nodeIsTheRequirement) {
        if (spell == null) {
            return Result.deny("unknown", "Unknown spell.");
        }
        if (ObscurialRules.isObscurialAbility(spell)) {
            return Result.deny("obscurial", "This is an Obscurial ability, not a learnable spell.");
        }
        if (spell.getCategory() == SpellCategory.DARK_ARTS
                && !ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return Result.deny("dark_arts_sealed", "The Dark Arts are sealed away.");
        }
        // Checked after the module gate and before every player-progress gate, mirroring the cast
        // gate's ordering: the module owns whether the spell is present at all, and no amount of
        // skill, profession or mastery unlocks a spell nobody has written yet.
        if (!spell.isImplemented()) {
            return Result.deny("not_written", "Not yet learnable — this spell is still being written.");
        }
        // A Squib, a goblin, a house-elf: someone whose heritage can never hold a wand cannot learn what they could
        // never slot. A player who has not chosen a heritage yet is not refused; they have not chosen against it.
        if (heritage != null && heritage.getSelectedHeritage() != null
                && (!heritage.canUseWand() || !heritage.canCast())) {
            return Result.deny("no_wand", "Only someone who can use a wand can learn spells.");
        }
        if (!ObscurialRules.canHeritageUseSpell(heritage, spell)) {
            return Result.deny("obscurial_only", "Only Obscurials can learn this spell.");
        }
        if (spellData.knowsSpell(spell.getId())) {
            return Result.deny("known", "You already know this spell.");
        }
        // What the law asks before a book will teach this. After the module and implementation gates,
        // which decide whether the spell exists at all, and before the player's own progress gates so
        // that "you have not studied this kind of magic" is the reason a student is told first rather
        // than a proficiency requirement they could not have met anyway.
        net.minecraft.network.chat.Component lawRefusal = SpellLawLearningGate.refusal(player, spell);
        if (lawRefusal != null) {
            return Result.deny(lawRefusal);
        }
        if (nodeIsTheRequirement) {
            return Result.allow();
        }
        if (!spell.getRequirement().isMet(player, spellData)) {
            return Result.deny(spell.getRequirement().describe());
        }
        if (player != null) {
            String requiredSkillId = spell.getEffectiveRequiredSkillId();
            if (requiredSkillId != null && !requiredSkillId.isBlank()) {
                PlayerSkillData skillData = player.getData(ModAttachments.SKILL_DATA.get());
                if (!skillData.hasSkill(requiredSkillId)) {
                    return Result.deny("skill", "Requires skill: %s", Component.translatableWithFallback(
                            "skill.wizards_and_beasts.node." + requiredSkillId + ".name", requiredSkillId));
                }
            }

            String requiredProfessionId = spell.getRequiredProfessionId();
            if (requiredProfessionId != null && !requiredProfessionId.isBlank()) {
                PlayerHeritageData heritageData = player.getData(ModAttachments.HERITAGE_DATA.get());
                if (!heritageData.hasUnlockedProfession(requiredProfessionId)) {
                    return Result.deny("profession", "Requires profession: %s", Component.translatableWithFallback(
                            "profession.wizards_and_beasts." + requiredProfessionId, requiredProfessionId));
                }
            }
        }

        String masterySpellId = spell.getMasterySourceSpellId();
        PlayerSpellData.MasteryTier masteryTier = spell.getRequiredMasteryTier();
        // Hits are recorded under the canonical id ("wizards_and_beasts:reparo"); a datapack names the
        // mastery spell bare ("reparo"). Resolve first, as SpellRequirement does for prerequisites, or the
        // count is always zero and the gate can never open.
        if (masterySpellId != null && !masterySpellId.isBlank()) {
            Spell masterySpell = at.koopro.wizardsandbeasts.spell.core.Spells.byId(masterySpellId);
            if (masterySpell != null) {
                masterySpellId = masterySpell.getId();
            }
        }
        if (masterySpellId != null && !masterySpellId.isBlank() && masteryTier != null
                && !spellData.hasReachedMasteryTier(masterySpellId, masteryTier)) {
            return Result.deny("mastery", "Requires %s mastery in %s", masteryTier.name().toLowerCase(),
                    spellName(masterySpellId));
        }
        return Result.allow();
    }

    /** A spell's display name for a refusal sentence, falling back to its id. */
    private static Component spellName(String spellId) {
        Spell spell = at.koopro.wizardsandbeasts.spell.core.Spells.byId(spellId);
        return spell == null ? Component.literal(spellId)
                : Component.translatableWithFallback(spell.getDisplayName(), spellId);
    }

    /**
     * Whether the spell may be learned, and if not, why -- as a translatable sentence, so the reader sees it in their
     * own language and with names rather than ids. {@link #reason()} is that sentence on the server, which resolves
     * it to its English fallback (logs, admin output, test messages).
     */
    public record Result(boolean learnable, Component why) {
        private static final String KEY = "spell.wizards_and_beasts.learning.refusal.";

        public static Result allow() {
            return new Result(true, Component.empty());
        }

        public static Result deny(Component why) {
            return new Result(false, why);
        }

        public static Result deny(String key, String english, Object... args) {
            return new Result(false, Component.translatableWithFallback(KEY + key, english, args));
        }

        public String reason() {
            return why.getString();
        }
    }
}
