package at.koopro.wizardsandbeasts.skill;

import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.stats.PlayerStatsSyncPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.standing.StandingService;
import at.koopro.wizardsandbeasts.standing.gate.StandingGates;
import at.koopro.wizardsandbeasts.standing.gate.StandingRequirement;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import net.minecraft.server.level.ServerPlayer;
import at.koopro.wizardsandbeasts.spell.cast.ModifierStack;

/**
 * Public API for querying and modifying skill data.
 * Combines proficiency bonuses with skill tree bonuses.
 */
public final class SkillSystemAPI {
    /** Hard cap on total earnable skill points; routing under this budget is the web's core decision. */
    public static final int MAX_SKILL_POINTS = 60; // TUNE

    /**
     * Per-audience earnable-point cap, branched at the {@link #awardPoints} seam. All audiences share
     * {@link #MAX_SKILL_POINTS} today, so the hook is a no-op — a future era can give a heritage a
     * larger or smaller budget without touching the earn plumbing.
     */
    private static final java.util.EnumMap<SkillTreeId.Audience, Integer> AUDIENCE_POINT_CAP =
            new java.util.EnumMap<>(SkillTreeId.Audience.class);

    static {
        for (SkillTreeId.Audience audience : SkillTreeId.Audience.values()) {
            AUDIENCE_POINT_CAP.put(audience, MAX_SKILL_POINTS); // TUNE
        }
    }

    /** Total earnable skill points for a player's audience (all {@link #MAX_SKILL_POINTS} today). */
    public static int pointCapFor(SkillTreeId.Audience audience) {
        return AUDIENCE_POINT_CAP.getOrDefault(audience, MAX_SKILL_POINTS);
    }

    public record UnlockCheck(boolean allowed, String reason) {}

    private SkillSystemAPI() {}

    // ── Multiplier Queries (proficiency × skill bonuses) ──

    /**
     * Total damage multiplier = proficiency multiplier × skill multiplier.
     */
    /**
     * The skill web's damage multiplier for this caster and spell: the category bonus times the
     * per-spell bonus.
     *
     * <p><b>Proficiency is not in here any more.</b> It used to be — this method multiplied in the
     * {@code Proficiency} enum tier while {@code ProficiencyScaler}'s float curve was multiplied in
     * separately at the cast site, so the same practice was paid for twice from two systems reading
     * the same counter. Worse, the enum channel ignored {@code Module.PROFICIENCY}: switching the
     * module off still handed out 1.2x at mastery. Proficiency now has exactly one owner, and it is
     * the module-gated one.
     *
     * <p>The two halves come from different places on purpose. Category bonuses are pre-aggregated
     * into {@link PlayerSkillBonusData} when allocation changes; per-spell bonuses live only in
     * {@link SkillEffectCache}, which is computed on demand. Reading categories from the cache too
     * would double-count them, since the cache aggregates both.
     */
    public static float getSkillDamageMultiplier(ServerPlayer player, Spell spell) {
        float category = PlayerSkillBonusData.forPlayer(player)
                .damageMultipliers()
                .getOrDefault(spell.getCategory(), 1.0f);
        return category * perSpellDamageMultiplier(player, spell);
    }

    /** Skill-web cooldown multiplier: category reduction times the per-spell reduction. Lower is faster. */
    public static float getSkillCooldownMultiplier(ServerPlayer player, Spell spell) {
        float category = PlayerSkillBonusData.forPlayer(player)
                .cooldownMultipliers()
                .getOrDefault(spell.getCategory(), 1.0f);
        return category * perSpellCooldownMultiplier(player, spell);
    }

    /**
     * The per-spell half, which until now went nowhere.
     *
     * <p>Seventeen shipped nodes — every {@code <spell>_unlock} in the web — declare a
     * {@code spell_damage_bonus} or {@code spell_cooldown_reduction}. {@link SkillEffectCache}
     * computed them correctly and exposed them through
     * {@link SkillEffectCache#getSpellDamageMultiplier}, and nothing ever called it: cast time read
     * {@link PlayerSkillBonusData}, which only carries per-<em>category</em> maps. Every one of those
     * nodes described a benefit the game did not give.
     *
     * <p>Scoped to the per-spell maps precisely so the category half stays where it already worked.
     */
    private static float perSpellDamageMultiplier(ServerPlayer player, Spell spell) {
        return getCache(player).getSpellOnlyDamageMultiplier(spell.getId());
    }

