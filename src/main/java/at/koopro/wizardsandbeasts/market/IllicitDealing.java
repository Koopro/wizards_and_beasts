package at.koopro.wizardsandbeasts.market;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.location.LocationDefinition;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import at.koopro.wizardsandbeasts.ministry.licence.MinistryLicenceTags;
import at.koopro.wizardsandbeasts.ministry.trace.CastSurvey;
import at.koopro.wizardsandbeasts.skill.GameplayStat;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The server side of a deal outside the law: who saw it, how likely it is to be talked about
 * ({@link IllicitRisk}), and — when it is — the offence filed through the Ministry's existing door
 * ({@link TraceService#report}): heat, a fine billed to the vault, the permanent file and the standing deed. No
 * second law system: a fence's deal is one more thing the Ministry can hear about.
 */
@NullMarked
public final class IllicitDealing {

    /** A fixed outcome for GameTests (true: always reported, false: never); null in play. */
    private static @Nullable Boolean testOutcome;

    private IllicitDealing() {}

    /** Fixes every roll's outcome, for a deterministic test; {@code null} restores chance. */
    public static void setTestOutcome(@Nullable Boolean outcome) {
        testOutcome = outcome;
    }

    /** The offence a deal of {@code deal} files when it is reported; null for a lawful one. */
    public static @Nullable MagicalOffence offenceFor(DealClass deal) {
        return switch (deal) {
            case LEGAL -> null;
            case RESTRICTED -> MagicalOffence.TRAFFICKING_RESTRICTED;
            case STOLEN -> MagicalOffence.HANDLING_STOLEN_GOODS;
            case SERVICE -> MagicalOffence.PROCURING_ILLEGAL_MAGIC;
            case CONTRABAND -> MagicalOffence.DEALING_CONTRABAND;
        };
    }

    /** The chance this deal is reported, as the roll would see it now. */
    public static double chance(ServerPlayer player, LocationDefinition def, DealClass deal, int units) {
        IllicitRisk risk = def.risk().orElse(null);
        if (risk == null || deal == DealClass.LEGAL) {
            return 0.0;
        }
        AABB box = player.getBoundingBox().inflate(risk.watchRadius());
        double r2 = risk.watchRadius() * risk.watchRadius();
        List<Identifier> hosts = def.presence().hosts();
        int officials = 0;
        int witnesses = 0;
        for (LivingEntity seen : player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && !e.isSleeping() && e.distanceToSqr(player) <= r2
                        && e.hasLineOfSight(player))) {
            if (seen.getType().is(MinistryLicenceTags.MINISTRY_OFFICIALS)) {
                officials++;
            } else if (seen instanceof ServerPlayer
                    || (CastSurvey.isMuggle(seen) && !hosts.contains(BuiltInRegistries.ENTITY_TYPE.getKey(seen.getType())))) {
                // The fence himself is not a witness to his own trade.
                witnesses++;
            }
        }
        float notoriety = MinistryRecords.get(player).notoriety();
        double discretion = SkillSystemAPI.getGameplayBonus(player, GameplayStat.TRACE_DISCRETION);
        return risk.chance(deal, units, officials, witnesses, notoriety, discretion,
                Config.illicitDetectionScalePercent / 100.0);
    }

    /**
     * Rolls one deal. Returns whether it reached the Ministry (and so was filed). Nothing happens for a lawful
     * deal or at a place with no risk.
     */
    public static boolean roll(ServerPlayer player, LocationDefinition def, DealClass deal, int units) {
        MagicalOffence offence = offenceFor(deal);
        return offence != null && rollAs(player, def, deal, units, offence);
    }

    /**
     * {@link #roll}, filing {@code offence} instead of the deal class's own when it is reported — a bribe is
     * a service bought from a fence and runs the same risk, but what it is when caught is bribery.
     */
    public static boolean rollAs(ServerPlayer player, LocationDefinition def, DealClass deal, int units,
                                 MagicalOffence offence) {
        if (deal == DealClass.LEGAL || def.risk().isEmpty() || !TraceService.isActive()) {
            return false;
        }
        boolean reported = testOutcome != null ? testOutcome
                : player.getRandom().nextDouble() < chance(player, def, deal, units);
        if (!reported) {
            return false;
        }
        TraceService.report(player, offence);
        PlayerFeedback.toast(player, NoticeKind.FAIL,
                Component.translatable("market.wizards_and_beasts.illicit.reported"),
                Component.translatable("market.wizards_and_beasts.illicit.reported.body", offence.displayName()));
        return true;
    }
}
