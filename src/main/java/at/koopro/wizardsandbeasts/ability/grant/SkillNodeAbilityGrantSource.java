package at.koopro.wizardsandbeasts.ability.grant;

import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillEffect;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill-node grant source: allocated nodes' {@link SkillEffect.GrantAbility} (new) and the legacy
 * free-string {@link SkillEffect.UnlockAbility}, bridged so existing node abilities stay source-tracked.
 * Read-only over allocated skill data.
 */
@NullMarked
public final class SkillNodeAbilityGrantSource implements AbilityGrantSource {

    @Override
    public AbilityGrants.Source source() {
        return AbilityGrants.Source.SKILL_NODE;
    }

    @Override
    public List<String> grantsFor(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        PlayerSkillData data = SkillSystemAPI.getSkillData(player);
        for (String nodeId : data.getUnlockedSkills().keySet()) {
            Skill node = SkillTrees.byId(nodeId);
            if (node == null) {
                continue;
            }
            for (SkillEffect effect : node.getEffects()) {
                if (effect instanceof SkillEffect.GrantAbility grant) {
                    out.add(grant.ability().id());
                } else if (effect instanceof SkillEffect.UnlockAbility unlock) {
                    out.add(unlock.abilityId());
                }
            }
        }
        return out;
    }
}
