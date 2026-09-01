package at.koopro.wizardsandbeasts.stats;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * The gameplay events that train a {@link PlayerStat}, and how much each is worth.
 *
 * <p>Named entry points rather than raw {@link PlayerStatsAPI#addTrainingProgress} calls at the call
 * sites, so every magnitude in the mod lives in this one file and the hook sites stay one readable
 * line. Each method is a no-op when {@link Module#PLAYER_STATS} is off — training that nobody can see
 * would still cost a sync packet per event.
 *
 * <p><b>Reading the constants.</b> They are raw amounts <em>before</em>
 * {@link StatTrainingScaler}'s S-curve, so they are not "points per event" — the curve makes each
 * point dearer than the last. {@code StatTrainingReachabilityTest} pins the real event counts; the
 * figures quoted below come from it rather than from intuition. Because the scaler returns zero at
 * 100, the last stretch is asymptotic by design: the practical ceiling is somewhere near 85–90, and
 * 100 is reachable only through {@code /wandb player stats set}.
 *
 * <p><b>Why {@link Source} is an enum.</b> The character sheet has to tell a player what raises a
 * stat, and the only honest answer is the list of hooks that actually call in here. Written out in
 * the GUI it would be a second list free to drift from this one — which is the shape of bug that
 * left REFLEXES untrainable for a release. {@link Source#forStat} derives the tooltip from the same
 * constants the hooks spend, so a source that stops firing cannot keep being advertised.
 */
@NullMarked
public final class StatTraining {

    /**
     * A spell that connected. The most frequent trainable event in the mod, so the smallest payout:
     * 307 hits to PRECISION 25, 819 to 50, 1 965 to 75.
     */
    static final float PRECISION_PER_SPELL_HIT = 0.10f;

    /**
     * A Protego ward that turned a spell aside. Needs someone shooting at you and a well-timed
     * shield, so it pays far better: 88 deflects to REFLEXES 25, 234 to 50, 561 to 75.
     */
    static final float REFLEXES_PER_DEFLECT = 0.35f;

    /**
     * A second spent fighting the Imperius Curse and failing to break it. Fires at most once per
     * second while controlled: 154 seconds under the curse to WILLPOWER 25, 410 to 50.
     */
    static final float WILLPOWER_PER_IMPERIUS_ENDURED = 0.20f;

    /**
     * A Legilimency intrusion repelled. Rare and adversarial, so it pays 2.5× enduring:
     * 62 defences to WILLPOWER 25, 164 to 50.
     */
    static final float WILLPOWER_PER_MIND_DEFENDED = 0.50f;

    /** Every way a stat can be trained by playing, with what it trains and what it is worth. */
    public enum Source {
        SPELL_HIT(PlayerStat.PRECISION, PRECISION_PER_SPELL_HIT, "spell_hit"),
        PROTEGO_DEFLECT(PlayerStat.REFLEXES, REFLEXES_PER_DEFLECT, "protego_deflect"),
        IMPERIUS_ENDURED(PlayerStat.WILLPOWER, WILLPOWER_PER_IMPERIUS_ENDURED, "imperius_endured"),
        MIND_DEFENDED(PlayerStat.WILLPOWER, WILLPOWER_PER_MIND_DEFENDED, "mind_defended");

        private final PlayerStat stat;
        private final float rawAmount;
        private final String id;

        Source(PlayerStat stat, float rawAmount, String id) {
            this.stat = stat;
            this.rawAmount = rawAmount;
            this.id = id;
        }

        public PlayerStat stat() { return stat; }

        /** Raw, pre-curve amount this event is worth. */
        public float rawAmount() { return rawAmount; }

        /** Lang key for the one-line description shown in the stat's tooltip. */
        public String descriptionKey() {
            return "stat.wizards_and_beasts.source." + id;
        }

        public Component description() {
            return Component.translatable(descriptionKey());
        }

        /** The sources that feed one stat, in declaration order. Empty for untrainable stats. */
        public static List<Source> forStat(PlayerStat stat) {
            List<Source> out = new ArrayList<>(2);
            for (Source source : values()) {
                if (source.stat == stat) out.add(source);
            }
            return out;
        }
    }

    private StatTraining() {}

    /** A spell cast by this player struck its target. Trains PRECISION. */
    public static void onSpellHit(ServerPlayer player) {
        train(player, Source.SPELL_HIT);
    }

    /** This player's Protego ward deflected an incoming spell. Trains REFLEXES. */
    public static void onSpellDeflected(ServerPlayer player) {
        train(player, Source.PROTEGO_DEFLECT);
    }

    /** This player fought the Imperius Curse for a second and did not break it. Trains WILLPOWER. */
    public static void onImperiusEndured(ServerPlayer player) {
        train(player, Source.IMPERIUS_ENDURED);
    }

    /** This player repelled a Legilimency intrusion. Trains WILLPOWER. */
    public static void onMindDefended(ServerPlayer player) {
        train(player, Source.MIND_DEFENDED);
    }

    private static void train(ServerPlayer player, Source source) {
        if (!ModuleManager.isEnabled(Module.PLAYER_STATS)) return;
        PlayerStatsAPI.addTrainingProgress(player, source.stat(), source.rawAmount(),
                source.descriptionKey());
    }
}
