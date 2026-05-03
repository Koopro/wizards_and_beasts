package at.koopro.wizardsandbeasts.client.state;

import at.koopro.wizardsandbeasts.data.PlayerTypeData;
import at.koopro.wizardsandbeasts.type.TransformationState;
import at.koopro.wizardsandbeasts.type.WizSubtype;
import at.koopro.wizardsandbeasts.type.WizType;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

public final class ClientTypeDataState {
    private static final PlayerTypeData INSTANCE = new PlayerTypeData();
    private static int lastSyncVersion;

    private ClientTypeDataState() {}

    public static void applySync(int syncVersion,
                                 @Nullable WizType type,
                                 @Nullable WizSubtype subtype,
                                 boolean locked,
                                 TransformationState state,
                                 @Nullable String activeFormId,
                                 boolean debugOverlay,
                                 Map<String, String> flags,
                                 int professionPoints,
                                 int totalProfessionPointsEarned,
                                 Set<String> unlockedProfessions,
                                 @Nullable String selectedProfessionId) {
        if (syncVersion < lastSyncVersion) {
            return;
        }
        lastSyncVersion = syncVersion;
        INSTANCE.applySync(type, subtype, locked, state, activeFormId, debugOverlay, flags,
                professionPoints, totalProfessionPointsEarned, unlockedProfessions, selectedProfessionId);
    }

    public static PlayerTypeData get() {
        return INSTANCE;
    }

    public static void clear() {
        INSTANCE.reset();
    }
}
