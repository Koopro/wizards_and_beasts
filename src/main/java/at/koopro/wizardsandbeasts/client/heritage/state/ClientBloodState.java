package at.koopro.wizardsandbeasts.client.heritage.state;

import at.koopro.wizardsandbeasts.heritage.nutrition.NutritionPolicy;
import at.koopro.wizardsandbeasts.heritage.vampire.ThirstStage;
import org.jspecify.annotations.NullMarked;

/**
 * The local player's nutrition policy and blood pool, as of the last sync.
 *
 * <p>Read by the blood meter and by the handler that hides the vanilla hunger bar. Two consumers, one
 * fact: whether the HUD replaces the hunger bar and what it replaces it with must be the same decision,
 * or a player ends up with both bars or with neither.
 *
 * <p>Cleared on disconnect by {@link ClientHeritageLifecycle}, for the reason
 * {@link ClientHeritageDataState#clear()} sets out at length: this is one static block with no world
 * scoping, so on a server without this mod nothing ever overwrites it and the last world's vampire keeps
 * their bar. Unlike its sibling it carries no sync-version guard, because there is no counter to compare
 * against — blood is a single scalar that the newest packet is always right about.
 *
 * <p>The thirst band is stored as the server sent it rather than recomputed from {@link #percent()}. The
 * band floors live in a common config, which is not synchronised; see
 * {@code BloodDataSyncS2CPayload}'s header.
 */
@NullMarked
public final class ClientBloodState {

    private static NutritionPolicy policy = NutritionPolicy.VANILLA;
    private static ThirstStage stage = ThirstStage.SATED;
    private static float blood;
    private static float maxBlood = 100.0f;

    private ClientBloodState() {}

    public static void applySync(NutritionPolicy syncedPolicy, ThirstStage syncedStage,
                                 float syncedBlood, float syncedMaxBlood) {
        policy = syncedPolicy;
        stage = syncedStage;
        maxBlood = Math.max(1.0f, syncedMaxBlood);
        blood = Math.max(0f, Math.min(syncedBlood, maxBlood));
    }

    public static NutritionPolicy policy() {
        return policy;
    }

    public static float blood() {
        return blood;
    }

    public static float maxBlood() {
        return maxBlood;
    }

    public static float percent() {
        return maxBlood <= 0f ? 0f : Math.max(0f, Math.min(1f, blood / maxBlood));
    }

    public static ThirstStage stage() {
        return stage;
    }

    /** Forgets the previous connection. See the class javadoc for why this is not optional. */
    public static void clear() {
        policy = NutritionPolicy.VANILLA;
        stage = ThirstStage.SATED;
        blood = 0f;
        maxBlood = 100.0f;
    }
}
