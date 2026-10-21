package at.koopro.wizardsandbeasts.owl.career.track;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * Things a wizard does in the world that a career can count.
 *
 * <p>Each value names one hook site that already exists for its own reasons — a bestiary page deepening,
 * a creature fed, a bond won. A career track never invents an activity: it only decides which of these
 * its holder is paid in experience for, and which a contract asks for. Adding a career that counts
 * something new means adding a value here and one {@link CareerProgression#record} call at the place it
 * happens.
 */
@NullMarked
public enum ActivityType implements StringRepresentable {
    /** A bestiary page went deeper by the player's own doing (never an admin edit). */
    BESTIARY_TIER,
    /** A creature's field research was completed for the first time. */
    RESEARCH_COMPLETED,
    /** A bondable creature accepted food. */
    CREATURE_FED,
    /** A bond crossed one of its profile's milestones. */
    BOND_MILESTONE,
    /** A bond reached its species' mastery mark. */
    BOND_MASTERED,
    /** Two of the player's creatures produced young. */
    CREATURE_BRED,
    /** A creature let itself be groomed for its hair. */
    CREATURE_GROOMED,
    /** A companion became trained: it carries out orders reliably. */
    COMPANION_TRAINED,
    /** A companion was mastered: full bond and full training. */
    COMPANION_MASTERED,
    /**
     * The player broke the last layer of a curse on a recovered artefact with their own counter-spell
     * ({@code treasure.CurseBreaking}). The subject is the curse kind, e.g. {@code wizards_and_beasts:flagrante}.
     */
    CURSE_BROKEN,
    /**
     * A recovered artefact was surrendered to Gringotts ({@code location.gringotts.GringottsServices}). The
     * subject is the treasure site it came from, e.g. {@code wizards_and_beasts:desert_tomb}.
     */
    TREASURE_RETURNED,
    /**
     * A deal was done across a trader's counter ({@code market.MarketTrade}): goods sold or bought, one record per
     * deal however many units. The subject is the location, e.g. {@code wizards_and_beasts:apothecary}, so a
     * place's standing counts only its own trade.
     */
    GOODS_TRADED,
    /**
     * A courier parcel reached its destination before its deadline ({@code travel.courier.CourierService}). The
     * subject is the location that dispatched it, so the sender's standing counts its own parcels.
     */
    COURIER_DELIVERED,
    /** As {@link #COURIER_DELIVERED}, inside the express window. Recorded in addition to it, never instead. */
    COURIER_EXPRESS,
    /**
     * A broom time trial was flown through every ring ({@code travel.trial.TimeTrialService}). The subject is the
     * location whose course it was.
     */
    TIME_TRIAL_FINISHED,
    /** As {@link #TIME_TRIAL_FINISHED}, under the course's par. Recorded in addition to it. */
    TIME_TRIAL_PAR,
    /**
     * A stretch of country overflown high on a broom for the first time ({@code travel.aerial.AerialSurvey}). The
     * subject is the biome below, so a contract can ask for distinct country.
     */
    AERIAL_SURVEY,
    /** A treasure site spotted from the air for the first time. The subject is the treasure site. */
    TREASURE_SIGHTED,
    /**
     * An Auror filed evidence on a case they hold: residue at a scene, a trail, an interview
     * ({@code ministry.trace.MinistryCasework}). The subject is the case kind: {@code wizards_and_beasts:player_case}
     * or the {@code ministry_cases/} template.
     */
    EVIDENCE_GATHERED,
    /** An Auror questioned a witness on a case they hold. Recorded in addition to {@link #EVIDENCE_GATHERED}. */
    WITNESS_INTERVIEWED,
    /** The evidence an Auror gathered put a name or a face to a case. */
    SUSPECT_IDENTIFIED,
    /** An Auror took a suspect in alive. */
    SUSPECT_APPREHENDED,
    /**
     * A case an Auror held was closed by a hearing or an apprehension — whatever the verdict. Aurors are paid for
     * closing cases, never for convictions.
     */
    CASE_CLOSED;

    public static final Codec<ActivityType> CODEC = StringRepresentable.fromEnum(ActivityType::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
