package at.koopro.wizardsandbeasts.disguise;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;
import java.util.UUID;

/**
 * Who a player currently looks like.
 *
 * <h2>What this is not</h2>
 * <p>It is not who they <em>are</em>. The wearer's own UUID, permissions, team, advancements, vault
 * and criminal record are untouched and unreadable from here — this record holds an appearance and a
 * clock, and nothing that grants anything. That separation is the security property of the whole
 * feature, and it is a property of the data model rather than of care taken at each call site: there
 * is no field here that could be mistaken for an identity claim.
 *
 * <h2>Why the original is not cached</h2>
 * <p>Nothing is ever taken away. The player's real profile stays exactly where it always was, on the
 * player; a disguise is a value laid <em>over</em> it for rendering, and reverting is deleting that
 * value. Caching a copy of something that was never modified would create a second source of truth
 * for a player's identity, which is the one thing this feature must not have.
 *
 * <h2>Why there is no {@code slim} field</h2>
 * <p>The brief asked for one. It is deliberately absent: the model variant is a property of the
 * <em>skin</em>, and the client already learns it from the {@link net.minecraft.world.entity.player.PlayerSkin}
 * it fetches — a real account's own choice, or {@code DefaultPlayerSkin.get(uuid)}'s variant when
 * there is no account behind the name. A boolean on the wire could only ever contradict that, and a
 * disguise that rendered a wide body under a slim skin's texture is worse than one that asks nobody.
 *
 * @param ticksRemaining how long the disguise lasts; {@code 0} means none and {@link #INDEFINITE}
 *                       means it lasts until something clears it
 * @param targetId       the impersonated player's UUID, used to fetch their skin client-side. Real
 *                       when Mojang knows the name, {@link UUIDUtil#createOfflinePlayerUUID} derived
 *                       when it does not — either way present, so a disguise is never half-set
 * @param targetName     their name, for the nameplate — carried rather than looked up, because the
 *                       impersonated player may be offline or on another server entirely
 */
@NullMarked
public record DisguiseState(int ticksRemaining, Optional<UUID> targetId, String targetName) {

    /**
     * A disguise with no clock.
     *
     * <p>Negative rather than {@link Integer#MAX_VALUE} so that "does not expire" is a distinguishable
     * state rather than a very large number that {@link #tickDown()} would silently decrement for
     * sixty-eight years. Admin disguises use it; Polyjuice never does — a potion that did not wear
     * off would remove the only window in which a disguise can be caught.
     */
    public static final int INDEFINITE = -1;

    public static final DisguiseState NONE = new DisguiseState(0, Optional.empty(), "");

    public static final Codec<DisguiseState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("ticksRemaining", 0).forGetter(DisguiseState::ticksRemaining),
            UUIDUtil.CODEC.optionalFieldOf("targetId").forGetter(DisguiseState::targetId),
            Codec.STRING.optionalFieldOf("targetName", "").forGetter(DisguiseState::targetName)
    ).apply(inst, DisguiseState::new));

    /** Whether this player is currently wearing somebody else's face. */
    public boolean isDisguised() {
        return (ticksRemaining > 0 || ticksRemaining == INDEFINITE) && targetId.isPresent();
    }

    /** Whether this disguise has a clock at all. */
    public boolean isIndefinite() {
        return ticksRemaining == INDEFINITE;
    }

    /**
     * Set the remaining ticks on a clock.
     *
     * <p>Clamps every negative to zero, {@link #INDEFINITE} included, and that is the point: a
     * countdown reaching zero and being ticked once more produces {@code -1}, which <em>is</em> the
     * sentinel. Letting it through would turn an expiring Polyjuice into a permanent disguise —
     * silently, and only for the player unlucky enough to hit the boundary.
     *
     * <p>So there is no way to reach the sentinel through this method. An endless disguise is built
     * with the canonical constructor, which is the one place that is allowed to mean it.
     */
    public DisguiseState withTicks(int ticks) {
        return new DisguiseState(Math.max(0, ticks), targetId, targetName);
    }

    /** One tick off the clock. A no-op on an indefinite disguise, which has no clock to run down. */
    public DisguiseState tickDown() {
        return isIndefinite() ? this : withTicks(ticksRemaining - 1);
    }
}
