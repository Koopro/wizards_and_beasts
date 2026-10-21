package at.koopro.wizardsandbeasts.skill.build.capstone;

import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Scholar capstone, {@code scholars_method}: reading ahead.
 *
 * <p>Every spell that waits on another's proficiency — Bombarda on a Proficient Diffindo, Crucio on a Mastered
 * Incendio — opens after half the prerequisite's practice. It changes <em>which spells a player can learn
 * when</em>, not how hard any spell hits: a Scholar reaches the deeper curriculum a season early and still has to
 * practise it like anyone else. It composes with {@code unlock_spell_early} by taking the lower gate.
 *
 * <p>Wired at {@code SpellRequirement.isMet}, the single place a proficiency prerequisite is judged.
 */
@NullMarked
public final class ScholarsMethod {

    public static final String ABILITY = "scholars_method";

    private ScholarsMethod() {}

    /** The practice a Scholar needs where anyone else needs {@code requiredCasts}. */
    public static int gate(int requiredCasts) {
        return requiredCasts <= 0 ? requiredCasts : (requiredCasts + 1) / 2;
    }

    public static int gateFor(ServerPlayer player, int requiredCasts) {
        return SkillSystemAPI.hasAbility(player, ABILITY) ? Math.min(requiredCasts, gate(requiredCasts)) : requiredCasts;
    }
}
