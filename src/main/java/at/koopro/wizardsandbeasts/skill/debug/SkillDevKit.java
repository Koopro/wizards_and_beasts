package at.koopro.wizardsandbeasts.skill.debug;

import at.koopro.wizardsandbeasts.command.debug.dev.DevLog;
import at.koopro.wizardsandbeasts.command.debug.dev.FeatureDevKit;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.sync.PlayerStateSyncService;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Points to spend, not nodes already spent.
 *
 * <p>Deliberately the opposite of what the spell kit does. Unlocking every node would make the web
 * untestable — you could no longer see a prerequisite refuse, a point cost apply, or a tree gate
 * hold, which is most of what there is to test about a skill web. Handing over the points instead
 * leaves every one of those behaviours intact and simply removes the grind in front of them.
 *
 * <p>{@code /wandb player skill force_unlock} is there for the case where you genuinely want a node
 * without earning it.
 */
@NullMarked
public final class SkillDevKit implements FeatureDevKit {

    @Override
    public String id() {
        return "skills";
    }

    @Override
    public String title() {
        return "Skills";
    }

    @Override
    public String summary() {
        return "Hand over the full point budget, leaving the unlock rules to still be testable.";
    }

    @Override
    public void open(ServerPlayer target, DevLog log) {
        PlayerSkillData data = target.getData(ModAttachments.SKILL_DATA.get());
        int have = data.getSkillPoints();
        int want = SkillSystemAPI.MAX_SKILL_POINTS;
        if (have >= want) {
            log.skip("already holding " + have + " skill points");
            return;
        }
        SkillSystemAPI.awardPoints(target, want - have);
        PlayerStateSyncService.syncSkills(target);
        log.changed("skill points", data.getSkillPoints());
    }

    /**
     * A full respec rather than a wipe: points are returned rather than lost.
     *
     * <p>{@code PlayerSkillData.resetAll} refunds as it clears, which is what makes this usable
     * mid-session: reset, then immediately try the next build with the same budget.
     */
    @Override
    public void reset(ServerPlayer target, DevLog log) {
        PlayerSkillData data = target.getData(ModAttachments.SKILL_DATA.get());
        int unlocked = data.getUnlockedSkills().size();
        data.resetAll();
        // The refund does not take back the spells those nodes taught -- see the same call in
        // SkillCommands.respec. A dev kit that left them behind would quietly build a save that no
        // ordinary play could produce, which is the one thing a testing tool must not do.
        int forgotten = SkillSystemAPI.revokeWebTaughtSpells(target);
        // Reconcile after the wipe, not before: it strips the effects the now-gone nodes were
        // granting, and running it first would reinstate them from data about to be deleted.
        SkillSystemAPI.reconcileDerivedEffects(target);
        PlayerStateSyncService.syncSkills(target);
        // The refund also strips every SKILL_NODE-sourced ability grant.
        PlayerStateSyncService.syncAbilityGrants(target);
        log.changed("skill web reset",
                unlocked + " nodes cleared, " + forgotten + " web-taught spell(s) forgotten");
    }
}
