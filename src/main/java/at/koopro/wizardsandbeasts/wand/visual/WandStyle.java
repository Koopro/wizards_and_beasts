package at.koopro.wizardsandbeasts.wand.visual;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * How a wand has been restyled: the looks its owner chose over the ones its wood and core would give it. Each part
 * left empty keeps the wand's own. The shape (handle, shaft, ornament) lives in the wand's configuration as it
 * always has; this carries the rest: the wood's colour and grain, the metal of its fittings and the colour of its
 * core's light.
 *
 * <p>Looks only. Nothing here is read by a stat, a cast or a rule: a wand restyled to look like elder is still the
 * holly it was, and its name keeps its real wood's rarity colour.
 *
 * @param tint  the wood's colour, RGB
 * @param grain the grain sheet
 * @param metal the fittings
 * @param glint the core's light, RGB
 */
@NullMarked
public record WandStyle(Optional<Integer> tint, Optional<Identifier> grain, Optional<WandMetal> metal,
                        Optional<Integer> glint) {

    public static final WandStyle NONE = new WandStyle(Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty());

    public static final Codec<WandStyle> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("tint").forGetter(WandStyle::tint),
            Identifier.CODEC.optionalFieldOf("grain").forGetter(WandStyle::grain),
            WandMetal.CODEC.optionalFieldOf("metal").forGetter(WandStyle::metal),
            Codec.INT.optionalFieldOf("glint").forGetter(WandStyle::glint)
    ).apply(i, WandStyle::new));

    public static final StreamCodec<ByteBuf, WandStyle> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.INT), WandStyle::tint,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), WandStyle::grain,
            ByteBufCodecs.optional(WandMetal.STREAM_CODEC), WandStyle::metal,
            ByteBufCodecs.optional(ByteBufCodecs.INT), WandStyle::glint,
            WandStyle::new);

    public boolean isEmpty() {
        return tint.isEmpty() && grain.isEmpty() && metal.isEmpty() && glint.isEmpty();
    }

    public WandStyle withTint(Optional<Integer> value) {
        return new WandStyle(value.map(c -> c & 0xFFFFFF), grain, metal, glint);
    }

    public WandStyle withGrain(Optional<Identifier> value) {
        return new WandStyle(tint, value, metal, glint);
    }

    public WandStyle withMetal(Optional<WandMetal> value) {
        return new WandStyle(tint, grain, value, glint);
    }

    public WandStyle withGlint(Optional<Integer> value) {
        return new WandStyle(tint, grain, metal, value.map(c -> c & 0xFFFFFF));
    }
}
