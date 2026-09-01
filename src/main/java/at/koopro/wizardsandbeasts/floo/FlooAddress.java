package at.koopro.wizardsandbeasts.floo;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * What a Floo address is allowed to be, and the one place that decides it.
 *
 * <h2>Why this exists</h2>
 * <p>{@link FlooNetworkManager#register} took any string at all. It stripped and lowercased it for
 * the key and stored the original for display, and that was the entire contract — so an empty
 * address, an address made only of spaces, one four hundred characters long, or one carrying
 * section-sign formatting codes all registered happily. An empty one is the worst of them: it keys on
 * {@code ""}, appears in the destination list as a blank row nobody can identify, and cannot be
 * unregistered by anyone who did not already know it was there.
 *
 * <p>Normalisation was also duplicated at five call sites as
 * {@code address.strip().toLowerCase(Locale.ROOT)}. One of those drifting is a bug where a fireplace
 * can be registered and then never found.
 *
 * <h2>The rules</h2>
 * <ul>
 *   <li>between {@value #MIN_LENGTH} and {@value #MAX_LENGTH} characters once stripped;</li>
 *   <li>letters, digits, spaces, apostrophes and hyphens, and nothing else;</li>
 *   <li>at least one letter, so an address is a name rather than a number.</li>
 * </ul>
 *
 * <p>Deliberately permissive within that. "The Leaky Cauldron", "Weasleys' Wizarding Wheezes" and
 * "12 Grimmauld Place" are all valid, because addresses are spoken aloud in fiction and reading like
 * a place name is the whole point.
 *
 * <h2>Why an allow-list rather than a block-list</h2>
 * <p>It used to refuse {@code §} and control characters and accept everything else. That is the wrong
 * shape for this check: the danger is not a known list of bad characters but the open set of
 * everything nobody thought about — bidi overrides that reverse a list row, zero-width joiners that
 * make two different addresses render identically, combining marks that spill out of a row's height.
 * Naming the handful of characters a place name actually needs is a rule that can be finished; naming
 * the ones it must not contain is not.
 *
 * <p>{@code §} therefore stays refused, but now as a consequence rather than as a special case.
 *
 * <h2>Reserved names</h2>
 * <p>There are none, deliberately. "Ministry" and "Spawn" are exactly the addresses a server most
 * wants to <em>exist</em>, and a hardcoded refusal list would be one more thing that differs between
 * servers for no reason a player could discover. Sealing a line is already an operator power
 * ({@code /wandb world floo disable}), and it is the right one: it acts on the specific hearth
 * somebody actually registered, after the fact, and it can be undone.
 *
 * <p>Pure: no level, no player, no registry. That is what lets it be unit-tested, which is what the
 * validation is for.
 */
@NullMarked
public final class FlooAddress {

    /** Short enough to be a slip, long enough for "Hog". */
    public static final int MIN_LENGTH = 3;

    /** Long enough for a street address, short enough not to be a paragraph. */
    public static final int MAX_LENGTH = 48;

    private FlooAddress() {}

    /** Why an address was refused, or that it was accepted. */
    public enum Validity {
        VALID,
        BLANK,
        TOO_SHORT,
        TOO_LONG,
        NO_LETTERS,
        ILLEGAL_CHARACTERS;

        public boolean ok() {
            return this == VALID;
        }

        /** The line a player is shown. Every refusal says which rule it broke. */
        public Component message() {
            return switch (this) {
                case VALID -> Component.translatable("floo.wizards_and_beasts.address.valid");
                case BLANK -> Component.translatable("floo.wizards_and_beasts.address.blank");
                case TOO_SHORT -> Component.translatable("floo.wizards_and_beasts.address.too_short", MIN_LENGTH);
                case TOO_LONG -> Component.translatable("floo.wizards_and_beasts.address.too_long", MAX_LENGTH);
                case NO_LETTERS -> Component.translatable("floo.wizards_and_beasts.address.no_letters");
                case ILLEGAL_CHARACTERS -> Component.translatable("floo.wizards_and_beasts.address.illegal");
            };
        }
    }

    /** Check a raw, un-stripped address. */
    public static Validity validate(String raw) {
        String display = display(raw);
        if (display.isEmpty()) {
            return Validity.BLANK;
        }
        if (display.length() < MIN_LENGTH) {
            return Validity.TOO_SHORT;
        }
        if (display.length() > MAX_LENGTH) {
            return Validity.TOO_LONG;
        }

        boolean sawLetter = false;
        for (int i = 0; i < display.length(); i++) {
            char c = display.charAt(i);
            if (Character.isLetter(c)) {
                sawLetter = true;
                continue;
            }
            if (!isAllowedNonLetter(c)) {
                return Validity.ILLEGAL_CHARACTERS;
            }
        }
        // Checked after the character sweep so a string that is both unpronounceable and unlettered
        // is reported as the harder problem. "§§§" is an illegal address, not a numberless one.
        if (!sawLetter) {
            return Validity.NO_LETTERS;
        }
        return Validity.VALID;
    }

    /**
     * The non-letters a place name is allowed to contain.
     *
     * <p>ASCII apostrophe only, not the typographic {@code ’}. Two addresses that render almost
     * identically but key differently are a trap — one of them is findable and the other is not, and
     * nothing on screen says which you typed.
     */
    private static boolean isAllowedNonLetter(char c) {
        return Character.isDigit(c) || c == ' ' || c == '\'' || c == '-';
    }

    public static boolean isValid(String raw) {
        return validate(raw).ok();
    }

    /**
     * The form shown to a player: stripped, otherwise exactly what was typed.
     *
     * <p>Case is preserved here on purpose — "The Leaky Cauldron" should read as it was written.
     * Only {@link #key} folds case, and only for lookup.
     */
    public static String display(String raw) {
        return raw.strip();
    }

    /**
     * The lookup key: stripped and case-folded, so "leaky cauldron" finds "The Leaky Cauldron"'s
     * neighbour spelled "Leaky Cauldron".
     *
     * <p>{@link Locale#ROOT} rather than the default locale, because a Turkish-locale server folding
     * {@code I} to {@code ı} would key the same address differently from every other server, and the
     * saved data would stop matching after a locale change.
     */
    public static String key(String raw) {
        return display(raw).toLowerCase(Locale.ROOT);
    }

    /** True when two addresses name the same hearth. */
    public static boolean sameAddress(String a, String b) {
        return key(a).equals(key(b));
    }
}
