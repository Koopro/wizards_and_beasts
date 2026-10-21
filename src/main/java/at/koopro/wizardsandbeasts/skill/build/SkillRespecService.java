package at.koopro.wizardsandbeasts.skill.build;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.currency.billing.GringottsBilling;
import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillEffect;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.sync.PlayerStateSyncService;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The two respec doors, quoted and then carried out — one class so the command, the GUI and the preview can never
 * disagree about what a respec does. See {@link RespecPricing} for the prices and why points are always refunded
 * in full.
 *
 * <p>A quote answers everything a player should know before paying: points back, the fee, the spells the web
 * taught that they will forget, the dark corruption they keep (study marks you; a refund cannot unmark), and — for
 * an unlearn that would cut the web — which nodes would be left floating.
 */
@NullMarked
public final class SkillRespecService {

    /**
     * @param reason          {@code ok}, or why the door is shut: {@code module_disabled}, {@code unknown_skill},
     *                        {@code not_allocated}, {@code would_strand}, {@code nothing_to_respec}
     * @param refundPoints    skill points returned
     * @param feeKnuts        what it will cost; 0 when free
     * @param free            whether this is the character's free respec
     * @param spellsForgotten web-taught spells the player will lose
     * @param corruptionKept  dark corruption the refunded nodes accrued, which stays
     * @param stranded        nodes an unlearn would cut off (non-empty only with {@code would_strand})
     */
    public record Quote(String reason, int refundPoints, long feeKnuts, boolean free,
                        List<String> spellsForgotten, float corruptionKept, Set<String> stranded) {
        public boolean allowed() {
            return "ok".equals(reason);
        }

        static Quote refused(String reason) {
            return new Quote(reason, 0, 0L, false, List.of(), 0f, Set.of());
        }
    }

    /** {@code ok} or {@code cannot_afford}, plus the quote it was carried out against. */
    public record Outcome(String result, Quote quote) {
        public boolean done() {
            return "ok".equals(result);
        }
    }

    private SkillRespecService() {}

    // ── quotes ──────────────────────────────────────────────────────────────────────────────────────

    public static Quote quoteUnlearn(ServerPlayer player, String skillId) {
        if (!ModuleManager.isEnabled(Module.SKILL_TREES)) {
            return Quote.refused("module_disabled");
        }
        Skill skill = SkillTrees.byId(skillId);
        if (skill == null) {
            return Quote.refused("unknown_skill");
        }
        PlayerSkillData data = SkillSystemAPI.getSkillData(player);
        Map<String, Integer> allocated = data.getUnlockedSkills();
        if (allocated.getOrDefault(skillId, 0) <= 0) {
            return Quote.refused("not_allocated");
        }
        Set<String> stranded = Builds.strandedBy(skillId, allocated);
        if (!stranded.isEmpty()) {
            return new Quote("would_strand", 0, 0L, false, List.of(), 0f, stranded);
        }
        Map<String, Integer> after = new HashMap<>(allocated);
        int level = after.get(skillId);
        if (level <= 1) {
            after.remove(skillId);
        } else {
            after.put(skillId, level - 1);
        }
        int refund = skill.getPointCost();
        long fee = RespecPricing.unlearnFee(refund, Config.skillUnlearnCostKnutsPerPoint,
                Config.skillRespecCostKnuts, billingOn());
        return new Quote("ok", refund, fee, false, forgotten(data, after), darkStudy(skill, 1), Set.of());
    }

    public static Quote quoteRespec(ServerPlayer player) {
        if (!ModuleManager.isEnabled(Module.SKILL_TREES)) {
            return Quote.refused("module_disabled");
        }
        PlayerSkillData data = SkillSystemAPI.getSkillData(player);
        if (data.getUnlockedSkills().isEmpty()) {
            return Quote.refused("nothing_to_respec");
        }
        int refund = 0;
        float corruption = 0f;
        for (Map.Entry<String, Integer> entry : data.getUnlockedSkills().entrySet()) {
            Skill skill = SkillTrees.byId(entry.getKey());
            if (skill != null) {
                refund += skill.getPointCost() * entry.getValue();
                corruption += darkStudy(skill, entry.getValue());
            }
        }
        boolean free = data.isFreeRespecAvailable();
        long fee = RespecPricing.fullRespecFee(free, Config.skillRespecCostKnuts, billingOn());
        return new Quote("ok", refund, fee, free && billingOn() && Config.skillRespecCostKnuts > 0,
                forgotten(data, Map.of()), corruption, Set.of());
    }

