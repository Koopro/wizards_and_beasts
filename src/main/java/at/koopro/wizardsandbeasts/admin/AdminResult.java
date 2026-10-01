package at.koopro.wizardsandbeasts.admin;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The outcome of one administrative request, always carrying the <em>authoritative</em> value afterwards.
 *
 * <p>That last part is what keeps the UI honest: whether the change was applied, was a no-op, or was
 * refused, the client overwrites whatever it was showing with {@link #value()}. A rejected edit therefore
 * snaps back to the real value instead of lingering as a fake one.
 *
 * @param settingId       the setting concerned (as requested, for an unknown id)
 * @param status          applied, unchanged, or rejected
 * @param rejection       why it was refused; null unless {@code status == REJECTED}
 * @param detailKey       optional translation key with a specific explanation (a conflict's rule)
 * @param previousValue   the value before the request; "" when not disclosed
 * @param value           the value now; "" when not disclosed (unauthorised or unknown)
 * @param restartRequired whether the stored value only takes effect after a restart
 */
@NullMarked
public record AdminResult(Identifier settingId,
                          Status status,
                          @Nullable AdminRejection rejection,
                          @Nullable String detailKey,
                          String previousValue,
                          String value,
                          boolean restartRequired) {

    public enum Status {
        APPLIED,
        UNCHANGED,
        REJECTED
    }

    public boolean applied() {
        return status == Status.APPLIED;
    }

    public boolean rejected() {
        return status == Status.REJECTED;
    }

    /** The change was held back pending confirmation; see {@link AdminRejection#CONFIRMATION_REQUIRED}. */
    public boolean needsConfirmation() {
        return rejection == AdminRejection.CONFIRMATION_REQUIRED;
    }

    static AdminResult applied(Identifier id, String previous, String now, boolean restartRequired) {
        return new AdminResult(id, Status.APPLIED, null, null, previous, now, restartRequired);
    }

    static AdminResult unchanged(Identifier id, String value, boolean restartRequired) {
        return new AdminResult(id, Status.UNCHANGED, null, null, value, value, restartRequired);
    }

    static AdminResult rejected(Identifier id, AdminRejection why, @Nullable String detailKey, String current) {
        return new AdminResult(id, Status.REJECTED, why, detailKey, current, current, false);
    }
}
