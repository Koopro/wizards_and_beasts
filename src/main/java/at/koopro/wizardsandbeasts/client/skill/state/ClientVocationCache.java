package at.koopro.wizardsandbeasts.client.skill.state;

import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Client-side mirror of committed Vocation state, fed by {@code VocationDataSyncS2CPayload}. Sibling to
 * {@link ClientSkillDataState}; exists so the future Vocation GUI can read committed slots. No GUI yet.
 */
public final class ClientVocationCache {
    private static Optional<Identifier> primary = Optional.empty();
    private static Optional<Identifier> secondary = Optional.empty();
    private static int lastSyncVersion;

    private ClientVocationCache() {}

    public static Optional<Identifier> primary() { return primary; }
    public static Optional<Identifier> secondary() { return secondary; }

    public static void applySync(int syncVersion, String primaryId, String secondaryId) {
        if (syncVersion < lastSyncVersion) {
            return;
        }
        lastSyncVersion = syncVersion;
        primary = parse(primaryId);
        secondary = parse(secondaryId);
    }

    private static Optional<Identifier> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        int colon = raw.indexOf(':');
        if (colon <= 0 || colon >= raw.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(Identifier.fromNamespaceAndPath(
                raw.substring(0, colon), raw.substring(colon + 1)));
    }
}