    // ── doors ───────────────────────────────────────────────────────────────────────────────────────

    /** Unlearns one level of {@code skillId}, charging the quoted fee first. */
    public static Outcome unlearn(ServerPlayer player, String skillId) {
        Quote quote = quoteUnlearn(player, skillId);
        if (!quote.allowed()) {
            return new Outcome(quote.reason(), quote);
        }
        // Charged before the refund, so a failed payment leaves the web exactly as it was.
        if (quote.feeKnuts() > 0 && !GringottsBilling.charge(player, MoneyFlow.SKILL_RESPEC, quote.feeKnuts())) {
            return new Outcome("cannot_afford", quote);
        }
        SkillSystemAPI.getSkillData(player).unlearnLevel(skillId);
        reconcile(player);
        return new Outcome("ok", quote);
    }

    /** Refunds the whole web, spending the free respec if the character still has it. */
    public static Outcome respec(ServerPlayer player) {
        Quote quote = quoteRespec(player);
        if (!quote.allowed()) {
            return new Outcome(quote.reason(), quote);
        }
        if (quote.feeKnuts() > 0 && !GringottsBilling.charge(player, MoneyFlow.SKILL_RESPEC, quote.feeKnuts())) {
            return new Outcome("cannot_afford", quote);
        }
        PlayerSkillData data = SkillSystemAPI.getSkillData(player);
        data.resetAll();
        // Only a respec that would otherwise have been charged spends the free one; with billing off every
        // respec is free anyway, and burning the token then would take it from a server that later turns fees on.
        if (billingOn() && Config.skillRespecCostKnuts > 0) {
            data.markFreeRespecUsed();
        }
        reconcile(player);
        return new Outcome("ok", quote);
    }

    /**
     * Everything an allocation change can affect: take back the web's spells (the refund alone does not — see
     * {@link SkillSystemAPI#reconcileDerivedEffects}), re-derive attributes, then sync points and grants.
     */
    private static void reconcile(ServerPlayer player) {
        SkillSystemAPI.reconcileDerivedEffects(player);
        PlayerStateSyncService.syncSkills(player);
        PlayerStateSyncService.syncAbilityGrants(player); // refund strips SKILL_NODE-source grants
    }

    // ── pieces ──────────────────────────────────────────────────────────────────────────────────────

    public static boolean billingOn() {
        return ModuleManager.isEnabled(Module.GRINGOTTS);
    }

    /** Effective per-point unlearn fee, for the client (0 when billing is off). */
    public static int effectiveUnlearnPerPoint() {
        return billingOn() ? Config.skillUnlearnCostKnutsPerPoint : 0;
    }

    /** Effective full-respec fee for this player's next respec, for the client. */
    public static int effectiveRespecFee(PlayerSkillData data) {
        return (int) RespecPricing.fullRespecFee(data.isFreeRespecAvailable(), Config.skillRespecCostKnuts, billingOn());
    }

    /** Web-taught spells no node in {@code after} would still grant: what the player will forget. */
    private static List<String> forgotten(PlayerSkillData data, Map<String, Integer> after) {
        Set<String> stillGranted = new HashSet<>();
        for (String nodeId : after.keySet()) {
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
        List<String> out = new ArrayList<>();
        for (String spellId : data.getWebTaughtSpells()) {
            if (!stillGranted.contains(spellId)) {
                out.add(spellId);
            }
        }
        out.sort(null);
        return out;
    }

    private static float darkStudy(Skill skill, int levels) {
        float total = 0f;
        for (SkillEffect effect : skill.getEffects()) {
            if (effect instanceof SkillEffect.DarkStudy study) {
                total += study.corruption() * levels;
            }
        }
        return total;
    }
}
