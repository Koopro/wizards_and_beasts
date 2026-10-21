package at.koopro.wizardsandbeasts.travel.trial;

import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.location.LocationService;
import at.koopro.wizardsandbeasts.location.LocationServices;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.travel.TimeTrialS2CPayload;
import at.koopro.wizardsandbeasts.owl.career.track.ActivityType;
import at.koopro.wizardsandbeasts.owl.career.track.CareerProgression;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.travel.TravelRecord;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Broom time trials: a broom shop lays a loop of hoops in the sky around its counter, and the player flies it against
 * the clock.
 *
 * <p>The {@code time_trial} location service arms a course (the board has already checked presence, tier and fee).
 * {@link #tick} then judges every flown tick through {@link TimeTrialSession}, on the server, from the broom's own
 * position. A finish records {@link ActivityType#TIME_TRIAL_FINISHED} — and {@link ActivityType#TIME_TRIAL_PAR} under
 * par — through the one activity stream, so the shop's standing and the Quidditch track's contracts count it.
 *
 * <p>Par is set from the course's length (see {@link TimeTrialCourse#PAR_SPEED}), so the broom is the progression:
 * the same course is a scramble on a Cleansweep and a cruise on a Nimbus.
 */
@NullMarked
public final class TimeTrialService {

    private static final PlayerScopedState<Active> ACTIVE = PlayerScopedState.create("time_trial");

    /** A session and the world it was armed in. */
    private record Active(TimeTrialSession session, ResourceKey<Level> world) {}

    private TimeTrialService() {}

    public static void register(LocationServices.Sink sink) {
        sink.register("time_trial", new Arm());
    }

    /** Whether the player has a course armed or running. */
    public static boolean active(ServerPlayer player) {
        return ACTIVE.get(player) != null;
    }

    /** The armed or running session, for the Travel tab; null when there is none. */
    public static @Nullable TimeTrialSession session(ServerPlayer player) {
        Active active = ACTIVE.get(player);
        return active == null ? null : active.session();
    }

    /** The {@code time_trial} service: arms a fresh course around the counter. */
    static final class Arm implements LocationService {
        @Override
        public Quote quote(ServerPlayer player, Use use) {
            if (!ModuleManager.isEnabled(Module.BROOM_FLIGHT)) {
                return Quote.no(Component.translatable("travel.wizards_and_beasts.trial.no_brooms"));
            }
            int best = player.getData(ModAttachments.TRAVEL_RECORD.get()).bestTicks(use.location().toString());
            return Quote.yes(best < 0
                    ? Component.translatable("travel.wizards_and_beasts.trial.quote")
                    : Component.translatable("travel.wizards_and_beasts.trial.quote_best", clock(best)));
        }

        @Override
        public boolean perform(ServerPlayer player, Use use) {
            if (!ModuleManager.isEnabled(Module.BROOM_FLIGHT) || !(player.level() instanceof ServerLevel level)) {
                return false;
            }
            if (!use.charge(player, use.fee())) {
                return false;
            }
            long now = level.getGameTime();
            long seed = player.getUUID().getLeastSignificantBits() ^ (now / 24_000L) * 31L
                    ^ player.blockPosition().asLong();
            TimeTrialCourse course = TimeTrialCourse.generate(player.getX(), player.getZ(), seed,
                    (x, z) -> level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), level.getMaxY());
            arm(player, use.location().toString(), course);
            PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                    Component.translatable("travel.wizards_and_beasts.trial.armed"),
                    Component.translatable("travel.wizards_and_beasts.trial.armed.body", clock(course.parTicks())));
            return true;
        }
    }

    /**
     * Arms {@code course} for {@code player}, replacing any course they had. The one door both the counter service
     * and the game tests use, so a test flies exactly the session the service would have started.
     */
    public static void arm(ServerPlayer player, String courseId, TimeTrialCourse course) {
        TimeTrialSession session = new TimeTrialSession(courseId, course, player.level().getGameTime());
        ACTIVE.put(player, new Active(session, player.level().dimension()));
        send(player, TimeTrialS2CPayload.ARMED, session, -1, "");
    }

    /** Judges one tick of a player's armed or running course. Server thread, every tick. */
    public static void tick(ServerPlayer player) {
        Active active = ACTIVE.get(player);
        if (active == null) {
            return;
        }
        TimeTrialSession session = active.session();
        long now = player.level().getGameTime();
        BroomEntity broom = player.getVehicle() instanceof BroomEntity b ? b : null;
        Vec3 position = broom != null ? broom.position() : player.position();
        double maxStep = TimeTrialSession.maxStep(broom != null ? broom.getTopSpeed() : 0.0f);
        TimeTrialSession.Event event = session.step(position, now, broom != null,
                player.level().dimension().equals(active.world()), maxStep);
        switch (event) {
            case NONE -> { }
            case STARTED, RING -> {
                player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                        SoundSource.PLAYERS, 0.6f, 0.8f + 0.1f * session.passes());
                send(player, TimeTrialS2CPayload.RUNNING, session, -1, "");
            }
            case FINISHED -> finish(player, session, now);
            default -> end(player, session, event);
        }
    }

    private static void finish(ServerPlayer player, TimeTrialSession session, long now) {
        ACTIVE.remove(player);
        int ticks = session.elapsed(now);
        TravelRecord before = player.getData(ModAttachments.TRAVEL_RECORD.get());
        int previousBest = before.bestTicks(session.courseId());
        TravelRecord after = before.withTrialFinished(session.courseId(), ticks);
        player.setData(ModAttachments.TRAVEL_RECORD.get(), after);
        boolean par = ticks <= session.course().parTicks();
        Identifier location = Identifier.tryParse(session.courseId());
        if (location != null) {
            CareerProgression.record(player, ActivityType.TIME_TRIAL_FINISHED, location);
            if (par) {
                CareerProgression.record(player, ActivityType.TIME_TRIAL_PAR, location);
            }
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                0.7f, par ? 1.2f : 0.9f);
        send(player, TimeTrialS2CPayload.FINISHED, session, ticks, "");
        boolean record = previousBest < 0 || ticks < previousBest;
        PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                Component.translatable(par ? "travel.wizards_and_beasts.trial.finished_par"
                        : "travel.wizards_and_beasts.trial.finished", clock(ticks)),
                Component.translatable(record ? "travel.wizards_and_beasts.trial.new_best"
                        : "travel.wizards_and_beasts.trial.best", clock(after.bestTicks(session.courseId())),
                        clock(session.course().parTicks())));
    }

    private static void end(ServerPlayer player, TimeTrialSession session, TimeTrialSession.Event why) {
        ACTIVE.remove(player);
        String key = "travel.wizards_and_beasts.trial.end." + why.name().toLowerCase(Locale.ROOT);
        send(player, TimeTrialS2CPayload.ENDED, session, -1, key);
        PlayerFeedback.actionBar(player, Component.translatable(key).withStyle(ChatFormatting.RED));
    }

    /** Withdraws any course without ceremony: logout, death, module switched off. */
    public static void clear(ServerPlayer player) {
        if (ACTIVE.remove(player) != null) {
            PacketDistributor.sendToPlayer(player, TimeTrialS2CPayload.cleared());
        }
    }

    private static void send(ServerPlayer player, int state, TimeTrialSession session, int resultTicks, String reason) {
        int best = player.getData(ModAttachments.TRAVEL_RECORD.get()).bestTicks(session.courseId());
        PacketDistributor.sendToPlayer(player, new TimeTrialS2CPayload(state, session.course(), session.passes(),
                session.running() ? session.startedAt() : -1L, resultTicks, best, reason));
    }

    /** "1:05.3" from ticks. */
    public static String clock(int ticks) {
        int tenths = Math.max(0, ticks) / 2;
        return String.format(Locale.ROOT, "%d:%02d.%d", tenths / 600, (tenths / 10) % 60, tenths % 10);
    }
}
