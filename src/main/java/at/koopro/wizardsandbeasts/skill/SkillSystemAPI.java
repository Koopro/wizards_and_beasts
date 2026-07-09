package at.koopro.wizardsandbeasts.skill;

import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.skill.vocation.VocationHelper;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.stats.PlayerStatsSyncPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import net.minecraft.server.level.ServerPlayer;
import at.koopro.wizardsandbeasts.spell.cast.ModifierStack;

import java.util.Locale;

/**
 * Public API for querying and modifying skill data.
 * Combines proficiency bonuses with skill tree bonuses.
 */
public final class SkillSystemAPI {
    /** Hard cap on total earnable skill points; routing under this budget is the web's core decision. */
    public static final int MAX_SKILL_POINTS = 60; // TUNE

    public record UnlockCheck(boolean allowed, String reason) {}

    private SkillSystemAPI() {}

    // ── Multiplier Queries (proficiency × skill bonuses) ──

    /**
     * Total damage multiplier = proficiency multiplier × skill multiplier.
     */
    public static float getDamageMultiplier(ServerPlayer player, Spell spell) {
        float profMult = spell.getProficiency(player).getDamageMultiplier();
        float skillMult = PlayerSkillBonusData.forPlayer(player)
                .damageMultipliers()
                .getOrDefault(spell.getCategory(), 1.0f);
        return profMult * skillMult;
    }

    /**
     * Total cooldown multiplier = proficiency multiplier × skill multiplier.
     */
    public static float getCooldownMultiplier(ServerPlayer player, Spell spell) {
        float profMult = spell.getProficiency(player).getCooldownMultiplier();
        float skillMult = PlayerSkillBonusData.forPlayer(player)
                .cooldownMultipliers()
                .getOrDefault(spell.getCategory(), 1.0f);
        return profMult * skillMult;
    }

    public static void applyDamageModifiers(ModifierStack stack, ServerPlayer player, Spell spell) {
        float profMult = spell.getProficiency(player).getDamageMultiplier();
        float skillMult = PlayerSkillBonusData.forPlayer(player)
                .damageMultipliers()
                .getOrDefault(spell.getCategory(), 1.0f);
        stack.multiplyDamage(profMult, "proficiency");
        stack.multiplyDamage(skillMult, "skill_tree");
    }

    public static void applyCooldownModifiers(ModifierStack stack, ServerPlayer player, Spell spell) {
        float profMult = spell.getProficiency(player).getCooldownMultiplier();
        float skillMult = PlayerSkillBonusData.forPlayer(player)
                .cooldownMultipliers()
                .getOrDefault(spell.getCategory(), 1.0f);
        stack.multiplyCooldown(profMult, "proficiency");
        stack.multiplyCooldown(skillMult, "skill_tree");
    }

    // ── Validation ──

    /**
     * Checks if a player can unlock (or level up) a skill.
     * Validates: skill exists, not maxed, enough points, adjacency, type allowed.
     */
    public static boolean canUnlock(ServerPlayer player, Skill skill) {
        return evaluateUnlock(player, skill).allowed();
    }

    /**
     * Returns a structured unlock check so packet handlers can show specific
     * rejection feedback instead of a generic "cannot unlock" message.
     *
     * <p>Web semantics (Phase 2): a node's <b>first</b> level is allocatable iff it is a
     * {@code root} node or ANY edge-neighbor is at level ≥ 1. Further levels of an
     * already-started node need only affordability and {@code < maxLevel} — maxing is optional
     * depth, never a gate.
     */
    public static UnlockCheck evaluateUnlock(ServerPlayer player, Skill skill) {
        if (!ModuleManager.isEnabled(Module.SKILL_TREES)) {
            return new UnlockCheck(false, "module_disabled");
        }
        PlayerSkillData data = getSkillData(player);
        if (data.isMaxed(skill.getId())) return new UnlockCheck(false, "maxed");
        if (data.getSkillPoints() < skill.getPointCost()) return new UnlockCheck(false, "not_enough_points");

        if (data.getSkillLevel(skill.getId()) == 0 && !skill.isRoot() && !hasAllocatedNeighbor(data, skill)) {
            return new UnlockCheck(false, "not_adjacent");
        }

        if (!isTreeAvailable(player, skill.getTree())) {
            return new UnlockCheck(false, "tree_unavailable");
        }

        // Vocation Mastery-cap + opposition lockout. Reaching here means SKILL_TREES is ENABLED (the guard
        // above short-circuits when disabled), so the gate is implicitly skipped while the module is off.
        VocationHelper.UnlockState vocationState = VocationHelper.unlockState(player, skill);
        if (vocationState != VocationHelper.UnlockState.ALLOWED) {
            return new UnlockCheck(false, "vocation_locked:" + vocationState.name().toLowerCase(Locale.ROOT));
        }
        return new UnlockCheck(true, "ok");
    }

