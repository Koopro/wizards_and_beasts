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

    private static final List<Step> SCRIPT = List.of(
            new Step("", 100, LookPreviewCapture::stage),
            new Step("", 40, p -> look(p, 2.5, 0.6, -3.0, 0f, 10f)),
            new Step("quidditch_balls", 10, p -> {}),
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
        // The balls at the size they are, beside a Seeker in Quidditch robes for scale: Quaffle, Snitch, Bludger.
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack()
                .withSuppressedOutput().withLevel(level), String.format(java.util.Locale.ROOT,
                "summon minecraft:armor_stand %.1f %d %.1f {Rotation:[180f,0f],ShowArms:1b,"
                        + "equipment:{chest:{id:\"wizards_and_beasts:quidditch_robes\",count:1},"
                        + "mainhand:{id:\"wizards_and_beasts:beaters_bat\",count:1}}}",
                base.getX() + 4.5, base.getY(), base.getZ() + 0.5));
        display(level, base.offset(0, 0, 0), 1.25, "quaffle", 1.0f);
        display(level, base.offset(1, 0, 0), 1.6, "golden_snitch", 1.0f);
        display(level, base.offset(2, 0, 0), 1.2, "bludger", 1.0f);
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
}
