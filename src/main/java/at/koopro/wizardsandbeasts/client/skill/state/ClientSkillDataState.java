package at.koopro.wizardsandbeasts.client.skill.state;

import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;

import java.util.Map;

public final class ClientSkillDataState {
    private static final PlayerSkillData INSTANCE = new PlayerSkillData();
    private static int lastSyncVersion;

    private ClientSkillDataState() {}

    private static long version;

    /** Bumps on every applied sync, so the sheet can cache the build it derives from the allocation. */
    public static long version() {
        return version;
    }

    public static PlayerSkillData get() {
        return INSTANCE;
    }

    public static void applySync(int syncVersion, int points, int totalPointsEarned, Map<String, Integer> unlockedSkills) {
        applySync(syncVersion, points, totalPointsEarned, unlockedSkills,
                at.koopro.wizardsandbeasts.network.skill.SkillDataSyncS2CPayload.RespecTerms.FREE);
    }

    public static void applySync(int syncVersion, int points, int totalPointsEarned, Map<String, Integer> unlockedSkills,
                                 at.koopro.wizardsandbeasts.network.skill.SkillDataSyncS2CPayload.RespecTerms terms) {
        if (syncVersion < lastSyncVersion) {
            return;
        }
        lastSyncVersion = syncVersion;
        respecTerms = terms;
        INSTANCE.applySync(points, totalPointsEarned, unlockedSkills, terms.freeRespecAvailable());
        version++;
    }

    private static at.koopro.wizardsandbeasts.network.skill.SkillDataSyncS2CPayload.RespecTerms respecTerms =
            at.koopro.wizardsandbeasts.network.skill.SkillDataSyncS2CPayload.RespecTerms.FREE;

    /** The respec prices the server last quoted for this player. */
    public static at.koopro.wizardsandbeasts.network.skill.SkillDataSyncS2CPayload.RespecTerms respecTerms() {
        return respecTerms;
    }
}
