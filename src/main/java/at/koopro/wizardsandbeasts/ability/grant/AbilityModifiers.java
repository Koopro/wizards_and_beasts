package at.koopro.wizardsandbeasts.ability.grant;

import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillEffect;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/**
 * Read surface for ability-scoped refinements: the summed per-level magnitude an allocated web puts on a
 * given ({@link AbilityKey}, {@link AbilityModifierAxis}) pair via {@link SkillEffect.AbilityRefinement}.
 *
 * <p>Refinement is a scoping <b>mechanism</b> only — it ships with zero authored nodes and zero consumers.
 * A future content pass authors {@code ability_refinement} effects and wires an ability implementation to
 * read {@link #get}; this layer just aggregates them, mirroring how {@link SkillSystemAPI} aggregates the
 * category multipliers.
 */
@NullMarked
public final class AbilityModifiers {

    private AbilityModifiers() {}

    /**
     * Summed refinement magnitude for {@code key} on {@code axis} across the player's allocated nodes,
     * scaled by each node's level (matching the per-level convention of the other skill effects). Returns
     * {@code 0} when nothing refines it (additive identity).
     */
    public static float get(ServerPlayer player, AbilityKey key, AbilityModifierAxis axis) {
        float total = 0f;
        PlayerSkillData data = SkillSystemAPI.getSkillData(player);
        for (Map.Entry<String, Integer> entry : data.getUnlockedSkills().entrySet()) {
            Skill node = SkillTrees.byId(entry.getKey());
            if (node == null) {
                continue;
            }
            int level = entry.getValue();
            for (SkillEffect effect : node.getEffects()) {
                if (effect instanceof SkillEffect.AbilityRefinement refinement
                        && refinement.axis() == axis
                        && refinement.ability().equals(key)) {
                    total += refinement.magnitudePerLevel() * level;
                }
            }
        }
        return total;
    }
}
