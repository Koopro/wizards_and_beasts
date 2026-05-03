package at.koopro.wizardsandbeasts.skill;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.Spell;
import at.koopro.wizardsandbeasts.spell.cast.ModifierStack;
import at.koopro.wizardsandbeasts.type.TypeSystemAPI;
import at.koopro.wizardsandbeasts.type.WizType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public API for querying and modifying skill data.
 * Combines proficiency bonuses with skill tree bonuses.
 */
public final class SkillSystemAPI {
    private static final Identifier SKILL_MAX_HEALTH_ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "skill_max_health");
    private static final Identifier SKILL_MOVEMENT_SPEED_ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "skill_movement_speed");
    private static final Identifier SKILL_ARMOR_ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "skill_armor");

    public record UnlockCheck(boolean allowed, String reason) {}

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

    public static void applyDamageModifiers(ModifierStack stack, ServerPlayer player, Spell spell) {
        float profMult = spell.getProficiency(player).getDamageMultiplier();
        SkillEffectCache cache = getCache(player);
        float skillMult = cache.getSpellDamageMultiplier(spell.getId(), spell.getCategory());
        stack.multiplyDamage(profMult, "proficiency");
        stack.multiplyDamage(skillMult, "skill_tree");
    }

    public static void applyCooldownModifiers(ModifierStack stack, ServerPlayer player, Spell spell) {
        float profMult = spell.getProficiency(player).getCooldownMultiplier();
        SkillEffectCache cache = getCache(player);
        float skillMult = cache.getSpellCooldownMultiplier(spell.getId(), spell.getCategory());
        stack.multiplyCooldown(profMult, "proficiency");
        stack.multiplyCooldown(skillMult, "skill_tree");
    }

    // ── Validation ──

    /**
     * Checks if a player can unlock (or level up) a skill.
     * Validates: skill exists, not maxed, enough points, prereqs met, type allowed.
     */
    public static boolean canUnlock(ServerPlayer player, Skill skill) {
        return evaluateUnlock(player, skill).allowed();
    }

    /**
     * Returns a structured unlock check so packet handlers can show specific
     * rejection feedback instead of a generic "cannot unlock" message.
     */
    public static UnlockCheck evaluateUnlock(ServerPlayer player, Skill skill) {
        PlayerSkillData data = getSkillData(player);
        if (data.isMaxed(skill.getId())) return new UnlockCheck(false, "maxed");
        if (data.getSkillPoints() < skill.getPointCost()) return new UnlockCheck(false, "not_enough_points");

        for (String prereqId : skill.getPrerequisites()) {
            if (!data.isMaxed(prereqId)) return new UnlockCheck(false, "missing_prerequisite:" + prereqId);
        }

        if (!isTreeAvailable(player, skill.getTree())) {
            return new UnlockCheck(false, "tree_unavailable");
        }
        return new UnlockCheck(true, "ok");
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
        if (!evaluateUnlock(player, skill).allowed()) return false;

        PlayerSkillData data = getSkillData(player);
        data.spendSkillPoints(skill.getPointCost());
        int newLevel = data.getSkillLevel(skillId) + 1;
        data.setSkillLevel(skillId, newLevel);

        applyImmediateEffects(player, skill, newLevel);
        reconcileDerivedEffects(player);
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
        applyImmediateEffects(player, skill, skill.getMaxLevel());
        reconcileDerivedEffects(player);
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

    public static void reconcileDerivedEffects(ServerPlayer player) {
        applyPassiveAttributes(player);
    }

    private static void applyImmediateEffects(ServerPlayer player, Skill skill, int newLevel) {
        PlayerSpellData spellData = player.getData(ModAttachments.SPELL_DATA.get());
        for (SkillEffect effect : skill.getEffects()) {
            if (effect instanceof SkillEffect.LearnSpell learn) {
                spellData.learnSpell(learn.spellId());
            } else if (effect instanceof SkillEffect.PassiveAttribute) {
                applyPassiveAttributes(player);
            } else if (effect instanceof SkillEffect.UnlockAbility) {
                // Ability availability is derived from unlocked skill levels via SkillEffectCache.
                // Keep this branch explicit so unlock effects remain discoverable in one place.
            }
        }
    }

    private static void applyPassiveAttributes(ServerPlayer player) {
        PlayerSkillData data = getSkillData(player);
        Map<String, Double> totals = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : data.getUnlockedSkills().entrySet()) {
            Skill skill = SkillTrees.byId(entry.getKey());
            if (skill == null) continue;
            int level = Math.max(0, entry.getValue());
            if (level == 0) continue;
            for (SkillEffect effect : skill.getEffects()) {
                if (effect instanceof SkillEffect.PassiveAttribute attr) {
                    totals.merge(attr.attributeId(), attr.amountPerLevel() * level, Double::sum);
                }
            }
        }

        applyAttributeModifier(player, "max_health", Attributes.MAX_HEALTH, SKILL_MAX_HEALTH_ID, totals.getOrDefault("max_health", 0.0));
        applyAttributeModifier(player, "movement_speed", Attributes.MOVEMENT_SPEED, SKILL_MOVEMENT_SPEED_ID, totals.getOrDefault("movement_speed", 0.0));
        applyAttributeModifier(player, "armor", Attributes.ARMOR, SKILL_ARMOR_ID, totals.getOrDefault("armor", 0.0));
    }

    private static void applyAttributeModifier(ServerPlayer player,
                                               String attributeId,
                                               net.minecraft.core.Holder<Attribute> attribute,
                                               Identifier modifierId,
                                               double value) {
        var instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(modifierId);
        if (value != 0.0d) {
            instance.addTransientModifier(new AttributeModifier(modifierId, value, AttributeModifier.Operation.ADD_VALUE));
        }
        if ("max_health".equals(attributeId) && value < 0 && player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }
}
