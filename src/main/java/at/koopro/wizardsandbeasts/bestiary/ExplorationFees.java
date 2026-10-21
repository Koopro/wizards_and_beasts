package at.koopro.wizardsandbeasts.bestiary;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.bestiary.data.EntryHistory;
import at.koopro.wizardsandbeasts.currency.billing.GringottsBilling;
import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.owl.career.track.CareerProgression;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * The Ministry's finder's fee for a field record: the first time a player's bestiary page on a species reaches
 * {@link DiscoveryTier#OBSERVED} by their own doing, the Department for the Regulation and Control of Magical
 * Creatures pays for the sighting — more for a creature it classifies as dangerous.
 *
 * <p>An exploration reward that cannot be farmed: once per species per player, ever. "Ever" is read from the
 * journal's discovery history ({@link EntryHistory#reachedDay()}), which keeps the day each depth was first reached
 * even if an administrator later lowers the page, so a reset never pays twice. Across the whole bestiary the fees
 * come to a few dozen Galleons, paid out over a career of exploring.
 */
@NullMarked
public final class ExplorationFees {

    private ExplorationFees() {}

    /** The fee for a species of Ministry rating {@code rating} (1–5; 0 for unrated), before the server's scale. */
    public static long baseFee(int rating) {
        return switch (rating) {
            case 5 -> 290L;  // 10 Sickles
            case 4 -> 145L;  // 5 Sickles
            case 3 -> 58L;   // 2 Sickles
            case 2 -> 29L;   // 1 Sickle
            default -> 14L;
        };
    }

    /** Whether {@code before} already holds a depth of OBSERVED or more: the fee was earned then. */
    public static boolean alreadyRecorded(EntryHistory before) {
        for (DiscoveryTier tier : DiscoveryTier.values()) {
            if (tier.tierIndex() >= DiscoveryTier.OBSERVED.tierIndex() && before.reachedDay().containsKey(tier.name())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called from the earned path of {@code BestiaryDataHelper.setTier} with the history as it stood before the
     * page moved. Pays at most once per species per player.
     */
    public static void onTierEarned(ServerPlayer player, Identifier species, DiscoveryTier old, DiscoveryTier reached,
                                    EntryHistory before) {
        if (reached.tierIndex() < DiscoveryTier.OBSERVED.tierIndex()
                || old.tierIndex() >= DiscoveryTier.OBSERVED.tierIndex()
                || alreadyRecorded(before)
                || !ModuleManager.isEnabled(Module.GRINGOTTS)) {
            return;
        }
        long fee = Math.round(baseFee(CareerProgression.ratingOf(species)) * (Config.explorationFeeScalePercent / 100.0));
        if (fee <= 0 || !GringottsBilling.credit(player, MoneyFlow.EXPLORATION_FEE, fee)) {
            return;
        }
        PlayerFeedback.toast(player, NoticeKind.DISCOVERY,
                Component.translatable("bestiary.wizards_and_beasts.finders_fee"),
                Component.translatable("bestiary.wizards_and_beasts.finders_fee.body",
                        at.koopro.wizardsandbeasts.owl.career.track.CareerText.creature(species),
                        CurrencyHelper.formatFromKnuts(fee)));
    }
}
