package at.koopro.neo.skill;

import at.koopro.neo.data.PlayerSkillData;
import at.koopro.neo.data.PlayerSpellData;
import at.koopro.neo.registry.ModAttachments;
import at.koopro.neo.spell.Spell;
import at.koopro.neo.type.TypeSystemAPI;
import at.koopro.neo.type.WizType;
import net.minecraft.server.level.ServerPlayer;

/**
 * Public API for querying and modifying skill data.
 * Combines proficiency bonuses with skill tree bonuses.
 */
public final class SkillSystemAPI {

    private SkillSystemAPI() {}

    // ── Multiplier Queries (proficiency × skill bonuses) ──

    /**
     * Total damage multiplier = proficiency multiplier × skill multiplier.
     */
    public static float getDamageMultiplier(ServerPlayer player, Spell spell) {
        float profMult = spell.getProficiency(player).getDamageMultiplier();
        SkillEffectCache cache = getCache(player);
        float skillMult = cache.getSpellDamageMultiplier(spell.getId(), spell.getCategory());
        return profMult * skillMult;
    }

    /**
     * Total cooldown multiplier = proficiency multiplier × skill multiplier.
     */
    public static float getCooldownMultiplier(ServerPlayer player, Spell spell) {
        float profMult = spell.getProficiency(player).getCooldownMultiplier();
        SkillEffectCache cache = getCache(player);
        float skillMult = cache.getSpellCooldownMultiplier(spell.getId(), spell.getCategory());
        return profMult * skillMult;
    }

    // ── Validation ──

    /**
     * Checks if a player can unlock (or level up) a skill.
     * Validates: skill exists, not maxed, enough points, prereqs met, type allowed.
     */
    public static boolean canUnlock(ServerPlayer player, Skill skill) {
        PlayerSkillData data = getSkillData(player);
        if (data.isMaxed(skill.getId())) return false;
        if (data.getSkillPoints() < skill.getPointCost()) return false;

        for (String prereqId : skill.getPrerequisites()) {
            if (!data.isMaxed(prereqId)) return false;
        }

        return isTreeAvailable(player, skill.getTree());
    }

    /**
     * Checks if a skill tree is available for the player's WizType.
     * Wandlore requires canUseWand. All others are universally available.
     */
    public static boolean isTreeAvailable(ServerPlayer player, SkillTreeId tree) {
        if (tree == SkillTreeId.WANDLORE) {
            WizType type = TypeSystemAPI.getPlayerType(player);
            return type != null && type.canUseWand();
        }
        return true;
    }

    // ── Unlock Flow ──

    /**
     * Attempts to unlock (or level up) a skill. Returns true on success.
     * Applies LearnSpell effects immediately to PlayerSpellData.
     */
    public static boolean tryUnlock(ServerPlayer player, String skillId) {
        Skill skill = SkillTrees.byId(skillId);
        if (skill == null) return false;
        if (!canUnlock(player, skill)) return false;

        PlayerSkillData data = getSkillData(player);
        data.spendSkillPoints(skill.getPointCost());
        int newLevel = data.getSkillLevel(skillId) + 1;
        data.setSkillLevel(skillId, newLevel);

        applyImmediateEffects(player, skill);
        return true;
    }

    /**
     * Force-unlocks a skill to max level, ignoring cost and prerequisites.
     * For admin commands.
     */
    public static void forceUnlock(ServerPlayer player, String skillId) {
        Skill skill = SkillTrees.byId(skillId);
        if (skill == null) return;

        PlayerSkillData data = getSkillData(player);
        data.setSkillLevel(skillId, skill.getMaxLevel());
        applyImmediateEffects(player, skill);
    }

    // ── Point Management ──

    public static void awardPoints(ServerPlayer player, int amount) {
        getSkillData(player).addSkillPoints(amount);
    }

    // ── Ability Checks ──

    public static boolean hasAbility(ServerPlayer player, String abilityId) {
        return getCache(player).hasAbility(abilityId);
    }

    // ── Internal ──

    public static PlayerSkillData getSkillData(ServerPlayer player) {
        return player.getData(ModAttachments.SKILL_DATA.get());
    }

    private static SkillEffectCache getCache(ServerPlayer player) {
        PlayerSkillData data = getSkillData(player);
        return SkillEffectCache.compute(data);
    }

    private static void applyImmediateEffects(ServerPlayer player, Skill skill) {
        PlayerSpellData spellData = player.getData(ModAttachments.SPELL_DATA.get());
        for (SkillEffect effect : skill.getEffects()) {
            if (effect instanceof SkillEffect.LearnSpell learn) {
                spellData.learnSpell(learn.spellId());
            }
        }
    }
}
