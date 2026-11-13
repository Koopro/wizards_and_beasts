package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The client half of the two-client duel capture ({@code spell/debug/DuelStage} is the server half). Each client
 * joins the duel server (launched with {@code --quickPlayMultiplayer}), waits for its opponent, and then — on a server
 * tick both clients read alike — holds the use key for six ticks, lets go so the spell is cast, presses again at once
 * and holds through the lock, as a player fighting a clash does. Screenshots from the player's own third-person view
 * go to {@code screenshots/duel/<role>__<shot>.png}.
 *
 * <p>Development only, inert unless {@code WB_DUEL_ROLE} is set ({@code A} west, {@code B} east).
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class DuelPlaytest {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ROLE = System.getenv("WB_DUEL_ROLE");
    private static final boolean ENABLED = ROLE != null && !FMLEnvironment.isProduction();

    /** Ticks between the server's start mark and the first press. */
    private static final int SETTLE_TICKS = 200;
    /** The mark's full length, as {@code DuelStage} sets it. */
    private static final int MARK_TICKS = 20 * 600;

    private static long startTick = -1;
    private static boolean holding;
    private static String pendingShot;
    private static boolean done;

    private DuelPlaytest() {}

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!ENABLED || done || player == null || mc.level == null) {
            return;
        }
        // The new-player heritage ceremony opens on join; the server has already made both duellists wizardkind.
        if (mc.screen != null) {
            mc.setScreen(null);
        }
        mc.options.hideGui = true;
        placeCamera(mc);
        float yaw = "A".equals(ROLE) ? -90.0F : 90.0F;
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(0.0F);

        long now = mc.level.getGameTime();
        if (startTick < 0) {
            // The server marks both duellists in the same tick once both are in (DuelStage); start a settled
            // while after seeing the mark, which both clients see on the same server tick.
            var mark = player.getEffect(net.minecraft.world.effect.MobEffects.LUCK);
            if (mark != null) {
                // Read back the server tick the mark was set on from how much of it is left, so a client that
                // sees it late still starts on the same tick as the other.
                long markedAt = now - (MARK_TICKS - mark.getDuration());
                // Rounded up to a whole window, so a few ticks of skew between the two clients' estimates
                // still land both on the same start.
                startTick = ((markedAt + SETTLE_TICKS) / SETTLE_TICKS + 1) * SETTLE_TICKS;
                LOGGER.info("Duel {}: marked at {}, begins at {}", ROLE, now, startTick);
            }
            return;
        }
        if (now < startTick) {
            return;
        }
        long t = now - startTick;
        // Hold six ticks, let go (the cast), take hold again at once and keep it through the lock.
        holding = t < 6 || (t >= 8 && t < 90);
        mc.options.keyUse.setDown(holding);
        if (t == 4) {
            pendingShot = "hold";
        } else if (t == 12) {
            pendingShot = "meet";
        } else if (t == 24) {
            pendingShot = "lock_early";
        } else if (t == 50) {
            pendingShot = "lock_mid";
        } else if (t == 80) {
            pendingShot = "lock_late";
        } else if (t == 120) {
            pendingShot = "after";
        } else if (t > 130) {
            done = true;
            mc.options.keyUse.setDown(false);
            LOGGER.info("Duel {}: done", ROLE);
            mc.stop();
        }
    }

    private static net.minecraft.world.entity.decoration.ArmorStand camera;

    /**
     * A stand off to the side of the lane: A shoots square from the south, B from three-quarters. Behind a duellist
     * the opponent and the meeting point sit straight behind the duellist's own back.
     */
    private static void placeCamera(Minecraft mc) {
        if (camera == null || camera.level() != mc.level) {
            camera = new net.minecraft.world.entity.decoration.ArmorStand(
                    net.minecraft.world.entity.EntityType.ARMOR_STAND, mc.level);
            camera.setInvisible(true);
        }
        double y = at.koopro.wizardsandbeasts.spell.debug.DuelStage.STAGE_Y;
        double midX = (at.koopro.wizardsandbeasts.spell.debug.DuelStage.WEST_X
                + at.koopro.wizardsandbeasts.spell.debug.DuelStage.EAST_X) / 2.0;
        boolean side = "A".equals(ROLE);
        double ex = side ? midX : midX + 7.0;
        double ez = side ? 9.0 : 7.0;
        double ey = y + 2.6;
        double dx = midX - ex;
        double dz = 0.5 - ez;
        double dy = (y + 1.4) - ey;
        float camYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float camPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        camera.setPos(ex, ey - camera.getEyeHeight(), ez);
        camera.setYRot(camYaw);
        camera.setXRot(camPitch);
        camera.setYHeadRot(camYaw);
        camera.xo = camera.getX();
        camera.yo = camera.getY();
        camera.zo = camera.getZ();
        camera.yRotO = camYaw;
        camera.xRotO = camPitch;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (mc.getCameraEntity() != camera) {
            mc.setCameraEntity(camera);
        }
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        if (!ENABLED || pendingShot == null) {
            return;
        }
        String shot = pendingShot;
        pendingShot = null;
        Minecraft mc = Minecraft.getInstance();
        Path file = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "duel"))
                .resolve(ROLE + "__" + shot + ".png");
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Duel: cannot write {}", file, e);
            }
        }));
    }
}
