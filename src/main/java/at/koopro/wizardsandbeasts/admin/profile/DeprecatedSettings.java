package at.koopro.wizardsandbeasts.admin.profile;

import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.Set;

/**
 * Setting ids that existed in earlier builds. An imported profile that names one gets a warning and, for a rename,
 * the value carried to the new id; a removed one is dropped. Empty today — every id shipped so far is still live —
 * which is the point of having the table before the first rename rather than after it.
 *
 * @param renamed old id → current id
 * @param removed ids with no successor
 */
@NullMarked
public record DeprecatedSettings(Map<String, String> renamed, Set<String> removed) {

    public static final DeprecatedSettings CURRENT = new DeprecatedSettings(Map.of(), Set.of());

    public DeprecatedSettings {
        renamed = Map.copyOf(renamed);
        removed = Set.copyOf(removed);
    }
}
