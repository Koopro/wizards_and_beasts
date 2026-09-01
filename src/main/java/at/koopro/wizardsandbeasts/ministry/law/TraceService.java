package at.koopro.wizardsandbeasts.ministry.law;

import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.data.PlayerMinistryRecord;
import at.koopro.wizardsandbeasts.ministry.post.MinistryPost;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * The Trace. Illegal magic is registered the moment it is worked — there is no line of sight to dodge and
 * no wilderness to hide in, which is how the Ministry knows about Unforgivables in the books.
 *
 * <p>Every crime in the mod enters the system through {@link #report}: it is the one seam, so adding a new
 * offence never means teaching another subsystem how notoriety works. Reporting is idempotent per call —
 * callers report an event, not a state — and the whole thing goes quiet when {@link Module#MINISTRY} is off,
 * which is the supported way to play with magic legal.
 */
@NullMarked
public final class TraceService {

    /** Notoriety shed per second while lying low. Slow enough that a spree takes real time to cool. */
    private static final float DECAY_PER_SECOND = 0.05f;

    private TraceService() {}

    /** True while the Ministry is watching at all. */
    public static boolean isActive() {
        return ModuleManager.isEnabled(Module.MINISTRY);
    }

    /**
     * Registers one committed offence: files it permanently, adds heat, and — for the paperwork offences
     * that carry one — assesses the fine. All three scale by the offender's priors for that same offence.
     *
     * @return the wanted level after the report, or {@link WantedLevel#CLEAR} when the Trace is off
     */
    public static WantedLevel report(ServerPlayer offender, MagicalOffence offence) {
        if (!isActive()) {
            return WantedLevel.CLEAR;
        }
        PlayerMinistryRecord before = MinistryRecords.get(offender);
        float priors = before.repeatMultiplier(offence);
        float gain = offence.notoriety() * priors;

        MinistryRecords.mutate(offender, record -> record.withOffence(offence, gain));
        PlayerMinistryRecord after = MinistryRecords.get(offender);

        announce(offender, offence, before.wantedLevel(), after.wantedLevel());

        // Filed first, billed second, so the fine notice follows the record of what it is for. The
        // multiplier passed is the one measured *before* this offence was filed — the same value the
        // notoriety gain used — so heat and fine escalate in step and a first offence is unscaled in both.
        MinistryFines.assessFor(offender, offence, priors);

        // Standing reads the *act*, not the heat. The Ministry axis already tracks notoriety on its
        // own, so a deed on this trigger is for the other axes — a crime that says something about
        // who you are becoming rather than about how badly you are wanted.
        at.koopro.wizardsandbeasts.standing.deed.DeedService.onOffence(offender, offence.getSerializedName());
        return after.wantedLevel();
    }

    /**
     * Cools heat for a player who is not currently being sought, or warms it for one who is ignoring a
     * bill. Called from the Ministry tick.
     *
     * <p>Only notoriety moves — the criminal file never does. A fugitive or a serving prisoner cools not
     * at all: you do not become less wanted by hiding from a sentence you already have. Neither does a
     * debtor: an unpaid fine is the mechanical reason a paperwork offence cannot simply be waited out, and
     * it slowly heats instead, capped well short of a manhunt by {@link FineSchedule#DEBT_HEAT_CEILING}.
     */
    public static void decay(ServerPlayer player, int elapsedTicks) {
        if (!isActive()) {
            return;
        }
        PlayerMinistryRecord record = MinistryRecords.get(player);
        if (record.owesFine()) {
            float heat = FineSchedule.debtHeat(record.notoriety(), elapsedTicks);
            if (heat > 0.0f) {
                MinistryRecords.mutate(player, r -> r.withNotoriety(r.notoriety() + heat));
            }
            return;
        }
        if (!FineSchedule.mayCool(record.notoriety(), false, record.fugitive(), record.isServingSentence())) {
            return;
        }
        float shed = DECAY_PER_SECOND * (elapsedTicks / 20.0f);
        MinistryRecords.mutate(player, r -> r.withNotoriety(r.notoriety() - shed));
    }

    private static void announce(ServerPlayer offender, MagicalOffence offence,
                                 WantedLevel before, WantedLevel after) {
        if (after == before) {
            return;
        }
        if (after.dispatchesAurors() && !before.dispatchesAurors()) {
            // The moment it stops being paperwork and starts being a manhunt.
            MinistryPost.send(offender,
                    Component.translatable("ministry.wizards_and_beasts.notice.wanted.subject"),
                    Component.translatable("ministry.wizards_and_beasts.notice.wanted.body",
                            offence.displayName(), after.displayName()));
        } else {
            MinistryPost.notify(offender,
                    Component.translatable("ministry.wizards_and_beasts.notice.recorded", offence.displayName()),
                    ChatFormatting.GRAY);
        }
    }
}
