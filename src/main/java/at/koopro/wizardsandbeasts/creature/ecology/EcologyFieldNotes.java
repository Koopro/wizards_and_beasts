package at.koopro.wizardsandbeasts.creature.ecology;

import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntry;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * What a player learns by watching. When a creature starts doing something its ecology explains — regrouping with
 * its herd, running from a predator, settling down for the day — every player who can see it from close enough
 * writes the matching capability into that creature's bestiary page as a field note.
 *
 * <p>Notes are the bestiary's account of behaviour, shown on the page from the first sighting on, so a player who
 * has watched a Mooncalf sleep through the day knows to come back at night, and one who has seen a Hippogriff
 * hold its ground knows to keep off it. Run on a change of intent only: a few dozen times a minute at most across a
 * whole loaded world, and each run is a walk over the level's player list.
 */
@NullMarked
public final class EcologyFieldNotes {

    /** A behaviour is seen from this close, with line of sight. */
    public static final double WITNESS_RANGE = 16.0;

    private EcologyFieldNotes() {}

    public static void witness(PathfinderMob mob, EcologyBrain.Decision decision) {
        EcologyCapability cause = decision.cause() != null ? decision.cause() : decision.intent().reveals();
        if (cause == null || !ModuleManager.isEnabled(Module.BESTIARY) || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        List<BestiaryEntry> entries = BestiaryDiscoveryHandler.entriesFor(mob.getType());
        if (entries.isEmpty()) {
            return;
        }
        String note = cause.getSerializedName();
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.distanceToSqr(mob) > WITNESS_RANGE * WITNESS_RANGE
                    || mob.isInvisibleTo(player) || !player.hasLineOfSight(mob)) {
                continue;
            }
            boolean learned = false;
            for (BestiaryEntry entry : entries) {
                learned |= BestiaryDataHelper.addFieldNote(player, entry.id(), note);
            }
            if (learned) {
                PlayerFeedback.actionBar(player, Component.translatable("bestiary.wizards_and_beasts.ecology.noted",
                        mob.getType().getDescription(), Component.translatable(cause.noteKey()))
                        .withStyle(ChatFormatting.DARK_GREEN));
            }
        }
    }
}
