package at.koopro.wizardsandbeasts.animagus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * Optional per-form sound events. Each is independently optional: a form may have a hurt cry and no
 * footstep. Absent means "use nothing", not "use the player's" — a cat does not grunt like a wizard.
 */
@NullMarked
public record AnimagusSounds(
        Optional<Identifier> ambient,
        Optional<Identifier> hurt,
        Optional<Identifier> step) {

    public static final AnimagusSounds NONE =
            new AnimagusSounds(Optional.empty(), Optional.empty(), Optional.empty());

    public static final Codec<AnimagusSounds> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("ambient").forGetter(AnimagusSounds::ambient),
            Identifier.CODEC.optionalFieldOf("hurt").forGetter(AnimagusSounds::hurt),
            Identifier.CODEC.optionalFieldOf("step").forGetter(AnimagusSounds::step)
    ).apply(instance, AnimagusSounds::new));
}
