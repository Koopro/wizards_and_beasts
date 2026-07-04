package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.Optional;

/**
 * Render-only ability: multiplies a colour into the creature's model tint (obscurus smoke, ashwinder
 * ember-glow, fairy shimmer). Mirrors {@link OccamyChoranaptyxis} — server-driven, client-rendered through
 * the synced {@link GenericBeastEntity#setTint(int)} channel, never touches gameplay. When {@code pulse} is
 * set the tint eases between {@code color} and {@code pulseColor} on a slow sine so the creature breathes.
 *
 * <p>Colours are authored in JSON as hex strings — {@code "#RRGGBB"} (opaque) or {@code "#AARRGGBB"}.
 */
public record Tint(int color, boolean pulse, Optional<Integer> pulseColor, int periodTicks) implements CreatureAbility {

    /** Hex-string colour codec: {@code "#RRGGBB"} → opaque ARGB, {@code "#AARRGGBB"} → as-written. */
    private static final Codec<Integer> COLOR_CODEC = Codec.STRING.xmap(
            Tint::parseHex,
            argb -> String.format(Locale.ROOT, "#%08X", argb));

    public static final MapCodec<Tint> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            COLOR_CODEC.fieldOf("color").forGetter(Tint::color),
            Codec.BOOL.optionalFieldOf("pulse", false).forGetter(Tint::pulse),
            COLOR_CODEC.optionalFieldOf("pulse_color").forGetter(Tint::pulseColor),
            Codec.INT.optionalFieldOf("period_ticks", 40).forGetter(Tint::periodTicks)
    ).apply(instance, Tint::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.TINT;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        // Server owns the synced value; the client reads it back through getTint() at render time.
        if (entity.level().isClientSide()) {
            return;
        }
        int target = color;
        if (pulse && pulseColor.isPresent()) {
            int period = Math.max(1, periodTicks);
            // 0..1 triangle/sine over the period — eased blend toward the pulse colour and back.
            float t = 0.5f - 0.5f * Mth.cos((entity.tickCount % period) / (float) period * Mth.TWO_PI);
            target = ARGB.srgbLerp(t, color, pulseColor.get());
        }
        if (entity.getTint() != target) {
            entity.setTint(target);
        }
    }

    private static int parseHex(String raw) {
        String s = raw.startsWith("#") ? raw.substring(1) : raw;
        long v = Long.parseLong(s, 16);
        // 6 hex digits = RGB with implicit full alpha; 8 digits = ARGB verbatim.
        return s.length() <= 6 ? (int) (0xFF000000L | v) : (int) v;
    }
}
