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
     * Registers one committed offence: files it permanently and adds heat, scaled by the offender's priors
     * for that same offence.
     *
     * @return the wanted level after the report, or {@link WantedLevel#CLEAR} when the Trace is off
     */
    public static WantedLevel report(ServerPlayer offender, MagicalOffence offence) {
        if (!isActive()) {
            return WantedLevel.CLEAR;
        }
        PlayerMinistryRecord before = MinistryRecords.get(offender);
        float gain = offence.notoriety() * before.repeatMultiplier(offence);

        MinistryRecords.mutate(offender, record -> record.withOffence(offence, gain));
        PlayerMinistryRecord after = MinistryRecords.get(offender);

        announce(offender, offence, before.wantedLevel(), after.wantedLevel());
        return after.wantedLevel();
    }

    /**
     * Cools heat for a player who is not currently being sought. Called from the Ministry tick.
     *
     * <p>Only notoriety decays — the criminal file never does. A fugitive or a serving prisoner cools not
     * at all: you do not become less wanted by hiding from a sentence you already have.
     */
    public static void decay(ServerPlayer player, int elapsedTicks) {
        if (!isActive()) {
            return;
        }
        PlayerMinistryRecord record = MinistryRecords.get(player);
        if (record.notoriety() <= 0.0f || record.fugitive() || record.isServingSentence()) {
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
