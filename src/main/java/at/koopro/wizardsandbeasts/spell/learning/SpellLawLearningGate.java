package at.koopro.wizardsandbeasts.spell.learning;

import at.koopro.wizardsandbeasts.corruption.DarkCorruptionService;
import at.koopro.wizardsandbeasts.ministry.trace.LegalClass;
import at.koopro.wizardsandbeasts.ministry.trace.SpellLaw;
import at.koopro.wizardsandbeasts.ministry.trace.SpellLawRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What the law asks of someone before a book will teach them.
 *
 * <p>Spell legality and spell acquisition disagreed: {@code spell_law} has classified spells since the
 * Ministry layer landed, and nothing on the learning path had ever read it. A first-year who found the
 * right book could read Avada Kedavra out of it with no more friction than Lumos, which makes the
 * classification decorative.
 *
 * <p>The rule is deliberately not "you may not learn dark magic". The Wizarding World is full of people
 * who learned things they should not have; Riddle read his way into most of it, and Snape invented
 * Sectumsempra as a schoolboy. What it is not full of is people who picked up an Unforgivable by
 * accident. So the gate asks for <em>study</em>, and reads that study off the Dark Arts web the player
 * has already been spending points in:
 *
 * <ul>
 *   <li>{@link LegalClass#UNRESTRICTED} — nothing. Lumos is Lumos.</li>
 *   <li>{@link LegalClass#RESTRICTED} — nothing extra either, and that is a decision rather than an
 *       omission. Twelve spells are restricted, including Stupefy and Expelliarmus, and each already
 *       carries whatever prerequisite its author gave it. A blanket gate here would lock ordinary
 *       duelling behind the Dark Arts, which is precisely backwards: restricted means the Ministry
 *       regulates its <em>use</em>, not that the knowledge is hidden.</li>
 *   <li>{@link LegalClass#DARK} — the student must have begun studying the Dark Arts at all.</li>
 *   <li>{@link LegalClass#UNFORGIVABLE} — they must have learned to control a curse first. This is the
 *       same order the skill web already enforces on its own Unforgivable nodes, which sit behind
 *       {@code curse_control}; without this, a book was a way around the study the web charges for.</li>
 * </ul>
 *
 * <p>Nothing here is a second morality system. The classification is the existing datapack's, the study
 * is the existing skill web's, and the stain is the existing {@link DarkCorruptionService} — the same
 * one the web's own {@code dark_study} nodes pay into.
 */
@NullMarked
public final class SpellLawLearningGate {

    /**
     * Having begun the Dark Arts at all: the entry node of that web, and the cheapest thing in it.
     *
     * <p>Named rather than derived from a node count, because "you have started studying this" is a
     * sentence a player can be told, and "you have three nodes somewhere in this tree" is not.
     */
    public static final String STUDY_DARK_ARTS = "dark_knowledge";

    /**
     * Having learned to hold a curse steady. The web already puts every Unforgivable behind this.
     */
    public static final String STUDY_CURSE_CONTROL = "curse_control";

    /**
     * What reading a dark book costs, over and above what casting will.
     *
     * <p>Kept well under the skill web's own prices — {@code dark_knowledge} charges 2 and
     * {@code avada_kedavra_unlock} charges 12 — because a book is a shortcut past the study, not a
     * replacement for it, and because the Ministry's real interest is in use rather than possession.
     * The point is that knowing changes you a little; casting changes you a lot.
     */
    private static final float STAIN_DARK = 1.0f;
    private static final float STAIN_UNFORGIVABLE = 4.0f;

    private SpellLawLearningGate() {}

    /** The law's reading of this spell, authored or derived from its category. Never null. */
    public static SpellLaw lawFor(Spell spell) {
        return SpellLawRegistry.lawFor(spell.getId(), spell.getCategory());
    }

    /**
     * Why the law refuses to let this player learn this spell, or null when it does not.
     *
     * <p>Pure: safe to call for a preview. {@code SpellSourceItem} asks before the player has read
     * anything so the refusal can be shown on the first right-click, which means this must not charge
     * anyone for picking a book up and putting it down again.
     *
     * @param player the student, or null where no player is in hand (the data-only validate overloads)
     */
    public static @Nullable Component refusal(@Nullable ServerPlayer player, Spell spell) {
        if (player == null) {
            return null;
        }
        LegalClass legalClass = lawFor(spell).legalClass();
        return switch (legalClass) {
            case UNRESTRICTED, RESTRICTED -> null;
            case DARK -> hasStudied(player, STUDY_DARK_ARTS)
                    ? null
                    : Component.translatable("spell.wizards_and_beasts.learning.refused.dark");
            case UNFORGIVABLE -> hasStudied(player, STUDY_CURSE_CONTROL)
                    ? null
                    : Component.translatable("spell.wizards_and_beasts.learning.refused.unforgivable");
        };
    }

    /**
     * Marks a student who has just read something they should not have.
     *
     * <p>Called from the moment the spell is actually learned, never from the preview. Learning files
     * no Ministry incident and is not meant to: the Trace answers magic that was performed, and a
     * wizard who has read about a curse has not cast one. What it does is stain them, which is the
     * consequence the mod already models and which creatures, the Patronus and the wand all read.
     *
     * @return the corruption charged, for callers that want to report it
     */
    public static float stainForLearning(ServerPlayer player, Spell spell) {
        float stain = switch (lawFor(spell).legalClass()) {
            case UNRESTRICTED, RESTRICTED -> 0.0f;
            case DARK -> STAIN_DARK;
            case UNFORGIVABLE -> STAIN_UNFORGIVABLE;
        };
        if (stain > 0.0f) {
            DarkCorruptionService.accrue(player, stain);
        }
        return stain;
    }

    /**
     * Whether the player has taken a named node.
     *
     * <p>Fails open when the node is not in the loaded datapack at all: a pack that strips the Dark
     * Arts web should not leave dark spells permanently unlearnable by a gate pointing at something
     * that no longer exists. The module switch and the spell's own requirements still apply.
     */
    private static boolean hasStudied(ServerPlayer player, String skillId) {
        if (SkillTrees.byId(skillId) == null) {
            return true;
        }
        PlayerSkillData skills = player.getData(ModAttachments.SKILL_DATA.get());
        return skills.hasSkill(skillId);
    }
}