    /**
     * Checks if a skill tree is available for the player's Heritage. A tree is gated to its
     * {@link SkillTreeId.Audience}: goblins see only goblin trees, house-elves only elf trees, and all
     * wand-using heritages see the wizard trees. Wandlore additionally requires a usable wand.
     */
    public static boolean isTreeAvailable(ServerPlayer player, SkillTreeId tree) {
        Heritage type = HeritageAPI.getPlayerHeritage(player);
        HeritageVariant subtype = HeritageAPI.getPlayerHeritageVariant(player);
        if (tree.getAudience() != SkillTreeId.audienceForHeritage(type)) {
            return false;
        }
        if (tree == SkillTreeId.WANDLORE) {
            return type != null && type.canUseWand() && (subtype == null || !subtype.hasTag("no_wand"));
        }
        return true;
    }

    // ── Unlock Flow ──

    /**
     * Attempts to unlock (or level up) a skill. Returns true on success.
     * Skill unlocks only affect skill-derived bonuses/abilities.
     */
    public static boolean tryUnlock(ServerPlayer player, String skillId) {
        Skill skill = SkillTrees.byId(skillId);
        if (skill == null) return false;
        if (!evaluateUnlock(player, skill).allowed()) return false;

        PlayerSkillData data = getSkillData(player);
        data.spendSkillPoints(skill.getPointCost());
        int newLevel = data.getSkillLevel(skillId) + 1;
        data.setSkillLevel(skillId, newLevel);

        applyImmediateEffects(skill);
        SkillAttributeApplicator.applyAll(player);
        PlayerStatsSyncPayload.syncToPlayer(player); // KNOWLEDGE derives from skill nodes unlocked
        // Mastering a new node is a formative achievement → happy memory.
        at.koopro.wizardsandbeasts.memory.MemoryService.tryFormMemory(
                player, at.koopro.wizardsandbeasts.memory.MemoryType.HAPPY, 0.5f, "skill_unlock", 1200L);
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
        applyImmediateEffects(skill);
        SkillAttributeApplicator.applyAll(player);
        PlayerStatsSyncPayload.syncToPlayer(player); // KNOWLEDGE derives from skill nodes unlocked
    }

    /** True if any edge-neighbor of {@code skill} is at level ≥ 1. */
    private static boolean hasAllocatedNeighbor(PlayerSkillData data, Skill skill) {
        for (String neighborId : SkillTrees.neighbors(skill.getId())) {
            if (data.getSkillLevel(neighborId) >= 1) {
                return true;
            }
        }
        return false;
    }

    // ── Point Management ──

    /**
     * Awards skill points, clamped so total earned never exceeds {@link #MAX_SKILL_POINTS}.
     * At the cap this is a no-op (XP-threshold earning silently stops).
     */
    public static void awardPoints(ServerPlayer player, int amount) {
        getSkillData(player).addSkillPoints(amount);
    }

    // ── Ability Checks ──

    public static boolean hasAbility(ServerPlayer player, String abilityId) {
        return getCache(player).hasAbility(abilityId);
    }

    /** Summed scalar gameplay bonus from all unlocked skills, or {@code 0} if none. */
    public static float getGameplayBonus(ServerPlayer player, GameplayStat stat) {
        return getCache(player).getGameplayBonus(stat);
    }

    // ── Internal ──

    public static PlayerSkillData getSkillData(ServerPlayer player) {
        return player.getData(ModAttachments.SKILL_DATA.get());
    }

    private static SkillEffectCache getCache(ServerPlayer player) {
        PlayerSkillData data = getSkillData(player);
        return SkillEffectCache.compute(data);
    }

    /** Count of skills in {@code tree} with at least one unlocked level. */
    public static int countUnlockedSkillsInTree(ServerPlayer player, SkillTreeId tree) {
        PlayerSkillData data = getSkillData(player);
        int n = 0;
        for (Skill skill : SkillTrees.getTree(tree)) {
            if (data.hasSkill(skill.getId())) {
                n++;
            }
        }
        return n;
    }

    public static void reconcileDerivedEffects(ServerPlayer player) {
        SkillAttributeApplicator.applyAll(player);
    }

    private static void applyImmediateEffects(Skill skill) {
        for (SkillEffect effect : skill.getEffects()) {
            if (effect instanceof SkillEffect.UnlockAbility) {
                // Ability availability is derived from unlocked skill levels via SkillEffectCache.
                // Keep this branch explicit so unlock effects remain discoverable in one place.
            }
        }
    }

    public static int getSpellGateOverride(ServerPlayer player, Spell spell) {
        return PlayerSkillBonusData.forPlayer(player)
                .spellGateOverrides()
                .getOrDefault(
                        net.minecraft.resources.Identifier.fromNamespaceAndPath("wizards_and_beasts", spell.getId()),
                        spell.getProficiency(player).getCastsRequired());
    }

    public static float getCategoryDamageMultiplier(ServerPlayer player, SpellCategory category) {
        return PlayerSkillBonusData.forPlayer(player).damageMultipliers().getOrDefault(category, 1.0f);
    }

    public static float getCategoryCooldownMultiplier(ServerPlayer player, SpellCategory category) {
        return PlayerSkillBonusData.forPlayer(player).cooldownMultipliers().getOrDefault(category, 1.0f);
    }
}
