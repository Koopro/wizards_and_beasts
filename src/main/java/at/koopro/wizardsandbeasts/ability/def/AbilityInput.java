package at.koopro.wizardsandbeasts.ability.def;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * How an ability is physically invoked: an optional hold-to-charge window and an optional target pick.
 * Optional on {@link AbilityDefinition} (default {@link #NONE}), so definitions that fire on a bare key
 * press stay unchanged.
 *
 * <p>{@code chargeTicks} is a <b>client-side</b> input contract — the client must hold the use/quick key
 * that long before it sends anything. The server cannot observe hold duration, so it is not re-validated;
 * the authoritative checks are the grant/module/cooldown gates plus {@code targeting}/{@code range}, which
 * <i>are</i> re-validated on every trigger.
 *
 * @param targeting   what the client must pick before firing
 * @param chargeTicks ticks the key must be held before the request is sent; clamped {@code >= 0}
 * @param range       max pick distance in blocks, and the server-side sanity bound on the received target;
 *                    clamped {@code >= 0}, meaningful only when {@code targeting != NONE}
 */
@NullMarked
public record AbilityInput(AbilityTargeting targeting, int chargeTicks, double range) {

    /** Press-to-fire, no target — the default for definitions that omit the {@code input} field. */
    public static final AbilityInput NONE = new AbilityInput(AbilityTargeting.NONE, 0, 0.0);

    public AbilityInput {
        chargeTicks = Math.max(0, chargeTicks);
        range = Math.max(0.0, range);
    }

    public static final Codec<AbilityInput> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AbilityTargeting.CODEC.optionalFieldOf("targeting", AbilityTargeting.NONE).forGetter(AbilityInput::targeting),
            Codec.INT.optionalFieldOf("chargeTicks", 0).forGetter(AbilityInput::chargeTicks),
            Codec.DOUBLE.optionalFieldOf("range", 0.0).forGetter(AbilityInput::range)
    ).apply(instance, AbilityInput::new));

    public boolean requiresTarget() {
        return targeting != AbilityTargeting.NONE;
    }

    public boolean requiresCharge() {
        return chargeTicks > 0;
    }
}
