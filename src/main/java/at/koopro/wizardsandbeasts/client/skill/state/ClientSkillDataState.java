package at.koopro.wizardsandbeasts.client.skill.state;

import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;

import java.util.Map;

public final class ClientSkillDataState {
    private static final PlayerSkillData INSTANCE = new PlayerSkillData();
    private static int lastSyncVersion;

    private ClientSkillDataState() {}

    public static PlayerSkillData get() {
        return INSTANCE;
    }

    public static void applySync(int syncVersion, int points, int totalPointsEarned, Map<String, Integer> unlockedSkills) {
        if (syncVersion < lastSyncVersion) {
            return;
        }
        lastSyncVersion = syncVersion;
        INSTANCE.applySync(points, totalPointsEarned, unlockedSkills);
    }
}
