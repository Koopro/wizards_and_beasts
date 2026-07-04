package at.koopro.wizardsandbeasts.skill.vocation;

/*
 * ============================ AUDIT (Skill System v2 — Vocation layer) ============================
 * Audited the live skill system before building. Reality diverges hard from the prompt's assumptions; the
 * framework below is the in-scope ADAPTATION (user-approved Path 1). Full severity-tagged findings live in
 * AUDIT_PUNCHLIST.md ("Vocation Specialization Framework"); deltas in MIGRATION_DELTAS.md. Key facts:
 *
 *  1. Tree enum is `SkillTreeId` (NOT `SkillTree`): 7 values (the 5 wizard trees + GOBLIN_CRAFT + ELF_BOND),
 *     no `int maxPoints` — tree gating is by heritage `Audience`. We own only the 5 wizard trees; goblin/elf
 *     trees have no Vocation, so `unlockState` returns ALLOWED for them (ungated).
 *  2. Node type is `Skill` (NOT a `SkillNode` record): a builder-built final class with a String `id`,
 *     `List<SkillEffect>` + lazily-derived `List<SkillNodeEffect>`, `tier` (0 = root) and `maxLevel`
 *     (multi-level). `capstoneNodeId` is matched by Identifier *path* == node String id. We never author nodes.
 *  3. `PlayerSkillData` is NBT save/load (NOT a Codec record): `skillPoints` (live remaining counter),
 *     `totalPointsEarned`, `unlockedSkills: Map<String,Integer>` (level map, not a node set). UNTOUCHED.
 *  4. Points/refund: remaining == `skillPoints`; refund == `skillPoints += pointCost*level` (see
 *     PlayerSkillData.resetSkill). The §7 admin teardown refunds via `resetSkill`, NOT a spent-points ledger.
 *  5. Single unlock gate: `SkillSystemAPI.evaluateUnlock(ServerPlayer, Skill)` — one injection added there,
 *     after the existing point/prereq/tree checks; that method already short-circuits when SKILL_TREES is off.
 *  6. Effect application: `SkillAttributeApplicator` is a full-rebuild singleton (strips all `skill/` mods),
 *     unusable for a static profile. Commitment profiles use the parallel `VocationEffectApplicator`
 *     (`vocation/`-prefixed, keyed, idempotent) — disjoint from the skill applicator.
 *  7. Datapack loader mirrors `BestiaryEntryLoader` (AddServerReloadListenersEvent). Nodes themselves are
 *     Java-defined, so there is no `SkillNodeRegistry` to mirror — only the data-driven loaders.
 *  8. Sync mirrors `SkillDataSyncS2CPayload`/`ClientSkillDataState`: a PARALLEL `VocationDataSyncS2CPayload`
 *     + `ClientVocationCache`, registered alongside in `ModNetworkSkills`. Existing payloads untouched.
 *  9. Command root is `/wandb skill` (there is no `/skilltree`): the `vocation` sub-tree grafts onto
 *     `SkillCommands.register()`.
 * 10. Gating: `ModuleManager.isEnabled(Module.SKILL_TREES)`; dark_arts commit also needs `Module.DARK_ARTS`.
 * 11. Custom attributes `spell_damage_bonus`/`wand_compatibility_bonus` do NOT exist; only WAND_AFFINITY /
 *     DARK_CORRUPTION / BEAST_RESISTANCE do. Unmapped stat axes ship as `grantedAbilities` flags (TODO consumer),
 *     since `SkillNodeEffect` has no `grant_ability` variant and adding one is out of scope.
 * ==================================================================================================
 */

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillAttributeApplicator;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.sync.PlayerStateSyncService;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Orchestrates Vocation commit / clear: validation, the commitment stat profile, the §7 admin teardown, and
 * the sync trigger. Pure queries live in {@link VocationHelper}; this class owns the mutations.
 */
@NullMarked
public final class VocationManager {

    /** Which commitment slot a set operation targets. */
    public enum Slot { PRIMARY, SECONDARY }

    /** Secondary commitments carry the stat profile at reduced strength. */
    private static final double SECONDARY_PROFILE_SCALE = 0.5;

    /** Outcome of a commit attempt — every distinct rejection reason the command surfaces. */
    public enum CommitResult {
        OK,
        MODULE_DISABLED,
        UNKNOWN_VOCATION,
        SAME_AS_OTHER_SLOT,
        OPPOSED,
        DARK_ARTS_DISABLED,
        RESPEC_REQUIRED
    }

    private VocationManager() {}

