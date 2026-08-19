package at.koopro.wizardsandbeasts.heritage.profession;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

/**
 * Turns {@link ProfessionSystemAPI.UnlockCheck} reason codes into something a player can read.
 *
 * <p>Both profession payloads used to print the raw code straight into the message — a refusal read
 * {@code "Cannot unlock profession: missing_prerequisite:auror_basics"}. Shared here because unlock and
 * select produce overlapping codes and had drifted into wording each one differently.
 */
@NullMarked
public final class ProfessionFeedback {

    private static final String L = "profession.wizards_and_beasts.reason.";

    private ProfessionFeedback() {}

    public static Component reasonOf(String reason) {
        // `missing_prerequisite` carries the required node id after a colon, so it is a prefix match
        // rather than an equality one.
        if (reason.startsWith("missing_prerequisite")) {
            int colon = reason.indexOf(':');
            ProfessionNode prereq = colon < 0 ? null : ProfessionNode.byId(reason.substring(colon + 1));
            return prereq == null
                    ? Component.translatable(L + "missing_prerequisite")
                    : Component.translatable(L + "missing_prerequisite_named", prereq.getDisplayName());
        }
        String key = switch (reason) {
            case "type_not_selected", "wrong_type", "already_unlocked",
                 "not_enough_points", "not_unlocked" -> reason;
            default -> "denied";
        };
        return Component.translatable(L + key);
    }
}
