package at.koopro.wizardsandbeasts.artefact.erised;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One thing the Mirror can show: a line of text, who it is for, and how strongly it pulls.
 *
 * <pre>{@code
 * { "text": "erised.wizards_and_beasts.vision.lost_companion", "weight": 12,
 *   "when": [ { "type": "lost_companion" } ], "arg": "lost_companion" }
 * }</pre>
 *
 * <p>{@code text} is a translation key. {@code when} must all hold ({@link VisionCondition}); an empty list is a
 * vision anyone may see, which a table keeps light so the specific ones win. {@code arg} fills the text's
 * {@code %s}: today only the most recently lost companion's name.
 */
@NullMarked
public record ErisedVision(String text, int weight, List<VisionCondition> when, Optional<Arg> arg) {

    public static final Codec<ErisedVision> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("text").forGetter(ErisedVision::text),
            Codec.intRange(1, 1000).optionalFieldOf("weight", 10).forGetter(ErisedVision::weight),
            VisionCondition.CODEC.listOf().optionalFieldOf("when", List.of()).forGetter(ErisedVision::when),
            Arg.CODEC.optionalFieldOf("arg").forGetter(ErisedVision::arg)
    ).apply(i, ErisedVision::new));

    public ErisedVision {
        when = List.copyOf(when);
    }

    public boolean shownTo(Gazer gazer) {
        for (VisionCondition condition : when) {
            if (!condition.test(gazer)) {
                return false;
            }
        }
        return arg.isEmpty() || arg.get().value(gazer).isPresent();
    }

    /** What a vision's {@code %s} may be filled with. */
    public enum Arg implements StringRepresentable {
        LOST_COMPANION;

        static final Codec<Arg> CODEC = StringRepresentable.fromEnum(Arg::values);

        public Optional<String> value(Gazer gazer) {
            return gazer.lostCompanions().stream().findFirst();
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
