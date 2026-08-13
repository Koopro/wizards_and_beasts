package at.koopro.wizardsandbeasts.heritage.appearance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Mechanism C — composites on top of whatever {@link Proportion} or {@link FormAppearance} left
 * behind.
 *
 * <p>Ordering is a correctness requirement rather than a preference: an overlay drawn <em>under</em>
 * a form replacement is invisible, and one drawn <em>instead of</em> a proportion pass silently
 * discards the proportion. An entry may hold a body arm and an overlay together — vampire (pallor
 * over proportion) and Obscurial (smoke over the shadow form) both do.
 *
 * <p>At least one of {@code texture} or {@code particle} must be present. An overlay that names
 * neither renders nothing while still claiming a union slot, which reads at a glance like a working
 * entry and is exactly the kind of thing that survives review.
 *
 * @param texture   optional texture composited over the body
 * @param particle  optional particle type emitted around the body
 * @param tint      packed ARGB applied to the overlay; defaults to opaque white (no recolour)
 * @param emissive  true to draw at full brightness, ignoring world light
 * @param trigger   optional gate. Absent means always-on — vampire pallor. Present means the overlay
 *                  waits on a state the trigger layer resolves; the human exertion flare is the
 *                  motivating case and is <b>deferred</b>, because neither proficiency tier nor cost
 *                  magnitude currently reaches the client for a remote player
 */
public record OverlayAppearance(
        Optional<Identifier> texture,
        Optional<Identifier> particle,
        int tint,
        boolean emissive,
        Optional<String> trigger
) implements AppearanceMechanism {

    /** Opaque white — the tint that changes nothing. */
    public static final int NO_TINT = 0xFFFFFFFF;

    public static final MapCodec<OverlayAppearance> CODEC =
            RecordCodecBuilder.<OverlayAppearance>mapCodec(instance -> instance.group(
                    Identifier.CODEC.optionalFieldOf("texture").forGetter(OverlayAppearance::texture),
                    Identifier.CODEC.optionalFieldOf("particle").forGetter(OverlayAppearance::particle),
                    Codec.INT.optionalFieldOf("tint", NO_TINT).forGetter(OverlayAppearance::tint),
                    Codec.BOOL.optionalFieldOf("emissive", false).forGetter(OverlayAppearance::emissive),
                    Codec.STRING.optionalFieldOf("trigger").forGetter(OverlayAppearance::trigger)
            ).apply(instance, OverlayAppearance::new)).validate(OverlayAppearance::validate);

    @Override
    public Type type() {
        return Type.OVERLAY;
    }

    /** True when no trigger gates this overlay, so it draws for as long as the entry resolves. */
    public boolean isAlwaysOn() {
        return trigger.isEmpty();
    }

    private static DataResult<OverlayAppearance> validate(OverlayAppearance overlay) {
        if (overlay.texture.isEmpty() && overlay.particle.isEmpty()) {
            return DataResult.error(() ->
                    "overlay names neither a texture nor a particle, so it would render nothing");
        }
        if (overlay.trigger.isPresent() && overlay.trigger.get().isBlank()) {
            return DataResult.error(() -> "overlay has a blank trigger; omit the field instead");
        }
        return DataResult.success(overlay);
    }
}
