package at.koopro.wizardsandbeasts.ministry.trace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * The last hearing on a wizard's file, kept whole so it can be appealed and undone.
 *
 * <p>Everything an appeal needs to re-hear the case and everything an overturn needs to give back is here: the
 * hearing as it was put, the ruling, and what the ruling actually cost — the heat and fine its filed offence
 * added, and the day the wand was to come back.
 *
 * @param caseId          the case it closed
 * @param closedAt        game tick of the ruling
 * @param hearing         the hearing as it was put to the court
 * @param ruling          what the court decided, as it now stands (an appeal replaces it)
 * @param notorietyAdded  heat the filed offence added
 * @param fineKnuts       the fine the filed offence billed
 * @param appealBy        last game tick an appeal may be lodged; 0 when none may be
 * @param appeal          what has become of an appeal
 */
@NullMarked
public record ClosedCase(long caseId, long closedAt, Wizengamot.Hearing hearing, Wizengamot.Ruling ruling,
                         float notorietyAdded, long fineKnuts, long appealBy, Appeal appeal) {

    /** Where an appeal stands. */
    public enum Appeal implements StringRepresentable {
        NONE,
        UPHELD,
        REDUCED,
        OVERTURNED;

        public static final Codec<Appeal> CODEC = StringRepresentable.fromEnum(Appeal::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<ClosedCase> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("case", 0L).forGetter(ClosedCase::caseId),
            Codec.LONG.optionalFieldOf("closed_at", 0L).forGetter(ClosedCase::closedAt),
            Wizengamot.Hearing.CODEC.fieldOf("hearing").forGetter(ClosedCase::hearing),
            Wizengamot.Ruling.CODEC.fieldOf("ruling").forGetter(ClosedCase::ruling),
            Codec.FLOAT.optionalFieldOf("notoriety_added", 0.0f).forGetter(ClosedCase::notorietyAdded),
            Codec.LONG.optionalFieldOf("fine", 0L).forGetter(ClosedCase::fineKnuts),
            Codec.LONG.optionalFieldOf("appeal_by", 0L).forGetter(ClosedCase::appealBy),
            Appeal.CODEC.optionalFieldOf("appeal", Appeal.NONE).forGetter(ClosedCase::appeal)
    ).apply(instance, ClosedCase::new));

    public ClosedCase withAppeal(Appeal outcome, Wizengamot.Ruling standing) {
        return new ClosedCase(caseId, closedAt, hearing, standing, notorietyAdded, fineKnuts, appealBy, outcome);
    }
}
