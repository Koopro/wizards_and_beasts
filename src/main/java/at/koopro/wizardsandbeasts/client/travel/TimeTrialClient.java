package at.koopro.wizardsandbeasts.client.travel;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.travel.TimeTrialS2CPayload;
import at.koopro.wizardsandbeasts.travel.trial.TimeTrialCourse;
import at.koopro.wizardsandbeasts.travel.trial.TimeTrialService;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * The flyer's half of a time trial: the hoops drawn in the sky and a clock in the corner of the screen.
 *
 * <p>Holds only what the server last sent ({@link TimeTrialS2CPayload}); judges nothing. The hoops are particle rings
 * — the next one bright, the one after faint — redrawn a few times a second, so a course costs nothing when no one is
 * flying it and leaves nothing behind.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class TimeTrialClient {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "time_trial");

    /** Points per drawn hoop. */
    private static final int RING_POINTS = 28;
    /** Ticks between redraws. */
    private static final int REDRAW_TICKS = 4;
    /** How long a finish or a disqualification stays on screen, ticks. */
    private static final int RESULT_HOLD_TICKS = 100;
    private static final DustParticleOptions FAINT = new DustParticleOptions(0xC9A227, 1.2f);

    private static @Nullable TimeTrialS2CPayload state;
    private static long resultShownAt;

    private TimeTrialClient() {}

    public static void accept(TimeTrialS2CPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        state = payload.state() == TimeTrialS2CPayload.CLEARED ? null : payload;
        resultShownAt = mc.level == null ? 0L : mc.level.getGameTime();
    }

    /** Nothing about one server's courses or profile survives into the next. */
    @SubscribeEvent
    public static void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        state = null;
        ClientTravelProfileState.clear();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        TimeTrialS2CPayload s = state;
        if (s == null || mc.level == null || mc.player == null) {
            return;
        }
        long now = mc.level.getGameTime();
        if (isOver(s) && now - resultShownAt > RESULT_HOLD_TICKS) {
            state = null;
            return;
        }
        if (isOver(s) || now % REDRAW_TICKS != 0) {
            return;
        }
        TimeTrialCourse course = s.course();
        if (course.rings().isEmpty()) {
            return;
        }
        drawRing(mc, course, s.passes(), true);
        if (s.passes() + 1 < course.passesToFinish()) {
            drawRing(mc, course, s.passes() + 1, false);
        }
    }

    private static boolean isOver(TimeTrialS2CPayload s) {
        return s.state() == TimeTrialS2CPayload.FINISHED || s.state() == TimeTrialS2CPayload.ENDED;
    }

    /** One hoop, upright and facing along the course: its plane is normal to the line from the hoop before it. */
    private static void drawRing(Minecraft mc, TimeTrialCourse course, int passes, boolean next) {
        Vec3 centre = course.ringFor(passes);
        Vec3 previous = course.ringFor(passes + course.rings().size() - 1);
        Vec3 forward = new Vec3(centre.x - previous.x, 0.0, centre.z - previous.z);
        forward = forward.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : forward.normalize();
        Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 up = new Vec3(0, 1, 0);
        double radius = course.ringRadius();
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = Math.PI * 2.0 * i / RING_POINTS;
            Vec3 p = centre.add(side.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
            if (next) {
                mc.level.addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, 0.0, 0.0, 0.0);
            } else {
                mc.level.addParticle(FAINT, p.x, p.y, p.z, 0.0, 0.0, 0.0);
            }
        }
    }

    /** The clock: hoops passed, time, par and best — top centre, under the boss bar line. */
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        TimeTrialS2CPayload s = state;
        if (s == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        long now = mc.level.getGameTime();
        int total = s.course().passesToFinish();
        Component line;
        if (s.state() == TimeTrialS2CPayload.FINISHED) {
            line = Component.translatable("travel.wizards_and_beasts.trial.hud.finished",
                    TimeTrialService.clock(s.resultTicks()), TimeTrialService.clock(s.course().parTicks()));
        } else if (s.state() == TimeTrialS2CPayload.ENDED) {
            line = Component.translatable(s.reasonKey());
        } else if (s.startedAt() < 0) {
            line = Component.translatable("travel.wizards_and_beasts.trial.hud.armed",
                    TimeTrialService.clock(s.course().parTicks()));
        } else {
            line = Component.translatable("travel.wizards_and_beasts.trial.hud.running", s.passes(), total,
                    TimeTrialService.clock((int) (now - s.startedAt())),
                    TimeTrialService.clock(s.course().parTicks()));
        }
        int width = graphics.guiWidth();
        int x = (width - mc.font.width(line)) / 2;
        graphics.drawString(mc.font, line, x, 24, 0xFFF2E3B3, true);
        if (s.bestTicks() >= 0 && s.state() != TimeTrialS2CPayload.ENDED) {
            Component best = Component.translatable("travel.wizards_and_beasts.trial.hud.best",
                    TimeTrialService.clock(s.bestTicks()));
            graphics.drawString(mc.font, best, (width - mc.font.width(best)) / 2, 35, 0xFFB8A77A, true);
        }
    }
}
