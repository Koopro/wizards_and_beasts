package at.koopro.wizardsandbeasts.bestiary.harvest;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import org.jspecify.annotations.NullMarked;

/**
 * Whether one harvest rule yields this time. Pure — no player, no loot context, no Minecraft — so the
 * thing a player actually experiences can be pinned by unit tests without a running world.
 *
 * <p>The order of the checks is deliberate and is the anti-abuse design:
 *
 * <ol>
 *   <li><b>Tier</b> first, because it is the point of the feature and is free to evaluate.</li>
 *   <li><b>Cooldown</b> second, and <em>before</em> the chance roll. Rolling first would let a farm
 *       burn attempts against a lockout it cannot pass, and — worse — would consume randomness whose
 *       consumption a player could observe.</li>
 *   <li><b>Chance</b> last, so the roll is only spent on a kill that could actually yield.</li>
 * </ol>
 *
 * <p>The cooldown is what answers "kill the same mob type repeatedly" and "build a farm". It is keyed
 * per player <em>and</em> per bestiary entry, so mastering a unicorn never throttles a phoenix, and it
 * is stored on the persisted bestiary attachment rather than in memory — a lockout you could clear by
 * relogging is not a lockout.
 */
@NullMarked
public final class HarvestGate {

    private HarvestGate() {}

    /** Sentinel for "this player has never harvested this entry". */
    public static final long NEVER = Long.MIN_VALUE;

    /**
     * Everything except the chance roll: the two deterministic gates. Split out so a test can assert
     * the gating without owning a random source, and so the caller only spends a roll when it matters.
     *
     * @param held      the player's tier for this rule's entry
     * @param lastTick  game time of the player's last successful harvest of this entry, or {@link #NEVER}
     * @param nowTick   current game time
     */
    public static boolean isEligible(HarvestRule rule, DiscoveryTier held, long lastTick, long nowTick) {
        if (!rule.tierSatisfiedBy(held)) {
            return false;
        }
        return isOffCooldown(rule, lastTick, nowTick);
    }

    /**
     * Whether the per-entry lockout has expired.
     *
     * <p>Tolerates {@code nowTick} being <em>earlier</em> than {@code lastTick}. That is not a
     * hypothetical: game time is per-level, so walking into the Nether can move it backwards by a large
     * margin, and a naive {@code now - last >= cooldown} would then lock a player out for the rest of
     * the world's life. A clock that has gone backwards is treated as a fresh start.
     */
    public static boolean isOffCooldown(HarvestRule rule, long lastTick, long nowTick) {
        if (rule.cooldownSeconds() <= 0 || lastTick == NEVER) {
            return true;
        }
        if (nowTick < lastTick) {
            return true;
        }
        return nowTick - lastTick >= (long) rule.cooldownSeconds() * 20L;
    }

    /**
     * The full decision, with the chance roll supplied by the caller.
     *
     * @param roll a value in {@code [0, 1)}; the rule yields when it is below {@link HarvestRule#chance}
     */
    public static boolean yields(HarvestRule rule, DiscoveryTier held, long lastTick, long nowTick, float roll) {
        return isEligible(rule, held, lastTick, nowTick) && roll < rule.chance();
    }

    /**
     * Stack size for a yield that has already been decided.
     *
     * @param roll a value in {@code [0, 1)}, mapped across the inclusive count range
     */
    public static int countFor(HarvestRule rule, float roll) {
        int span = rule.maxCount() - rule.minCount();
        if (span <= 0) {
            return rule.minCount();
        }
        // Clamped rather than trusted: a roll of exactly 1.0 would otherwise index one past the top.
        int offset = (int) (Math.max(0.0f, Math.min(0.9999f, roll)) * (span + 1));
        return rule.minCount() + Math.min(span, offset);
    }
}
