package at.koopro.wizardsandbeasts.animagus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * A form's collision box and camera height, in blocks.
 * <p>
 * {@code eyeHeight} is carried separately because {@code EntityDimensions.scalable} derives eye
 * height from height at a fixed ratio, which is wrong for animals whose eyes do not sit near the
 * top of their body — a rat's do not.
 */
@NullMarked
public record AnimagusHitbox(float width, float height, float eyeHeight) {

    public static final Codec<AnimagusHitbox> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("width").forGetter(AnimagusHitbox::width),
            Codec.FLOAT.fieldOf("height").forGetter(AnimagusHitbox::height),
            Codec.FLOAT.fieldOf("eye_height").forGetter(AnimagusHitbox::eyeHeight)
    ).apply(instance, AnimagusHitbox::new));
}
