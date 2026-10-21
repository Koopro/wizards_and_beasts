package at.koopro.wizardsandbeasts.ministry.trace;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * What a piece of evidence is, and how much it is worth on its own.
 *
 * <p>Every kind is something the <em>server</em> observed: a Trace reading, an official's report, a Muggle's
 * account taken by the Obliviators, a player who was standing there when it happened, residue an Auror found
 * at the scene, a witness an Auror questioned, the wand itself, a plea. None of them is something a player can
 * write: a client never supplies the content of evidence, only asks for an observation to be made.
 *
 * <p>Weights are on a 0–100 scale against {@link EvidenceRules#IDENTIFY} and the conviction standards. They are
 * pinned by {@code EvidenceRulesTest}; a change here changes who gets convicted.
 */
@NullMarked
public enum EvidenceKind implements StringRepresentable {

    /** The Trace felt underage magic with no grown wizard about to blame it on. */
    TRACE_READING(70, false, true),
    /** A Ministry official watched it happen. */
    OFFICIAL_REPORT(80, false, false),
    /** One Muggle's account, taken before the Obliviators got to them. */
    MUGGLE_ACCOUNT(30, false, false),
    /** A player the server recorded as an eyewitness says what they saw. Capped, see {@link EvidenceRules}. */
    WIZARD_TESTIMONY(30, true, false),
    /** An Auror examined the scene and found the caster's magical signature on it. */
    SCENE_RESIDUE(35, false, true),
    /** The residue followed from the scene to where the caster went afterwards. */
    RESIDUE_TRAIL(30, false, true),
    /** An Auror questioned a recorded witness. */
    WITNESS_INTERVIEW(25, false, false),
    /** Priori Incantato: the wand shows the spell. Conclusive. */
    WAND_EXAMINATION(100, false, true),
    /** The accused pleaded guilty. */
    CONFESSION(100, false, true);

    public static final Codec<EvidenceKind> CODEC = StringRepresentable.fromEnum(EvidenceKind::values);

    private final int weight;
    private final boolean fromPlayer;
    private final boolean oncePerIncident;

    EvidenceKind(int weight, boolean fromPlayer, boolean oncePerIncident) {
        this.weight = weight;
        this.fromPlayer = fromPlayer;
        this.oncePerIncident = oncePerIncident;
    }

    /** What one item of this kind is worth before deduplication and caps. */
    public int weight() {
        return weight;
    }

    /** Whether this kind is the word of another player — never enough on its own. */
    public boolean fromPlayer() {
        return fromPlayer;
    }

    /**
     * Whether this kind counts once per incident whoever produced it: the same scene examined by two Aurors, or
     * the same wand examined twice, is one finding.
     */
    public boolean oncePerIncident() {
        return oncePerIncident;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
