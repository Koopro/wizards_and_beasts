package at.koopro.wizardsandbeasts.currency.billing;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Every way gameplay moves money in or out of a vault, and what kind of movement it is.
 *
 * <p>The kind is what makes the {@link GringottsLedger} useful: a server whose sources outrun its sinks is
 * inflating. Moving coins between a vault and a pocket at the counter is not a flow — no money is made or destroyed.
 * Coins found as loot are not seen until they are deposited, so they show up only as a rising balance.
 */
@NullMarked
public enum MoneyFlow {

    /** A career's daily wage ({@code CareerWork}). */
    CAREER_WAGE(Kind.SOURCE, true),
    /** A completed career contract ({@code owl.career.track.CareerProgression}). Paid once per contract. */
    CAREER_CONTRACT(Kind.SOURCE, true),
    /** A completed location contract ({@code location.LocationProgression}): a bank's commission, a shop's order. */
    LOCATION_CONTRACT(Kind.SOURCE, true),
    /** Gringotts' finder's fee for a recovered artefact surrendered at the counter ({@code GringottsServices}). */
    TREASURE_FINDERS_FEE(Kind.SOURCE, true),
    /** Goods sold to a lawful trader's counter ({@code market.MarketTrade}); priced by that good's demand. */
    MARKET_SALE(Kind.SOURCE, true),
    /** Goods, treasure or contract work paid for by a fence ({@code market.MarketTrade}, a location with a risk). */
    ILLICIT_SALE(Kind.SOURCE, true),
    /** The Ministry's finder's fee for the first field record of a species ({@code bestiary.ExplorationFees}). */
    EXPLORATION_FEE(Kind.SOURCE, true),
    /** A courier parcel delivered in time ({@code travel.courier.CourierService}): the sender's postage. */
    COURIER_PAY(Kind.SOURCE, true),
    /** The Auror Office's bounty for closing a case with the culprit taken alive ({@code ministry.trace.MinistryCasework}). */
    MINISTRY_BOUNTY(Kind.SOURCE, true),

    /** A wand after the first, from Ollivander's trial ({@code OllivanderPrice}). */
    WAND_PURCHASE(Kind.SINK, false),
    /** Ollivander tending a worn or damaged wand ({@code WandCare}). */
    WAND_CARE(Kind.SINK, false),
    /** Reshaping the skill web ({@code SkillCommands#respec}). */
    SKILL_RESPEC(Kind.SINK, false),
    /** Putting a hearth on the Floo Network ({@code FlooRegistrationService}). */
    FLOO_REGISTRATION(Kind.SINK, false),
    /** A Ministry fine collected from the vault ({@code MinistryFines}). */
    MINISTRY_FINE(Kind.SINK, false),
    /** The Ministry's fee for endorsing a licence to a higher rank ({@code LicenceUpgrade}). */
    LICENCE_ENDORSEMENT(Kind.SINK, false),
    /** A licence issued or renewed at the Ministry's licence office ({@code ministry.licence.LicenceOffice}). */
    LICENCE_FEE(Kind.SINK, false),
    /** The Wizengamot's appeal fee, refunded when the appeal succeeds ({@code ministry.trace.MinistryTrace}). */
    LEGAL_FEE(Kind.SINK, false),
    /** A location service's fee: appraisal, curse-breaking, wand evaluation ({@code location.LocationService}). */
    LOCATION_SERVICE(Kind.SINK, false),
    /** Rent on a high-security Gringotts vault ({@code location.gringotts.HighSecurityVault}). */
    VAULT_RENT(Kind.SINK, false),
    /** Goods bought at a lawful trader's counter ({@code market.MarketTrade}). */
    MARKET_PURCHASE(Kind.SINK, false),
    /** Goods or services bought from a fence ({@code market.MarketTrade}, a location service with a risk). */
    ILLICIT_PURCHASE(Kind.SINK, false),
    /** A career or location tier's certification fee, paid on promotion ({@code CareerGoal.Fee}). */
    CERTIFICATION(Kind.SINK, false),
    /** A wand made to order at Ollivanders ({@code WandCommission}). */
    WAND_COMMISSION(Kind.SINK, false),

    /** Galleons spent on Dragots: converted, not destroyed (the bank's commission is inside the rate). */
    DRAGOT_PURCHASE(Kind.EXCHANGE, false),
    /** Dragots sold back for Knuts. */
    DRAGOT_SALE(Kind.EXCHANGE, true),

    /** Pocket coins paid into a vault at the counter: loot, Niffler hoards and player trade entering the bank. */
    COUNTER_DEPOSIT(Kind.CIRCULATION, true),
    /** Vault money drawn out as pocket coins at the counter. */
    COUNTER_WITHDRAWAL(Kind.CIRCULATION, false),
    /**
     * Pocket Dragots spent across a trader's till, at their Knut value there. The vault never sees them; the entry
     * makes foreign coin leaving the world measurable next to the Dragots Gringotts sells.
     */
    DRAGOT_TENDER(Kind.CIRCULATION, false),
    /**
     * A toll paid to travel to a privately kept public hearth ({@code floo.FlooTolls}). Moved from one vault to
     * another, never made or destroyed: paired with {@link #FLOO_TOLL_INCOME} on the keeper's side.
     */
    FLOO_TOLL(Kind.CIRCULATION, false),
    /** A hearth keeper's share of the tolls paid to reach their hearth. */
    FLOO_TOLL_INCOME(Kind.CIRCULATION, true),

    /** An administrator's deposit (command or Control Center). */
    ADMIN_DEPOSIT(Kind.ADMIN, true),
    /** An administrator's withdrawal or clearing of a vault. */
    ADMIN_WITHDRAWAL(Kind.ADMIN, false);

    /** What a flow does to the amount of money in the world. */
    public enum Kind {
        /** Creates money. */
        SOURCE,
        /** Destroys money. */
        SINK,
        /** Converts it into something else of value (foreign coin). */
        EXCHANGE,
        /**
         * Moves it between a pocket and a vault. Not money made or destroyed — but coins found as loot first become
         * bank money here, so deposits less withdrawals is how much pocket money the world has been paying in.
         */
        CIRCULATION,
        /** An administrator's hand, kept apart so it never reads as gameplay. */
        ADMIN;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Kind kind;
    private final boolean credits;

    MoneyFlow(Kind kind, boolean credits) {
        this.kind = kind;
        this.credits = credits;
    }

    public Kind kind() {
        return kind;
    }

    /** Whether money moves into the vault on this flow (a credit), rather than out of it (a charge). */
    public boolean credits() {
        return credits;
    }

    /** The stable id stored in the ledger and printed by commands. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "currency.wizards_and_beasts.flow." + id();
    }

    public static @Nullable MoneyFlow byId(String id) {
        for (MoneyFlow flow : values()) {
            if (flow.id().equals(id)) {
                return flow;
            }
        }
        return null;
    }
}
