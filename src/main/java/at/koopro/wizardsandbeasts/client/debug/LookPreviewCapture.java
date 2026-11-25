package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Photographs the look previews (docs/development/LOOK_PREVIEW_REFERENCE.md): the Quidditch balls, Norbert's egg in a
 * fire, and the Dark Mark over the trees at night. Development only, inert unless {@code WB_LOOK_PREVIEW=1};
 * {@code WB_LOOK_PREVIEW_WORLD} names the save. Shots go to {@code screenshots/look_preview}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class LookPreviewCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = System.getenv("WB_LOOK_PREVIEW") != null && !FMLEnvironment.isProduction();

    private record Step(String shot, int ticks, Consumer<ServerPlayer> server) {}

    private static List<Step> script() {
        List<Step> steps = new java.util.ArrayList<>();
        steps.add(new Step("", 100, LookPreviewCapture::stage));
        // The Quidditch balls in vanilla style: a hero shot, then four views of each ball.
        steps.add(new Step("", 40, p -> eye(p, QX + 1.9, 1.95, -1.2, 0f, 4f)));
        steps.add(new Step("quidditch_hero", 10, p -> {}));
        for (int b = 0; b < BALLS.length; b++) {
            for (int v = 0; v < VIEWS.length; v++) {
                double x = viewX(b, v);
                double back = VIEW_BACK[b];
                // A three-quarter look from up and to one side.
                steps.add(new Step("", 30, p -> eyeAt(p, x - 0.45 * back, VIEW_Y + 0.55 * back, VIEW_Z - back,
                        x, VIEW_Y, VIEW_Z)));
                steps.add(new Step(BALLS[b] + "_" + VIEWS[v], 10, p -> {}));
            }
        }
        // Held as vanilla holds a snowball: the flat sprite in hand, the hotbar showing the icons.
        String[] held = {"quaffle", "bludger", "golden_snitch"};
        steps.add(new Step("", 40, p -> {
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.getInventory().clearContent();
            for (int i = 0; i < held.length; i++) {
                p.getInventory().setItem(i, new net.minecraft.world.item.ItemStack(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                                net.minecraft.resources.Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, held[i]))));
            }
            p.getInventory().setItem(3, new net.minecraft.world.item.ItemStack(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                            net.minecraft.resources.Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "beaters_bat"))));
            eye(p, QX + 1.9, 1.95, -1.2, 0f, 4f);
            hold(p, 0);
            showGui = true;
        }));
        for (int i = 0; i < held.length; i++) {
            int slot = i;
            if (i > 0) {
                steps.add(new Step("", 20, p -> hold(p, slot)));
            }
            steps.add(new Step("hand_" + held[i], 10, p -> {}));
        }
        steps.add(new Step("", 10, p -> {
            showGui = false;
            p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        }));
        steps.addAll(OTHER_SCENES);
        return steps;
    }

    private static final List<Step> OTHER_SCENES = List.of(
            new Step("", 40, p -> look(p, 8.5, 0.4, -2.2, 0f, 22f)),
            new Step("ridgeback_egg", 10, p -> {}),
            new Step("", 40, p -> look(p, 17.0, 0.7, -1.4, 0f, 6f)),
            new Step("owls", 10, p -> {}),
            new Step("", 40, p -> look(p, 28.5, 0.5, -3.0, 0f, -6f)),
            new Step("weasley_clock", 10, p -> {}),
            new Step("", 40, p -> {
                p.level().setDayTime(18000);
                look(p, 0.5, 1, 0.5, 180f, -32f);
            }),
            new Step("dark_mark", 10, p -> {}));

    private static BlockPos base = BlockPos.ZERO;
    private static boolean opened;
    private static int stage;
    private static int wait;
    private static int step;
    private static boolean captureThisFrame;
    private static boolean markLit;
    /** Set by a server step, applied on the client tick: the in-hand shots need the hand and hotbar drawn. */
    private static volatile boolean showGui;

    private LookPreviewCapture() {}

    private static void stage(ServerPlayer p) {
        ServerLevel level = p.level();
        // Out in the open: the save has a building at its spawn.
        base = p.blockPosition().offset(40, 0, 40);
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        for (int x = -4; x <= 32; x++) {
            for (int z = -6; z <= 4; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            }
        }
        quidditch(level);
        // Norbert's egg in Hagrid's fire.
        level.setBlockAndUpdate(base.offset(8, 0, 0), Blocks.CAMPFIRE.defaultBlockState());
        display(level, base.offset(8, 0, 0), 0.95, "norwegian_ridgeback_egg", 1.0f);
        // The post owls on perches: snowy (Hedwig), great grey (Errol), tawny and barn (the post office).
        String[] coats = {"base", "great_grey", "tawny", "barn"};
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "owl"));
        for (int i = 0; i < coats.length; i++) {
            BlockPos perch = base.offset(14 + i * 2, 0, 0);
            level.setBlockAndUpdate(perch, Blocks.SPRUCE_FENCE.defaultBlockState());
            var owl = type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (owl instanceof net.minecraft.world.entity.Mob mob) {
                mob.snapTo(perch.getX() + 0.5, perch.getY() + 1.0, perch.getZ() + 0.5, 180f, 0f);
                mob.setYHeadRot(180f);
                mob.setNoAi(true);
                if (!coats[i].equals("base") && mob instanceof at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity beast) {
                    beast.applyVariant(coats[i]);
                }
                level.addFreshEntity(mob);
            }
        }
        // The Weasley clock on a kitchen wall.
        for (int x = 25; x <= 32; x++) {
            for (int y = 0; y <= 5; y++) {
                level.setBlockAndUpdate(base.offset(x, y, 3), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
            }
        }
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack()
                .withSuppressedOutput().withLevel(level), String.format(java.util.Locale.ROOT,
                "summon minecraft:painting %d %d %d {facing:2b,variant:\"wizards_and_beasts:weasley_clock\"}",
                base.getX() + 28, base.getY() + 2, base.getZ() + 2));
        p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        markLit = true;
    }

    /** West of the other scenes, so none of them stands in the background. */
    private static final double QX = -60;
    private static final String[] BALLS = {"quaffle", "bludger", "golden_snitch"};
    private static final String[] VIEWS = {"front", "side", "top", "bottom"};
    /** Room per view (the Snitch's wings span two blocks) and the camera's distance back. */
    private static final double[] BALL_SPACING = {1.2, 1.2, 1.8};
    private static final double[] VIEW_BACK = {0.9, 0.8, 1.0};
    private static final double VIEW_Y = 1.6;
    /** Beyond the hoops and far east of the hero shot, so neither shows behind the other. */
    private static final double VIEW_Z = 30;

    private static double viewX(int ball, int view) {
        double start = QX + 60 + 16 * ball;
        return start + view * BALL_SPACING[ball];
    }

    /**
     * The Quidditch balls in vanilla style (owner, 2026-11-25): each one element at model scale 1 (Quaffle 6 px,
     * Bludger 5, Snitch 2 with plane wings) over a pitch with goal hoops, then each ball turned to show its front,
     * side, top and bottom. Every display has a fixed daylight brightness.
     */
    private static void quidditch(ServerLevel level) {
        for (int x = -12; x <= 112; x++) {
            for (int z = -8; z <= 36; z++) {
                level.setBlockAndUpdate(base.offset((int) QX + x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        // Three goal hoops behind the hero shot: tall posts and thin gold rings, the middle one tallest.
        int[][] hoops = {{-6, 9}, {2, 12}, {10, 9}};
        for (int[] hoop : hoops) {
            BlockPos foot = base.offset((int) QX + hoop[0], 0, 24);
            for (int y = 0; y < hoop[1]; y++) {
                level.setBlockAndUpdate(foot.above(y), Blocks.OAK_FENCE.defaultBlockState());
            }
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = -4; dy <= 4; dy++) {
                    double r = Math.sqrt(dx * dx + dy * dy);
                    if (r > 2.5 && r < 3.5) {
                        level.setBlockAndUpdate(foot.offset(dx, hoop[1] + 3 + dy, 0), Blocks.GOLD_BLOCK.defaultBlockState());
                    }
                }
            }
        }
        // The hero shot: the balls in a row in the air, Quaffle on the left (+x is the left facing +z).
        ball(level, QX + 2.8, 1.85, 0.5, "quaffle", 1.0, 8, NONE, 0);
        ball(level, QX + 1.9, 1.85, 0.5, "bludger", 1.0, 8, NONE, 0);
        ball(level, QX + 0.9, 2.05, 0.5, "golden_snitch", 1.0, 8, NONE, 0);
        // Four views of each: as made, turned to show its east side, its top, its bottom.
        for (int b = 0; b < BALLS.length; b++) {
            for (int v = 0; v < VIEWS.length; v++) {
                ball(level, viewX(b, v), VIEW_Y, VIEW_Z, BALLS[b], 1.0, 8, VIEW_TURN[v], v);
            }
        }
    }

    private static final float S45 = (float) Math.sqrt(0.5);
    private static final float[] NONE = {0f, 0f, 0f, 1f};
    /** front, side (east face to the camera), top (top to the camera), bottom. */
    private static final float[][] VIEW_TURN = {NONE, {0f, S45, 0f, S45}, {-S45, 0f, 0f, S45}, {S45, 0f, 0f, S45}};

    /**
     * A ball whose centre lands at (x, centreY, z). A geo item's origin sits at the item's centre and the ball's
     * centre {@code centreUnits} above it; the display is turned by quaternion {@code q} about its origin, so the
     * translation moves the turned centre back onto the point (view 0 front/1 side keep it above, 2 top swings it
     * toward -z, 3 bottom toward +z).
     */
    private static void ball(ServerLevel level, double x, double centreY, double z, String item, double scale,
                             int centreUnits, float[] q, int view) {
        double c = centreUnits / 16.0 * scale;
        double tx = 0;
        double ty = view <= 1 ? -c : 0;
        double tz = view == 2 ? c : view == 3 ? -c : 0;
        command(level, String.format(java.util.Locale.ROOT,
                "summon minecraft:item_display %.3f %.3f %.3f {item:{id:\"%s:%s\",count:1},item_display:\"none\","
                        + "brightness:{sky:15,block:0},"
                        + "transformation:{left_rotation:[%.4ff,%.4ff,%.4ff,%.4ff],right_rotation:[0f,0f,0f,1f],"
                        + "translation:[%.3ff,%.3ff,%.3ff],scale:[%.3ff,%.3ff,%.3ff]}}",
                base.getX() + x, base.getY() + centreY, base.getZ() + z, WizardsAndBeastsMod.MODID, item,
                q[0], q[1], q[2], q[3], tx, ty, tz, scale, scale, scale));
    }

    private static void command(ServerLevel level, String cmd) {
        level.getServer().getCommands().performPrefixedCommand(
                level.getServer().createCommandSourceStack().withSuppressedOutput().withLevel(level), cmd);
    }

    /** Selects a hotbar slot on the server and tells the client, which owns the selected slot. */
    private static void hold(ServerPlayer p, int slot) {
        p.getInventory().setSelectedSlot(slot);
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(slot));
    }

    /** Places the eye at (ex, ey, ez) looking at (tx, ty, tz), all base-relative. */
    private static void eyeAt(ServerPlayer p, double ex, double ey, double ez, double tx, double ty, double tz) {
        double dx = tx - ex, dy = ty - ey, dz = tz - ez;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        eye(p, ex, ey, ez, yaw, pitch);
    }

    /** Places the spectator so the eye, 1.62 above the feet, is at these base-relative coordinates. */
    private static void eye(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        look(p, x, y - 1.62, z, yaw, pitch);
    }

    private static void display(ServerLevel level, BlockPos at, double up, String item, float scale) {
        String cmd = String.format(java.util.Locale.ROOT,
                "summon minecraft:item_display %.2f %.2f %.2f {item:{id:\"%s:%s\",count:1},item_display:\"none\","
                        + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
                        + "translation:[0f,0f,0f],scale:[%.2ff,%.2ff,%.2ff]}}",
                at.getX() + 0.5, at.getY() + up, at.getZ() + 0.5, WizardsAndBeastsMod.MODID, item, scale, scale, scale);
        level.getServer().getCommands().performPrefixedCommand(
                level.getServer().createCommandSourceStack().withSuppressedOutput().withLevel(level), cmd);
    }

    private static void look(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.teleportTo(p.level(), base.getX() + x, base.getY() + y, base.getZ() + z, Set.of(), yaw, pitch, false);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ENABLED || opened || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        opened = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.hideGui = true;
        GLFW.glfwSetWindowSize(mc.getWindow().handle(), 1280, 720);
        stage = 1;
        wait = 80;
        mc.createWorldOpenFlows().openWorld(
                System.getenv().getOrDefault("WB_LOOK_PREVIEW_WORLD", "wb_entity_showcase"), () -> stage = 3);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED || stage == 0 || stage == 3) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null) {
            return;
        }
        if (mc.options.hideGui == showGui) {
            mc.options.hideGui = !showGui;
        }
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
        if (markLit && mc.player.tickCount % 5 == 0) {
            server.execute(() -> {
                ServerLevel level = server.overworld();
                at.koopro.wizardsandbeasts.spell.darkmark.DarkMark.draw(level,
                        Vec3.atCenterOf(base).add(0, 44, -56), 1.6, 0f);
            });
        }
        if (captureThisFrame || --wait > 0) {
            return;
        }
        if (stage == 1) {
            stage = 2;
            step = 0;
            begin(mc, server);
            return;
        }
        if (!SCRIPT.get(step).shot().isEmpty()) {
            captureThisFrame = true;
        } else {
            advance(mc, server);
        }
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        if (!captureThisFrame) {
            return;
        }
        captureThisFrame = false;
        Minecraft mc = Minecraft.getInstance();
        Path file = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "look_preview"))
                .resolve(SCRIPT.get(step).shot() + ".png");
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Look preview: cannot write {}", file, e);
            }
        }));
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            advance(mc, server);
        }
    }

    private static void advance(Minecraft mc, MinecraftServer server) {
        step++;
        if (step >= SCRIPT.size()) {
            stage = 3;
            LOGGER.info("Look preview: done");
            mc.stop();
            return;
        }
        begin(mc, server);
    }

    private static void begin(Minecraft mc, MinecraftServer server) {
        Step current = SCRIPT.get(step);
        wait = current.ticks();
        if (!current.shot().isEmpty()) {
            return;
        }
        mc.setScreen(null);
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                current.server().accept(player);
            }
        });
    }

    /** Last, so every static it reads is already set (static fields initialise in textual order). */
    private static final List<Step> SCRIPT = script();
}
