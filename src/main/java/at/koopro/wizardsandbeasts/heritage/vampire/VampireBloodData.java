package at.koopro.wizardsandbeasts.heritage.vampire;

import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One blood-drinker's pool, and nothing else.
 *
 * <p>A mutable {@link ModAttachments.NbtSerializable} rather than a codec record, matching
 * {@code PlayerHeritageData} and {@code PlayerSpellData}: the pool is written several times a second by
 * the drain tick, and rebuilding an immutable record each time to store it back is motion without
 * meaning. No gameplay decisions live here — that is {@link VampireBloodAPI} (player-level operations)
 * and {@link VampireBloodHandler} (when they happen). This class only clamps.
 *
 * <p><b>{@link #maxBlood} is stored, not looked up.</b> It is seeded from config when a heritage is
 * committed, so a later Born-vs-Turned capacity split, a ranked elder, or an item that widens the pool
 * are all a change to the seeding and not a change to everything that reads a percentage.
 *
 * <p><b>Death.</b> The attachment is {@code copyOnDeath}, so the pool survives — being killed does not
 * make a vampire less thirsty, and a respawn that handed back a full pool would make dying the cheapest
 * way to feed. What death does do is settle the pool to a fixed fraction on respawn
 * ({@code vampireBloodRespawnPercent}, 40% by default), which is {@link ThirstStage#THIRSTY}: enough to
 * walk away from the graveyard, not enough to walk away fed. See {@code VampireBloodEvents.onRespawn}.
 */
@NullMarked
public class VampireBloodData implements ModAttachments.NbtSerializable {

    /**
     * Ceiling on {@link #ticksSinceLastFeed}, and the value a fresh pool starts at.
     *
     * <p>A capped counter rather than a free-running one for two reasons. A vampire who has never fed
     * must not read as having just fed, or their first bite is refused for two seconds; and the counter
     * is incremented every pass forever, so left uncapped it eventually overflows into a negative and
     * every feed after that is refused for the rest of the session.
     */
    private static final int FEED_CLOCK_CAP = 1_000_000;

    private float blood;
    private float maxBlood = 100.0f;

    /**
     * Whether this pool has ever been filled by {@link #reset}.
     *
     * <p>The migration flag, and the reason it is serialized. Every player carries this attachment,
     * including vampires who were vampires before the blood economy existed — and a default pool is an
     * <em>empty</em> pool, so without this they would all have logged in dying of thirst. See
     * {@code VampireBloodEvents.onLogin}.
     */
    private boolean seeded;

    /**
     * Fractional drain carried between enforcement passes.
     *
     * <p>The tick runs on an interval and the drain rate is per second, so the loss for one pass is
     * usually a fraction of a point. Rounding it away each time is how a "0.2 per second" drain silently
     * becomes zero; this accumulates the remainder instead. Named for vanilla's own exhaustion, which
     * solves the identical problem for hunger.
     */
    private float exhaustion;

    private int ticksSinceLastFeed = FEED_CLOCK_CAP;

    /** Live-only: the pool value last pushed to the owning client. Never serialized — see the API. */
    private transient float lastSyncedBlood = Float.NaN;

    /**
     * Live-only: the band the player was last told about.
     *
     * <p>Not serialized, deliberately. Persisting it would mean a vampire who logged out PARCHED is never
     * told they are parched when they log back in — and the one moment a thirst warning is worth most is
     * the moment a player arrives back in a body they left in trouble.
     */
    @Nullable
    private transient ThirstStage lastNotifiedStage;

    public float getBlood() {
        return blood;
    }

    public void setBlood(float value) {
        blood = Mth.clamp(value, 0f, maxBlood);
    }

    public float getMaxBlood() {
        return maxBlood;
    }

    /** Widening or narrowing the pool re-clamps what is in it, so a shrunk pool cannot overflow. */
    public void setMaxBlood(float value) {
        maxBlood = Math.max(1.0f, value);
        blood = Mth.clamp(blood, 0f, maxBlood);
    }

    /** Adds blood, clamped at the ceiling. Negative amounts are ignored — use {@link #consumeBlood}. */
    public void addBlood(float amount) {
        if (amount <= 0f) {
            return;
        }
        setBlood(blood + amount);
    }

    /**
     * Spends blood if there is enough of it.
     *
     * @return {@code true} when the full amount was paid; {@code false} leaves the pool untouched, so a
     *         caller that cannot afford an ability is never left having half-paid for it.
     */
    public boolean consumeBlood(float amount) {
        if (amount <= 0f) {
            return true;
        }
        if (blood < amount) {
            return false;
        }
        setBlood(blood - amount);
        return true;
    }

    /** Drains without the affordability check, floored at empty. This is what the passive tick uses. */
    public void drainBlood(float amount) {
        if (amount <= 0f) {
            return;
        }
        setBlood(blood - amount);
    }

    public float getBloodPercent() {
        return maxBlood <= 0f ? 0f : Mth.clamp(blood / maxBlood, 0f, 1f);
    }

    public ThirstStage getThirstStage() {
        return ThirstStage.of(getBloodPercent());
    }

    public float getExhaustion() {
        return exhaustion;
    }

    public void setExhaustion(float value) {
        exhaustion = Math.max(0f, value);
    }

    public void addExhaustion(float amount) {
        if (amount <= 0f) {
            return;
        }
        exhaustion = Math.min(exhaustion + amount, maxBlood);
    }

    public int getTicksSinceLastFeed() {
        return ticksSinceLastFeed;
    }

    public void setTicksSinceLastFeed(int ticks) {
        ticksSinceLastFeed = Math.max(0, ticks);
    }

    public void addTicksSinceLastFeed(int ticks) {
        if (ticksSinceLastFeed >= FEED_CLOCK_CAP) {
            return;
        }
        setTicksSinceLastFeed(Math.min(FEED_CLOCK_CAP, ticksSinceLastFeed + Math.max(0, ticks)));
    }

    /** True once this pool has been filled for a real heritage; false on a pool nobody has ever seeded. */
    public boolean isSeeded() {
        return seeded;
    }

    /** Fills the pool and clears the feed clock. The state a freshly committed vampire starts in. */
    public void reset(float newMaxBlood) {
        setMaxBlood(newMaxBlood);
        blood = maxBlood;
        exhaustion = 0f;
        ticksSinceLastFeed = FEED_CLOCK_CAP;
        seeded = true;
        lastSyncedBlood = Float.NaN;
        lastNotifiedStage = null;
    }

    /** The band the player has already been warned about, or null when they have not been warned. */
    @Nullable
    public ThirstStage getLastNotifiedStage() {
        return lastNotifiedStage;
    }

    public void setLastNotifiedStage(@Nullable ThirstStage stage) {
        lastNotifiedStage = stage;
    }

    float getLastSyncedBlood() {
        return lastSyncedBlood;
    }

    void setLastSyncedBlood(float value) {
        lastSyncedBlood = value;
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Blood", blood);
        tag.putFloat("MaxBlood", maxBlood);
        tag.putFloat("Exhaustion", exhaustion);
        tag.putInt("TicksSinceLastFeed", ticksSinceLastFeed);
        tag.putBoolean("Seeded", seeded);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        // Max first: setBlood clamps against it, so reading them the other way round would truncate a
        // pool belonging to a vampire whose capacity had been widened.
        maxBlood = Math.max(1.0f, tag.getFloat("MaxBlood").orElse(100.0f));
        blood = Mth.clamp(tag.getFloat("Blood").orElse(maxBlood), 0f, maxBlood);
        exhaustion = Math.max(0f, tag.getFloat("Exhaustion").orElse(0f));
        ticksSinceLastFeed = Mth.clamp(tag.getInt("TicksSinceLastFeed").orElse(FEED_CLOCK_CAP), 0, FEED_CLOCK_CAP);
        seeded = tag.getBoolean("Seeded").orElse(false);
        lastSyncedBlood = Float.NaN;
        lastNotifiedStage = null;
    }
}