    private static float perSpellCooldownMultiplier(ServerPlayer player, Spell spell) {
        return getCache(player).getSpellOnlyCooldownMultiplier(spell.getId());
    }

    /**
     * Publish the skill channel into the cast's {@link ModifierStack}.
     *
     * <p>Damage and cooldown together, because they are one channel with two faces and setting only
     * half of it is always a mistake.
     *
     * <p>Misfire rides along as a third face rather than a separate call, for the same reason. It is
     * <em>added</em> (negatively) instead of set, because {@code ModifierStack} accumulates misfire
     * from several independent sources — the wand's fizzle, allegiance, the caster's PRECISION — and
     * a setter would silently discard whichever ran first. The reader clamps the running total to
     * {@code [0, 1]}, so a large web bonus floors at zero rather than turning into a hit bonus.
     */
    public static void applySkillModifiers(ModifierStack stack, ServerPlayer player, Spell spell) {
        stack.setSkill(getSkillDamageMultiplier(player, spell), getSkillCooldownMultiplier(player, spell));
        float steadiness = getGameplayBonus(player, GameplayStat.SPELL_MISFIRE_REDUCTION);
        if (steadiness > 0f) {
            stack.addMisfireChance(-steadiness, "skill_web");
        }
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

        // Region capability requirement — ordered strictly after the audience check. A sealed region
        // (e.g. wandlore for a squib, spell_mastery for an obscurial) is visible in the chart but cannot
        // be allocated. Rejects crafted payloads for sealed regions server-side.
        Heritage heritage = HeritageAPI.getPlayerHeritage(player);
        HeritageVariant variant = HeritageAPI.getPlayerHeritageVariant(player);
        if (!SkillTreeId.meetsRequirement(skill.getTree().getRequirement(), heritage, variant)) {
            return new UnlockCheck(false, "requirement_unmet");
        }

        // Magical standing — last, and last on purpose. It is the only gate a player can move by
        // playing differently, so it should be the reason they are told about once the fixed facts
        // (audience, capability) have already passed. Skips entirely when no gate is authored, which
        // is every default install.
        if (!StandingGates.isEmpty()) {
            StandingRequirement unmet = StandingGates.firstUnmet(
                    skill.getTree(), skill.getId(), axis -> StandingService.bandOf(player, axis));
            if (unmet != null) {
                return new UnlockCheck(false, "standing_unmet");
            }
        }

        // Vocation is declarative identity only (web rework Phase 3): the web's travel cost under the
        // point cap does the differentiation the old mastery-band/opposition gate used to enforce.
        return new UnlockCheck(true, "ok");
    }

