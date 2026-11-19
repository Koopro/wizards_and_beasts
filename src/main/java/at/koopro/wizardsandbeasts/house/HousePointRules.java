package at.koopro.wizardsandbeasts.house;

import at.koopro.wizardsandbeasts.ministry.trace.Incident;
import at.koopro.wizardsandbeasts.ministry.trace.LegalClass;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.career.track.ActivityType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/** What earns and costs house points. */
@NullMarked
public final class HousePointRules {

    public static final int LESSON = 5;
    public static final int WORK = 2;
    public static final int MILESTONE = 10;
    public static final int DARK = -10;
    public static final int UNFORGIVABLE = -50;

    private HousePointRules() {}

    /** Everyday work: most of it a little, the milestones of a career more. Capped daily by {@link HouseService}. */
    public static void forWork(ServerPlayer player, ActivityType type) {
        int points = switch (type) {
            case CASE_CLOSED, SUSPECT_APPREHENDED, CURSE_BROKEN, BOND_MASTERED, RESEARCH_COMPLETED,
                 COMPANION_MASTERED, TREASURE_RETURNED -> MILESTONE;
            default -> WORK;
        };
        HouseService.award(player, points);
    }

    /** O.W.L. results: points for every subject the sitting raised, by the new grade. Not capped. */
    public static void forExam(ServerPlayer player, Map<OWLSubject, OWLGrade> before, Map<OWLSubject, OWLGrade> after) {
        int total = 0;
        for (Map.Entry<OWLSubject, OWLGrade> e : after.entrySet()) {
            OWLGrade old = before.get(e.getKey());
            if (old != null && old.ordinal() <= e.getValue().ordinal()) {
                continue;
            }
            total += switch (e.getValue()) {
                case O -> 20;
                case E -> 15;
                case A -> 10;
                default -> 0;
            };
        }
        HouseService.awardUncapped(player.level().getServer(), HouseService.houseOf(player), total);
    }

    /** The Ministry named a dark witch or wizard: their house pays, if they are about to answer for it. */
    public static void forDarkMagic(MinecraftServer server, Incident incident) {
        ServerPlayer caster = server.getPlayerList().getPlayer(incident.caster());
        if (caster == null) {
            return;
        }
        int delta = incident.legalClass() == LegalClass.UNFORGIVABLE ? UNFORGIVABLE : DARK;
        HouseService.awardUncapped(server, HouseService.houseOf(caster), delta);
    }
}