    public static CommitResult commit(ServerPlayer player, Slot slot, Identifier vocationId) {
        if (!ModuleManager.isEnabled(Module.SKILL_TREES)) {
            return CommitResult.MODULE_DISABLED;
        }
        VocationDefinition vocation = VocationRegistry.get(vocationId);
        if (vocation == null) {
            return CommitResult.UNKNOWN_VOCATION;
        }

        PlayerVocationData data = VocationHelper.getData(player);
        Optional<Identifier> otherSlot = slot == Slot.PRIMARY ? data.secondary() : data.primary();

        if (otherSlot.filter(vocationId::equals).isPresent()) {
            return CommitResult.SAME_AS_OTHER_SLOT;
        }
        if (otherSlot.map(other -> VocationRegistry.areOpposed(vocationId, other)).orElse(false)) {
            return CommitResult.OPPOSED;
        }
        if (vocation.tree() == SkillTreeId.DARK_ARTS && !ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return CommitResult.DARK_ARTS_DISABLED;
        }

        // Reject overwriting a slot whose current Vocation already has Mastery progress — that needs a respec.
        Optional<Identifier> currentSlot = slot == Slot.PRIMARY ? data.primary() : data.secondary();
        if (currentSlot.isPresent()) {
            VocationDefinition current = VocationRegistry.get(currentSlot.get());
            if (current != null && hasMasteryUnlocked(player, current)) {
                return CommitResult.RESPEC_REQUIRED;
            }
        }

        PlayerVocationData updated = slot == Slot.PRIMARY
                ? data.withPrimary(Optional.of(vocationId))
                : data.withSecondary(Optional.of(vocationId));
        player.setData(ModAttachments.VOCATION_DATA.get(), updated);

        // Rebuild both commitment profiles from scratch to stay idempotent: primary at full
        // strength, secondary reduced. grantedAbilities flags are consumed live by
        // VocationAbilityHooks (spell modifiers, corruption accrual, harvest/beast handlers).
        VocationEffectApplicator.removeAll(player);
        updated.primary().map(VocationRegistry::get)
                .ifPresent(v -> VocationEffectApplicator.apply(player, v));
        updated.secondary().map(VocationRegistry::get)
                .ifPresent(v -> VocationEffectApplicator.apply(player, v, SECONDARY_PROFILE_SCALE));

        PlayerStateSyncService.syncVocations(player);
        return CommitResult.OK;
    }

    /**
     * Full admin teardown: strip the commitment profile, remove every now-invalid Mastery node of the cleared
     * Vocation(s) and refund their points via {@link PlayerSkillData#resetSkill}, clear both slots, resync.
     */
    public static void clear(ServerPlayer player) {
        PlayerVocationData data = VocationHelper.getData(player);
        PlayerSkillData skillData = player.getData(ModAttachments.SKILL_DATA.get());

        VocationEffectApplicator.removeAll(player);

        data.primary().map(VocationRegistry::get).ifPresent(v -> refundMasteryNodes(skillData, v));
        data.secondary().map(VocationRegistry::get).ifPresent(v -> refundMasteryNodes(skillData, v));

        player.setData(ModAttachments.VOCATION_DATA.get(), PlayerVocationData.EMPTY);

        // Mastery nodes were removed from unlockedSkills → rebuild skill-derived attributes/bonuses.
        SkillAttributeApplicator.applyAll(player);
        PlayerStateSyncService.syncSkills(player);
        PlayerStateSyncService.syncVocations(player);
    }

    /** True if the player has any unlocked Mastery-band node in the Vocation's tree. */
    public static boolean hasMasteryUnlocked(ServerPlayer player, VocationDefinition vocation) {
        PlayerSkillData data = player.getData(ModAttachments.SKILL_DATA.get());
        for (Skill skill : SkillTrees.getTree(vocation.tree())) {
            if (data.hasSkill(skill.getId()) && VocationHelper.band(skill) == VocationHelper.Band.MASTERY) {
                return true;
            }
        }
        return false;
    }

    private static void refundMasteryNodes(PlayerSkillData data, VocationDefinition vocation) {
        for (Skill skill : SkillTrees.getTree(vocation.tree())) {
            if (data.hasSkill(skill.getId()) && VocationHelper.band(skill) == VocationHelper.Band.MASTERY) {
                data.resetSkill(skill.getId()); // refunds pointCost*level into skillPoints
            }
        }
    }

    /** The committed Vocation (definition) for a slot, or null if empty / undefined. */
    public static @Nullable VocationDefinition committed(ServerPlayer player, Slot slot) {
        Optional<Identifier> id = slot == Slot.PRIMARY
                ? VocationHelper.getPrimary(player)
                : VocationHelper.getSecondary(player);
        return id.map(VocationRegistry::get).orElse(null);
    }
}
