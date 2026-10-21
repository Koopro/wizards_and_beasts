package at.koopro.wizardsandbeasts.location;

import at.koopro.wizardsandbeasts.currency.billing.GringottsBilling;
import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Something a location sells: appraising an artefact, breaking its curse, renting a vault. Registered once by id in
 * {@link LocationServices}; each location's data decides whether it offers it, from which tier and at what price
 * ({@link ServiceOffer}). {@link LocationProgression#useService} has already checked the module, the player's
 * presence and their tier before {@link #perform} runs.
 */
@NullMarked
public interface LocationService {

    /** What using it now would do or cost, for the board's service line. Read-only. */
    Quote quote(ServerPlayer player, Use use);

    /** Does it. Charges through {@link Use#charge}, and says itself what happened. */
    boolean perform(ServerPlayer player, Use use);

    /**
     * @param available whether {@link #perform} would do anything now
     * @param detail    one line: the price and what it buys, or why not
     */
    record Quote(boolean available, Component detail) {
        public static Quote no(Component why) {
            return new Quote(false, why);
        }

        public static Quote yes(Component what) {
            return new Quote(true, what);
        }
    }

    /**
     * One use of a service at one location by one player.
     *
     * @param location   the location's id
     * @param def        its definition
     * @param offer      this service's terms there
     * @param tiersAbove tiers the player holds above the offer's minimum
     */
    record Use(Identifier location, LocationDefinition def, ServiceOffer offer, int tiersAbove) {

        public long fee() {
            return offer.feeFor(tiersAbove);
        }

        public float rate() {
            return offer.rateFor(tiersAbove);
        }

        /** Whether money changes hands at all: without Gringotts, a service is a favour. */
        public static boolean billed() {
            return ModuleManager.isEnabled(Module.GRINGOTTS);
        }

        /**
         * Takes {@code knuts} for this service, all or nothing; refused, the player is told the price. A fence's
         * services are booked as illicit purchases, so the ledger keeps the black market apart.
         */
        public boolean charge(ServerPlayer player, long knuts) {
            return charge(player, def.risk().isPresent() ? MoneyFlow.ILLICIT_PURCHASE : MoneyFlow.LOCATION_SERVICE,
                    knuts);
        }

        /** As {@link #charge(ServerPlayer, long)}, booked to a flow of the service's own (a vault's rent). */
        public boolean charge(ServerPlayer player, MoneyFlow flow, long knuts) {
            if (!billed() || knuts <= 0) {
                return true;
            }
            if (GringottsBilling.charge(player, flow, knuts)) {
                return true;
            }
            PlayerFeedback.refuse(player, Component.translatable("location.wizards_and_beasts.cannot_afford"),
                    Component.translatable("location.wizards_and_beasts.price", CurrencyHelper.formatFromKnuts(knuts)));
            return false;
        }
    }
}
