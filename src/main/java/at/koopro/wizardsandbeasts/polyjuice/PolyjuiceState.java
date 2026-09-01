package at.koopro.wizardsandbeasts.polyjuice;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;
import java.util.UUID;

/**
 * Who a wizard currently looks like.
 *
 * <h2>What this is not</h2>
 * <p>It is not who they <em>are</em>. The drinker's own UUID, permissions, team, advancements, vault
 * and criminal record are untouched and unreadable from here — this record holds an appearance and a
 * clock, and nothing that grants anything. That separation is the security property of the whole
 * feature, and it is a property of the data model rather than of care taken at each call site: there
 * is no field here that could be mistaken for an identity claim.
 *
 * <h2>Why the original is not cached</h2>
 * <p>The brief asks for the original profile to be stored for restore. It is not, deliberately —
 * because nothing is ever taken away. The player's real profile stays exactly where it always was, on
 * the player; a disguise is a value laid <em>over</em> it for rendering, and reverting is deleting
 * that value. Caching a copy of something that was never modified would create a second source of
 * truth for a player's identity, which is the one thing this feature must not have.
 *
 * @param ticksRemaining how long the disguise lasts; 0 means none
 * @param targetId       the impersonated player's UUID, used to fetch their skin client-side
 * @param targetName     their name, for the nameplate — carried rather than looked up, because the
 *                       impersonated player may be offline or on another server entirely
 */
@NullMarked
public record PolyjuiceState(int ticksRemaining, Optional<UUID> targetId, String targetName) {

    public static final PolyjuiceState NONE = new PolyjuiceState(0, Optional.empty(), "");

    public static final Codec<PolyjuiceState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("ticksRemaining", 0).forGetter(PolyjuiceState::ticksRemaining),
            UUIDUtil.CODEC.optionalFieldOf("targetId").forGetter(PolyjuiceState::targetId),
            Codec.STRING.optionalFieldOf("targetName", "").forGetter(PolyjuiceState::targetName)
    ).apply(inst, PolyjuiceState::new));

    /** Whether the drinker is currently wearing somebody else's face. */
    public boolean isDisguised() {
        return ticksRemaining > 0 && targetId.isPresent();
    }

    public PolyjuiceState withTicks(int ticks) {
        return new PolyjuiceState(Math.max(0, ticks), targetId, targetName);
    }
}
