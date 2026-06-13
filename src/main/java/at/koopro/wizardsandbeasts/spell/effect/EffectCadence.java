package at.koopro.wizardsandbeasts.spell.effect;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * When a top-level effect entry fires during a BEAM_CHANNEL cast (F2). Honored only by the
 * beam-channel dispatch in {@code WandBeamChannelLogic}; for every other cast type
 * (self/projectile/cone/targeted) cadence is <b>inert</b> — the whole entry list fires once at the
 * existing application point exactly as before.
 */
public enum EffectCadence implements StringRepresentable {
    /** Once, on the first channel tick. */
    START("start"),
    /** Every channel application interval (the default). */
    TICK("tick"),
    /** Once, at channel end/release (including switching to another spell mid-hold). */
    END("end");

    public static final Codec<EffectCadence> CODEC = StringRepresentable.fromValues(EffectCadence::values);

    private final String serializedName;

    EffectCadence(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
