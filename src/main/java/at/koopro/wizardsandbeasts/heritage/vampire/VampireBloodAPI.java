package at.koopro.wizardsandbeasts.heritage.vampire;

import at.koopro.wizardsandbeasts.heritage.nutrition.NutritionPolicy;
import at.koopro.wizardsandbeasts.heritage.nutrition.NutritionPolicyResolver;
import at.koopro.wizardsandbeasts.network.heritage.BloodDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * The one way the rest of the mod touches a blood pool.
 *
 * <p>Everything here is server-side and every write syncs, which is the point: the pool is drawn on the
 * HUD, so a write that does not reach the client is a bar that lies. {@code ObscurialResourceManager}
 * gets away with plain setters because Obscurial resources ride the heritage flag map and are therefore
 * synced by whoever sends the heritage payload next; blood has its own payload precisely so it can be
 * sent at the cadence a bar needs rather than at the cadence a heritage change needs, and that trade
 * only pays off if the writes go through one place.
 *
 * <p>Mirrors {@code HeritageAPI}'s shape deliberately: static accessors over the attachment, plus the
 * two lifecycle verbs ({@link #seed}, {@link #clear}) that the heritage commit path calls.
 */
@NullMarked
public final class VampireBloodAPI {

    /**
     * How far the pool must move before an unprompted sync is sent.
     *
     * <p>The drain tick moves the pool by a fraction of a point at a time; sending every one of those
     * would put a packet on the wire several times a second per vampire to move a bar by less than a
     * pixel. Half a point is roughly one pixel of a 100-wide meter, so nothing visible is skipped. A
     * stage change syncs regardless of this — see {@link #syncIfChanged}.
     */
    private static final float SYNC_EPSILON = 0.5f;

    private VampireBloodAPI() {}

    public static VampireBloodData getData(ServerPlayer player) {
        return player.getData(ModAttachments.VAMPIRE_BLOOD.get());
    }

    public static float getBlood(ServerPlayer player) {
        return getData(player).getBlood();
    }

    public static ThirstStage getThirstStage(ServerPlayer player) {
        return getData(player).getThirstStage();
    }

    /** True when this player is on the blood economy at all. Shorthand over the policy resolver. */
    public static boolean drinksBlood(ServerPlayer player) {
        return NutritionPolicyResolver.resolve(player) == NutritionPolicy.BLOOD;
    }

    /** Adds blood and pushes the result. Returns how much was actually taken on, after the ceiling. */
    public static float addBlood(ServerPlayer player, float amount) {
        VampireBloodData data = getData(player);
        float before = data.getBlood();
        data.addBlood(amount);
        sync(player);
        return data.getBlood() - before;
    }

    /**
     * Spends blood if the player can afford it, and syncs when they could.
     *
     * <p><b>No caller yet, deliberately.</b> This is the half of the pool that makes it a resource rather
     * than a meter: bat form, a night-sight, a charmed gaze all cost blood, and each is out of scope for
     * this pass. Shipping the verb with the pool means the first of them does not also have to invent the
     * refuse-rather-than-overdraw contract — which is the part that is easy to get wrong, because the
     * tempting implementation drains to zero and lets the ability fire anyway.
     *
     * @return {@code false} with the pool untouched when there is not enough — the caller's ability
     *         should refuse rather than proceed on credit.
     */
    public static boolean consumeBlood(ServerPlayer player, float amount) {
        VampireBloodData data = getData(player);
        if (!data.consumeBlood(amount)) {
            return false;
        }
        sync(player);
        return true;
    }

    /** Sets the pool outright. The debug commands' verb; ordinary gameplay should add or consume. */
    public static void setBlood(ServerPlayer player, float value) {
        getData(player).setBlood(value);
        sync(player);
    }

    /**
     * Gives a freshly committed blood-drinker a full pool sized from config.
     *
     * <p>Called from {@code HeritageAPI.commit}. Sizing the pool at commit rather than reading config
     * live is what leaves room for a Born vampire to hold more than a Dhampir later: the capacity becomes
     * a property of the character at the moment the character is made, exactly like the POWER roll.
     */
    public static void seed(ServerPlayer player) {
        getData(player).reset(VampireBloodConfig.maxBlood);
        sync(player);
    }

    /**
     * Takes the pool away — the inverse of {@link #seed}, called from {@code HeritageAPI.clear}.
     *
     * <p>Resets rather than leaving the old value behind, because the attachment is {@code copyOnDeath}
     * and therefore persistent: a wizard who was once a vampire would otherwise carry a stale pool that
     * nothing drains and nothing draws, and would get it back at whatever value it stopped at if they
     * ever became a vampire again.
     */
    public static void clear(ServerPlayer player) {
        getData(player).reset(VampireBloodConfig.maxBlood);
        sync(player);
    }

    /** Pushes the pool to its owner unconditionally. */
    public static void sync(ServerPlayer player) {
        VampireBloodData data = getData(player);
        data.setLastSyncedBlood(data.getBlood());
        BloodDataSyncS2CPayload.syncToPlayer(player);
    }

    /**
     * Pushes the pool only when it has moved enough to matter, or when the thirst band has changed.
     *
     * <p>The band check is not an optimisation — it is a correctness rule. The bar's colour and the
     * player's penalties are both keyed on the band, so a band boundary crossed inside the epsilon would
     * leave a SATED-coloured bar on a player the server is already debuffing.
     */
    public static void syncIfChanged(ServerPlayer player) {
        VampireBloodData data = getData(player);
        float last = data.getLastSyncedBlood();
        if (Float.isNaN(last)) {
            sync(player);
            return;
        }
        boolean moved = Math.abs(data.getBlood() - last) >= SYNC_EPSILON;
        boolean bandChanged = ThirstStage.of(clampPercent(last, data.getMaxBlood())) != data.getThirstStage();
        if (moved || bandChanged) {
            sync(player);
        }
    }

    private static float clampPercent(float blood, float max) {
        return max <= 0f ? 0f : Math.max(0f, Math.min(1f, blood / max));
    }
}
