package at.koopro.wizardsandbeasts.client.heritage.state;

import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.TransformationState;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public final class ClientHeritageDataState {
    private static final PlayerHeritageData INSTANCE = new PlayerHeritageData();
    private static int lastSyncVersion;

    private ClientHeritageDataState() {}

    public static void applySync(int syncVersion,
                                 @Nullable Heritage heritage,
                                 @Nullable HeritageVariant variant,
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
        INSTANCE.applySync(heritage, variant, locked, state, activeFormId, debugOverlay, flags,
                professionPoints, totalProfessionPointsEarned, unlockedProfessions, selectedProfessionId);
    }

    public static PlayerHeritageData get() {
        return INSTANCE;
    }

    public static void clear() {
        INSTANCE.reset();
    }
}
