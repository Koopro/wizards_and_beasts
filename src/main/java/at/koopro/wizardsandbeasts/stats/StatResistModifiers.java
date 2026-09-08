package at.koopro.wizardsandbeasts.stats;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;

/**
 * The mind-magic read for {@link PlayerStat#WILLPOWER}, and the module gate that goes with it.
 *
 * <p>The sibling of {@link StatCastModifiers}: that class is every stat the cast pipeline spends, this one
 * is the stat the resist rolls spend. Imperius throw-off, Occlumency defence and the Resolve pool itself all
 * read WILLPOWER through {@link StatEffects}, and each of the four call sites used to compute the trait
 * itself.
 *
 * <h2>Why the gate matters here more than anywhere else</h2>
 * Every other consumer of this system already asks {@link Module#PLAYER_STATS} first — the cast modifiers,
 * every training hook, the tuition discount — and each of them degrades to <em>nothing</em> when the module
 * is off, which is the right shape for a modifier. The resist rolls cannot degrade to nothing, because they
 * do not add a modifier; they scale a chance. With the module off, an ungated read returns WILLPOWER 0
 * forever — the trait is never displayed, never trained ({@link StatTraining} is a no-op) and never
 * settable — so {@link StatEffects#resistScalar} pinned at its floor and the Imperius Curse became roughly
 * two and a half times harder to throw off on a server that had switched player stats <em>off</em>.
 *
 * <p>So the fallback is the top of the range rather than the bottom: with the module off a wizard rolls as
 * though fully trained, which restores the flat 0–100 pool and the 30/15 attempt costs the Imperius code
 * used before this system existed — the economy {@link StatEffects#RESOLVE_COST_BREAK_FREE} is documented
 * against. Switching a module off must never make the game harder than never having shipped it.
 */
@NullMarked
public final class StatResistModifiers {

    private StatResistModifiers() {}

    /**
     * The WILLPOWER value every mind-magic roll should use: the player's own trait, or the top of the range
     * when {@link Module#PLAYER_STATS} is off and the trait is unreachable.
     */
    public static int resistTrait(Player player) {
        return ModuleManager.isEnabled(Module.PLAYER_STATS)
                ? PlayerStatsAPI.getStat(player, PlayerStat.WILLPOWER)
                : PlayerStatsData.MAX_VALUE;
    }

    /** Multiplier on a mind-magic resist chance for this player. */
    public static float resistScalar(Player player) {
        return StatEffects.resistScalar(resistTrait(player));
    }

    /** Ceiling of this player's Resolve pool. Never 0 — division by it is safe. */
    public static float maxResolve(Player player) {
        return StatEffects.maxResolve(resistTrait(player));
    }

    /** Resolve this player regenerates per tick while not under control. */
    public static float resolveRegenPerTick(Player player) {
        return StatEffects.resolveRegenPerTick(resistTrait(player));
    }

    /** Resolve this player spends throwing off a mind-control curse. */
    public static float resolveCostToBreakFree(Player player) {
        return StatEffects.resolveCostToBreakFree(resistTrait(player));
    }

    /** Resolve this player spends on a resist attempt that failed. */
    public static float resolveCostOfFailedAttempt(Player player) {
        return StatEffects.resolveCostOfFailedAttempt(resistTrait(player));
    }

    /** How full this player's Resolve pool is, as a fraction of its ceiling. Clamped to [0, 1]. */
    public static float resolveCharge(Player player, float resolve) {
        return StatEffects.resolveCharge(resolve, resistTrait(player));
    }
}
