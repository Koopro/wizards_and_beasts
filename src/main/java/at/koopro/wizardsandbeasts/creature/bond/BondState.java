package at.koopro.wizardsandbeasts.creature.bond;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The per-creature half of the bond: who it belongs to, how much it likes them, and the two timers.
 *
 * <p>Plain mutable storage, owned by the entity and mutated only on the server. It holds no rules —
 * every threshold, gain and penalty lives in the species' {@link BondProfile}, and the decisions
 * live in {@link BondableBeast}. Splitting it this way is what lets the Niffler keep its own
 * bespoke feed handling while sharing the storage and the save format with creatures that use the
 * generic path.
 *
 * <h2>Save format</h2>
 * The three original keys are the Niffler's, byte for byte — {@code OwnerUUID} as a string,
 * {@code BondLevel}, {@code FeedCooldown} — so an existing world's Nifflers load with their owner
 * and bond intact after the migration onto this class. {@code GiftCooldown} is new and written only
 * when non-zero, which is never for a species without a gift.
 */
@NullMarked
public final class BondState {

    private static final String KEY_OWNER = "OwnerUUID";
    private static final String KEY_LEVEL = "BondLevel";
    private static final String KEY_FEED_COOLDOWN = "FeedCooldown";
    private static final String KEY_GIFT_COOLDOWN = "GiftCooldown";
    private static final String KEY_BREED_COOLDOWN = "BreedCooldown";
    private static final String KEY_GROWTH_TICKS = "GrowthTicks";
    private static final String KEY_JUVENILE = "Juvenile";

    @Nullable
    private UUID ownerUUID;
    private int level;
    private int feedCooldown;
    private int giftCooldown;
    private int breedCooldown;

    /** Ticks a juvenile has left to grow. Zero on an adult, which is what {@link #juvenile} means. */
    private int growthTicks;
    private boolean juvenile;

    /** Company accrued toward the next proximity tick. Transient by design — the Niffler's was too. */
    private int proximityTicks;

    /**
     * Ticks left waiting for a partner after being fed the breeding item. Transient: a pair split up
     * by a server restart should be fed again rather than silently resuming.
     */
    private int loveTicks;

    @Nullable
    public UUID ownerUUID() {
        return ownerUUID;
    }

    public void setOwner(@Nullable UUID owner) {
        this.ownerUUID = owner;
    }

    public boolean isOwnedBy(UUID candidate) {
        return candidate.equals(ownerUUID);
    }

    public int level() {
        return level;
    }

    /** Set the bond, clamped into {@code [0, maxBond]}. Returns the level actually stored. */
    public int setLevel(int value, int maxBond) {
        this.level = Math.max(0, Math.min(maxBond, value));
        return this.level;
    }

    public int feedCooldown() {
        return feedCooldown;
    }

    public void setFeedCooldown(int ticks) {
        this.feedCooldown = Math.max(0, ticks);
    }

    public int giftCooldown() {
        return giftCooldown;
    }

    public void setGiftCooldown(int ticks) {
        this.giftCooldown = Math.max(0, ticks);
    }

    public int breedCooldown() {
        return breedCooldown;
    }

    public void setBreedCooldown(int ticks) {
        this.breedCooldown = Math.max(0, ticks);
    }

    public boolean isJuvenile() {
        return juvenile;
    }

    public int growthTicks() {
        return growthTicks;
    }

    /** Mark this creature a juvenile with {@code ticks} of growing left. */
    public void beginGrowth(int ticks) {
        this.juvenile = ticks > 0;
        this.growthTicks = Math.max(0, ticks);
    }

    /** Count down one tick of growth; returns true on the tick it finishes growing up. */
    public boolean tickGrowth() {
        if (!juvenile) {
            return false;
        }
        if (--growthTicks > 0) {
            return false;
        }
        growthTicks = 0;
        juvenile = false;
        return true;
    }

    public int loveTicks() {
        return loveTicks;
    }

    public void setLoveTicks(int ticks) {
        this.loveTicks = Math.max(0, ticks);
    }

    public int proximityTicks() {
        return proximityTicks;
    }

    public void setProximityTicks(int ticks) {
        this.proximityTicks = Math.max(0, ticks);
    }

    /** Decrement both persisted timers by one tick. Call once per server tick. */
    public void tickTimers() {
        if (feedCooldown > 0) {
            feedCooldown--;
        }
        if (giftCooldown > 0) {
            giftCooldown--;
        }
        if (breedCooldown > 0) {
            breedCooldown--;
        }
        if (loveTicks > 0) {
            loveTicks--;
        }
    }

    public void save(ValueOutput output) {
        if (ownerUUID != null) {
            output.store(KEY_OWNER, Codec.STRING, ownerUUID.toString());
        }
        output.store(KEY_LEVEL, Codec.INT, level);
        output.store(KEY_FEED_COOLDOWN, Codec.INT, feedCooldown);
        if (giftCooldown > 0) {
            output.store(KEY_GIFT_COOLDOWN, Codec.INT, giftCooldown);
        }
        if (breedCooldown > 0) {
            output.store(KEY_BREED_COOLDOWN, Codec.INT, breedCooldown);
        }
        if (juvenile) {
            output.store(KEY_JUVENILE, Codec.BOOL, true);
            output.store(KEY_GROWTH_TICKS, Codec.INT, growthTicks);
        }
    }

    /**
     * Read back, tolerating a malformed owner string rather than throwing.
     *
     * <p>A creature whose owner id fails to parse becomes unowned, which is recoverable by feeding
     * it again; refusing to load the entity is not.
     */
    public void load(ValueInput input, int maxBond) {
        ownerUUID = input.read(KEY_OWNER, Codec.STRING).map(BondState::parseUuid).orElse(null);
        setLevel(input.read(KEY_LEVEL, Codec.INT).orElse(0), maxBond);
        setFeedCooldown(input.read(KEY_FEED_COOLDOWN, Codec.INT).orElse(0));
        setGiftCooldown(input.read(KEY_GIFT_COOLDOWN, Codec.INT).orElse(0));
        setBreedCooldown(input.read(KEY_BREED_COOLDOWN, Codec.INT).orElse(0));
        juvenile = input.read(KEY_JUVENILE, Codec.BOOL).orElse(false);
        growthTicks = juvenile ? Math.max(0, input.read(KEY_GROWTH_TICKS, Codec.INT).orElse(0)) : 0;
        proximityTicks = 0;
        loveTicks = 0;
    }

    @Nullable
    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
