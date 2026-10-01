package at.koopro.wizardsandbeasts.admin.profile;

import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.regex.Pattern;

/** Profile ids, names and export file names: what is allowed, and how a name becomes an id. */
@NullMarked
public final class ProfileIds {

    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,48}");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9 _'\\-().]{1,48}");
    /** A plain file name in the profile folder: no separators, no "..", always .json. */
    private static final Pattern FILE = Pattern.compile("[A-Za-z0-9_\\-.]{1,64}\\.json");

    private ProfileIds() {}

    public static boolean validId(String id) {
        return ID.matcher(id).matches();
    }

    public static boolean validName(String name) {
        return NAME.matcher(name).matches() && !name.isBlank();
    }

    /** The name of a snapshot taken without a label: "Snapshot 2026-10-01 11.32.05" (a name may not hold ':'). */
    public static String snapshotName(java.time.LocalDateTime when) {
        return "Snapshot " + when.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss",
                java.util.Locale.ROOT));
    }

    public static boolean validFileName(String file) {
        return FILE.matcher(file).matches() && !file.contains("..");
    }

    /** "Hogwarts RP (2)" → "hogwarts_rp_2". */
    public static String idFor(String name) {
        String id = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (id.isEmpty()) {
            id = "profile";
        }
        return id.length() > 48 ? id.substring(0, 48) : id;
    }
}