    /**
     * Checks whether a tree's <b>audience</b> matches the player's tradition of origin: goblins see only
     * goblin trees, house-elves only elf trees, and the wizard-audience heritages (wizardkind incl. squib,
     * werewolf, obscurial, vampire, half/quarter-veela, half-giant) see the wizard trees. Capability
     * requirements (wand/casting) are a separate, in-web gate handled in {@link #evaluateUnlock} — the old
     * hardcoded WANDLORE wand check was migrated into {@link SkillTreeId.Requirement#WAND}.
     */
    public static boolean isTreeAvailable(ServerPlayer player, SkillTreeId tree) {
        Heritage type = HeritageAPI.getPlayerHeritage(player);
        HeritageVariant subtype = HeritageAPI.getPlayerHeritageVariant(player);
        return tree.getAudience() == SkillTreeId.audienceForHeritage(type, subtype);
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

        applyImmediateEffects(player, skill);
        SkillAttributeApplicator.applyAll(player);
        PlayerStatsSyncPayload.syncToPlayer(player); // KNOWLEDGE derives from skill nodes unlocked
        // A newly allocated node may grant an ability (legacy unlock_ability or grant_ability) → refresh
        // the source-tracked snapshot so the client mirror reflects the new SKILL_NODE grant.
        at.koopro.wizardsandbeasts.network.skill.AbilityGrantsSyncS2CPayload.syncToPlayer(player);
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
        applyImmediateEffects(player, skill);
        SkillAttributeApplicator.applyAll(player);
        PlayerStatsSyncPayload.syncToPlayer(player); // KNOWLEDGE derives from skill nodes unlocked
        at.koopro.wizardsandbeasts.network.skill.AbilityGrantsSyncS2CPayload.syncToPlayer(player);
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
        SkillTreeId.Audience audience = SkillTreeId.audienceForHeritage(
                HeritageAPI.getPlayerHeritage(player), HeritageAPI.getPlayerHeritageVariant(player));
        getSkillData(player).addSkillPoints(amount, pointCapFor(audience));
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

    /**
     * Re-derives everything an allocation change can affect. Called from every refund path
     * ({@code respec}, {@code reset}, {@code reset &lt;skill&gt;}) and from the login web migration.
     *
     * <p>The spell revoke lives here rather than in each command precisely because there are four
     * callers and a fifth will be added eventually; a refund path that forgot to call it would leave
     * the player holding a spell they no longer pay for. Idempotent — a still-allocated node
     * re-asserts its spell, so calling this on an unchanged web changes nothing.
     */
    public static void reconcileDerivedEffects(ServerPlayer player) {
        SkillAttributeApplicator.applyAll(player);
        revokeWebTaughtSpells(player);
    }

    /**
     * The effects that must be <em>pushed</em> at allocation time rather than derived on read.
     *
     * <p>Most effects are derived: abilities recompute from allocated nodes through
     * {@link at.koopro.wizardsandbeasts.ability.grant.AbilityGrantService}, attributes are re-applied
     * wholesale by {@link SkillAttributeApplicator}, and the multiplier maps are rebuilt from the
     * cache. Spell knowledge is the exception, because it lives in {@code PlayerSpellData} as one flat
     * set shared with the teacher and has no source column to derive from.
     */
    private static void applyImmediateEffects(ServerPlayer player, Skill skill) {
        for (SkillEffect effect : skill.getEffects()) {
            if (effect instanceof SkillEffect.UnlockAbility) {
                // Ability availability is derived from unlocked skill levels via SkillEffectCache.
                // Keep this branch explicit so unlock effects remain discoverable in one place.
            } else if (effect instanceof SkillEffect.LearnSpell learn) {
                teachSpell(player, learn.spellId());
            }
        }
    }

    /**
     * Teaches a node's spell, recording it only if the player did not already know it.
     *
     * <p>The ledger entry is what {@link #revokeWebTaughtSpells} later acts on, and skipping it for a
     * spell the player already had is the whole reason a respec cannot confiscate a lesson bought
     * from a teacher with Knuts.
     */
    private static void teachSpell(ServerPlayer player, String spellId) {
        var spell = at.koopro.wizardsandbeasts.spell.core.Spells.byId(spellId);
        if (spell == null) {
            return;
        }
        String canonicalId = spell.getId();
        var spellData = player.getData(ModAttachments.SPELL_DATA.get());
        if (spellData.knowsSpell(canonicalId)) {
            return;
        }
        spellData.learnSpell(canonicalId);
        getSkillData(player).recordWebTaughtSpell(canonicalId);
        at.koopro.wizardsandbeasts.network.spell.SpellDataSyncS2CPayload.syncToPlayer(player);
    }

    /**
     * Revokes every web-taught spell whose granting node is no longer allocated. Called from the
     * respec path; safe to call at any time, since a still-allocated node simply re-asserts its spell.
     *
     * @return the number of spells forgotten
     */
    public static int revokeWebTaughtSpells(ServerPlayer player) {
        PlayerSkillData data = getSkillData(player);
        java.util.Set<String> stillGranted = new java.util.HashSet<>();
        for (String nodeId : data.getUnlockedSkills().keySet()) {
            Skill node = SkillTrees.byId(nodeId);
            if (node == null) {
                continue;
            }
            for (SkillEffect effect : node.getEffects()) {
                if (effect instanceof SkillEffect.LearnSpell learn) {
                    var spell = at.koopro.wizardsandbeasts.spell.core.Spells.byId(learn.spellId());
                    stillGranted.add(spell != null ? spell.getId() : learn.spellId());
                }
            }
        }

        var spellData = player.getData(ModAttachments.SPELL_DATA.get());
        int forgotten = 0;
        for (String spellId : java.util.List.copyOf(data.getWebTaughtSpells())) {
            if (stillGranted.contains(spellId)) {
                continue;
            }
            spellData.forgetSpell(spellId);
            data.forgetWebTaughtSpell(spellId);
            forgotten++;
        }
        if (forgotten > 0) {
            at.koopro.wizardsandbeasts.network.spell.SpellDataSyncS2CPayload.syncToPlayer(player);
        }
        return forgotten;
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
