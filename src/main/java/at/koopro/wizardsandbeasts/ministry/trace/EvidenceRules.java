package at.koopro.wizardsandbeasts.ministry.trace;

import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How much the evidence on an incident is worth, and what that is enough for. Pure and deterministic.
 *
 * <p>The rules that keep this fair, each pinned by {@code EvidenceRulesTest}:
 *
 * <ol>
 *   <li><b>One source, one voice.</b> A witness who gave an account and was later interviewed counts once, at the
 *       better of the two. Sourceless evidence, and kinds that are one finding however often they are made
 *       ({@link EvidenceKind#oncePerIncident()}: residue, the wand, a plea), count once per kind.</li>
 *   <li><b>Players corroborate, they do not convict.</b> Testimony from players is capped at
 *       {@link #PLAYER_TESTIMONY_CAP}, and evidence that is <em>only</em> players' word never reaches
 *       {@link #IDENTIFY}. However many friends a player brings, they cannot put a name to an incident, let alone
 *       carry a hearing, without something the Ministry saw for itself.</li>
 *   <li><b>The graver the charge, the more it takes.</b> A conviction needs {@link #standardFor} of the charge's
 *       legal class; an appeal raises the bar by {@link #APPEAL_BONUS}.</li>
 * </ol>
 */
@NullMarked
public final class EvidenceRules {

    /** What it takes to put a name to an incident. */
    public static final int IDENTIFY = 50;
    /** The most all players' testimony on one incident can add together. */
    public static final int PLAYER_TESTIMONY_CAP = 45;
    /** The most any incident's evidence can be worth; past this it is simply proven. */
    public static final int MAX_STRENGTH = 200;
    /** How much more an appeal court asks for than the hearing did. */
    public static final int APPEAL_BONUS = 15;
    /** How long magical residue stays readable at a scene: three in-game days. */
    public static final long SCENE_FRESH_TICKS = 72000L;

    private EvidenceRules() {}

    /** The strength of the evidence on one incident, after deduplication and caps. */
    public static int strength(Collection<Evidence> evidence) {
        Map<UUID, Integer> bySource = new HashMap<>();
        Map<UUID, Boolean> sourceIsPlayer = new HashMap<>();
        Map<EvidenceKind, Integer> sourceless = new EnumMap<>(EvidenceKind.class);
        for (Evidence item : evidence) {
            EvidenceKind kind = item.kind();
            if (item.source().isEmpty() || kind.oncePerIncident()) {
                sourceless.merge(kind, kind.weight(), Math::max);
                continue;
            }
            UUID source = item.source().get();
            Integer before = bySource.get(source);
            if (before == null || kind.weight() > before) {
                bySource.put(source, kind.weight());
                sourceIsPlayer.put(source, kind.fromPlayer());
            }
        }
        int ministry = 0;
        int players = 0;
        for (Map.Entry<UUID, Integer> entry : bySource.entrySet()) {
            if (sourceIsPlayer.get(entry.getKey())) {
                players += entry.getValue();
            } else {
                ministry += entry.getValue();
            }
        }
        for (int weight : sourceless.values()) {
            ministry += weight;
        }
        players = Math.min(players, PLAYER_TESTIMONY_CAP);
        int total = ministry + players;
        if (ministry == 0) {
            total = Math.min(total, IDENTIFY - 1);
        }
        return Math.min(MAX_STRENGTH, total);
    }

    /** Whether the evidence puts a name to the incident. */
    public static boolean identifies(Collection<Evidence> evidence) {
        return strength(evidence) >= IDENTIFY;
    }

    /** The evidence a hearing needs before it will convict on a charge of this class. */
    public static int standardFor(LegalClass legalClass) {
        return switch (legalClass) {
            case UNRESTRICTED, RESTRICTED -> IDENTIFY;
            case DARK -> 60;
            case UNFORGIVABLE -> 75;
        };
    }

    /** Whether {@code strength} carries a charge of this class, with {@code bonus} added to the bar. */
    public static boolean meetsStandard(int strength, LegalClass legalClass, int bonus) {
        return strength >= standardFor(legalClass) + Math.max(0, bonus);
    }

    /** Whether residue cast at {@code castTick} can still be read at {@code now}. */
    public static boolean sceneFresh(long castTick, long now) {
        return now - castTick <= SCENE_FRESH_TICKS;
    }
}
