package at.koopro.wizardsandbeasts.apparition;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Why an attempt to begin Apparating did or did not start.
 *
 * <p>The gate has always known its own reasons — it wrote each one to the player as a toast and then returned
 * a bare {@code boolean}. That made the reason a side effect: a caller could learn <i>that</i> a jump was
 * refused but never <i>why</i>, so anything wanting to react differently to "no licence" than to "on
 * cooldown" had to re-derive the whole gate and risk disagreeing with it.
 *
 * <p>Each constant carries the lang key the player is shown, so the message and the reason cannot drift
 * apart. {@link #STARTED} and the two silent refusals carry none.
 */
@NullMarked
public enum ApparitionStartResult {

    /** A charge is now in flight. The only non-refusal. */
    STARTED(null, false),

    /** The Apparition module is switched off on this server. Silent: an absent feature does not scold. */
    REJECTED_MODULE_OFF(null, false),

    /** Never taught, or of a heritage that does not Apparate. */
    REJECTED_UNTRAINED("apparition.wizards_and_beasts.fail.untrained", false),

    /** Still in pieces from the last attempt. */
    REJECTED_SPLINCHED("apparition.wizards_and_beasts.fail.splinched", false),

    /**
     * No licence, on a server where the Ministry cares. Only a wall while
     * {@link at.koopro.wizardsandbeasts.module.Module#MINISTRY} is on — everywhere else the absence of a
     * licence is a tax on the splinch ladder rather than a refusal.
     */
    REJECTED_UNLICENSED(at.koopro.wizardsandbeasts.ministry.licence.MinistryLicences.REFUSAL_KEY, false),

    /** Not gathered yet. Transient, so it goes to the action bar rather than a toast. */
    REJECTED_COOLDOWN("apparition.wizards_and_beasts.fail.cooldown", true),

    /** Something here holds you in place. */
    REJECTED_WARDED_ORIGIN("apparition.wizards_and_beasts.fail.warded_origin", false),

    /** Already holding one. Silent by design; see {@code ApparitionChargeManager.begin}. */
    REJECTED_ALREADY_CHARGING(null, false),

    /** The destination is in another world. */
    REJECTED_OTHER_DIMENSION("apparition.wizards_and_beasts.fail.other_world", false);

    private final @Nullable String messageKey;
    private final boolean transientMessage;

    ApparitionStartResult(@Nullable String messageKey, boolean transientMessage) {
        this.messageKey = messageKey;
        this.transientMessage = transientMessage;
    }

    /** The lang key the player is shown, or {@code null} for the refusals that say nothing. */
    public @Nullable String messageKey() {
        return messageKey;
    }

    /**
     * True when the message belongs on the action bar rather than in a toast — it is worth saying now and
     * worthless a second later.
     */
    public boolean isTransientMessage() {
        return transientMessage;
    }

    public boolean started() {
        return this == STARTED;
    }
}
