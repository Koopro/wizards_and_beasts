package at.koopro.wizardsandbeasts.client.skill.state;

import at.koopro.wizardsandbeasts.skill.PlayerSkillBonusData;
import org.jspecify.annotations.NonNull;

public final class ClientSkillBonusCache {
    private static @NonNull PlayerSkillBonusData data = PlayerSkillBonusData.DEFAULT;

    private ClientSkillBonusCache() {
    }

    public static @NonNull PlayerSkillBonusData get() {
        return data;
    }

    public static void set(@NonNull PlayerSkillBonusData newData) {
        data = newData;
    }
}
