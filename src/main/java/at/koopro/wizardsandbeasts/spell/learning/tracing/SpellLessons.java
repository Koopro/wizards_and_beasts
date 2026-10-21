package at.koopro.wizardsandbeasts.spell.learning.tracing;

import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillEffect;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The lessons the skill web owes a player: spells named by a {@code learn_spell} effect on an allocated
 * node that the player does not yet know.
 *
 * <p><b>Derived, not stored.</b> Allocation is already persisted and so is spell knowledge; "owed" is the
 * difference between them. Nothing new is saved, so there is no migration: a save from before tracing
 * already knows every spell its nodes taught and therefore owes nothing, a refunded node simply stops
 * owing, and the client computes the same answer from the two attachments it already mirrors.
 */
@NullMarked
public final class SpellLessons {

    private SpellLessons() {
    }

    /** Spell id → the node that owes it, in allocation order. */
    public static Map<String, String> owed(PlayerSkillData skills, PlayerSpellData spells) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> unlocked : skills.getUnlockedSkills().entrySet()) {
            String nodeId = unlocked.getKey();
            Skill node = SkillTrees.byId(nodeId);
            if (node == null || unlocked.getValue() <= 0) {
                continue;
            }
            for (SkillEffect effect : node.getEffects()) {
                if (effect instanceof SkillEffect.LearnSpell learn) {
                    Spell spell = Spells.byId(learn.spellId());
                    if (spell != null && !spells.knowsSpell(spell.getId())) {
                        out.putIfAbsent(spell.getId(), nodeId);
                    }
                }
            }
        }
        return out;
    }

    /** The node owing {@code spellId}, or null when no allocated node does. */
    public static @Nullable String owingNode(PlayerSkillData skills, PlayerSpellData spells, String spellId) {
        Spell spell = Spells.byId(spellId);
        return spell == null ? null : owed(skills, spells).get(spell.getId());
    }

    /** The spell a node teaches, or null when it teaches none. */
    public static @Nullable String spellOf(Skill node) {
        for (SkillEffect effect : node.getEffects()) {
            if (effect instanceof SkillEffect.LearnSpell learn) {
                Spell spell = Spells.byId(learn.spellId());
                return spell != null ? spell.getId() : learn.spellId();
            }
        }
        return null;
    }
}
