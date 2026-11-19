package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.atmosphere.PlaceSense;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
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
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Photographs the atmosphere (client/atmosphere) with the layer OFF and then FULL from the same spot, by day and by
 * night, for the owner's side-by-side: the world-deco library showcase room built high over the spawn, then an
 * old-growth forest found by biome search.
 *
 * <p>Development only, inert unless {@code WB_ATMOS_SHOWCASE} names a set ({@code a1} = library + forest).
 * A fresh normal world is generated ({@code WB_ATMOS_SHOWCASE_SEED}, default the playtest seed); shots and a readout of every place's
 * score and strength at each shot go to {@code screenshots/atmosphere/<set>}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class AtmosphereShowcaseCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SET = FMLEnvironment.isProduction() ? null : System.getenv("WB_ATMOS_SHOWCASE");
    private static final Identifier LIBRARY = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
            "showcase/library/a");
    private static final ResourceKey<Biome> FOREST = ResourceKey.create(Registries.BIOME,
            Identifier.withDefaultNamespace("dark_forest"));

    /** A step: what the server does (or a shot to take, or a client config change), then ticks to wait. */
    private record Step(String shot, int ticks, Consumer<ServerPlayer> server, Runnable client) {
        static Step server(int ticks, Consumer<ServerPlayer> action) {
            return new Step("", ticks, action, () -> {});
        }

        static Step client(int ticks, Runnable action) {
            return new Step("", ticks, p -> {}, action);
        }

        static Step shot(String name) {
            return new Step(name, 4, p -> {}, () -> {});
        }
    }

    private static List<Step> script = List.of();
    private static boolean opened;
    private static int stage; // 0 idle, 1 loading, 2 running, 3 done
    private static int wait;
    private static int step;
    private static boolean captureThisFrame;
    private static BlockPos room = BlockPos.ZERO;
    private static BlockPos forest = BlockPos.ZERO;

    private AtmosphereShowcaseCapture() {}

    private static List<Step> a1() {
        List<Step> s = new ArrayList<>();
        s.add(Step.server(80, AtmosphereShowcaseCapture::setUp));
        // Library: camera in the room's middle, looking along the shelves.
        s.add(Step.server(60, p -> at(p, room.offset(8, 2, 3), 0f, 8f)));
        compare(s, "library_day", 6000);
        compare(s, "library_night", 18000);
        // Forest: found by biome search, stood on the floor of it.
        s.add(Step.server(200, AtmosphereShowcaseCapture::findForest));
        s.add(Step.server(200, AtmosphereShowcaseCapture::standInForest));
        compare(s, "forest_night", 18000);
        compare(s, "forest_day", 6000);
        compare(s, "forest_dusk", 13000);
        return s;
    }

    /** The same view with the layer OFF, then FULL after it has had time to recognise the place and fade in. */
    private static void compare(List<Step> s, String name, long time) {
        s.add(Step.server(10, p -> command(p, "time set " + time)));
        s.add(Step.client(160, () -> Config.atmosphere = Config.Atmosphere.OFF));
        s.add(Step.shot(name + "_off"));
        s.add(Step.client(260, () -> Config.atmosphere = Config.Atmosphere.FULL));
        s.add(Step.shot(name + "_on"));
        s.add(Step.client(30, () -> {}));
        s.add(Step.shot(name + "_on2"));
    }

    private static void setUp(ServerPlayer p) {
        // A new character is met by the heritage screen; choose one so it does not cover every shot.
        if (!at.koopro.wizardsandbeasts.heritage.HeritageAPI.hasHeritageSelected(p)) {
            at.koopro.wizardsandbeasts.heritage.HeritageAPI.commit(p, at.koopro.wizardsandbeasts.heritage.Heritage.WIZARDKIND,
                    at.koopro.wizardsandbeasts.heritage.HeritageVariant.HALF_BLOOD);
        }
        ServerLevel level = p.level();
        var rules = level.getGameRules();
        rules.set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, false, level.getServer());
        rules.set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_WEATHER, false, level.getServer());
        rules.set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS, false, level.getServer());
        command(p, "weather clear");
        command(p, "gamemode spectator " + p.getGameProfile().name());
        room = new BlockPos(p.getBlockX() - 8, 200, p.getBlockZ() - 9);
        level.getStructureManager().get(LIBRARY).ifPresentOrElse(t -> t.placeInWorld(level, room, room,
                        new StructurePlaceSettings(), level.random, 2),
                () -> LOGGER.error("Atmosphere showcase: no template {}", LIBRARY));
    }

    private static void findForest(ServerPlayer p) {
        ServerLevel level = p.level();
        BlockPos interior = forestInterior(level, p.blockPosition());
        if (interior == null) {
            LOGGER.error("Atmosphere showcase: no dark forest interior within 3000 blocks");
            return;
        }
        forest = surfaceForest(level, interior);
        at(p, new BlockPos(forest.getX(), 180, forest.getZ()), 0f, 0f);
    }

    /**
     * The nearest point, on a 32-block grid, whose biome near the surface is dark forest and whose four neighbours a
     * grid step away are too: the inside of a forest, not a sliver of one by the sea. Read from biome noise, so
     * nothing is generated until the spot is chosen.
     */
    private static BlockPos forestInterior(ServerLevel level, BlockPos origin) {
        int qy = (level.getSeaLevel() + 4) >> 2;
        for (int r = 0; r <= 3000; r += 32) {
            for (int dx = -r; dx <= r; dx += 32) {
                for (int dz = -r; dz <= r; dz += 32) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = origin.getX() + dx, z = origin.getZ() + dz;
                    if (forestAt(level, x, qy, z) && forestAt(level, x + 32, qy, z) && forestAt(level, x - 32, qy, z)
                            && forestAt(level, x, qy, z + 32) && forestAt(level, x, qy, z - 32)) {
                        return new BlockPos(x, 0, z);
                    }
                }
            }
        }
        return null;
    }

    private static boolean forestAt(ServerLevel level, int x, int qy, int z) {
        return level.getUncachedNoiseBiome(x >> 2, qy, z >> 2).is(FOREST);
    }

    /**
     * A column near the search hit whose <em>surface</em> is the forest and that has a trunk within a few blocks: the
     * 3D search can land on a biome sample that is not what the ground shows (it once stood the camera in the sea).
     */
    private static BlockPos surfaceForest(ServerLevel level, BlockPos hit) {
        for (int r = 0; r <= 96; r += 8) {
            for (int dx = -r; dx <= r; dx += 8) {
                for (int dz = -r; dz <= r; dz += 8) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = hit.getX() + dx, z = hit.getZ() + dz;
                    level.getChunk(x >> 4, z >> 4);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos ground = new BlockPos(x, y, z);
                    if (!level.getBiome(ground).is(FOREST) || !level.getBiome(ground.above(2)).is(FOREST)
                            || !level.getFluidState(ground.below()).isEmpty()) {
                        continue;
                    }
                    int trunks = 0;
                    for (BlockPos q : BlockPos.betweenClosed(ground.offset(-4, 0, -4), ground.offset(4, 3, 4))) {
                        if (level.getBlockState(q).is(net.minecraft.tags.BlockTags.LOGS)) {
                            trunks++;
                        }
                    }
                    if (trunks >= 6) {
                        return ground;
                    }
                }
            }
        }
        LOGGER.warn("Atmosphere showcase: no wooded forest floor near {}", hit);
        return hit;
    }

    private static void standInForest(ServerPlayer p) {
        ServerLevel level = p.level();
        level.getChunk(forest.getX() >> 4, forest.getZ() >> 4); // generated now if it is not yet
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, forest.getX(), forest.getZ());
        BlockPos feet = new BlockPos(forest.getX(), y, forest.getZ());
        LOGGER.info("Atmosphere showcase: forest stand {} in {}", feet,
                level.getBiome(feet).unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
        at(p, feet, 30f, -6f);
    }

    private static void at(ServerPlayer p, BlockPos pos, float yaw, float pitch) {
        p.teleportTo(p.level(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), yaw, pitch, false);
    }

    private static void command(ServerPlayer p, String command) {
        MinecraftServer server = p.level().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (SET == null || opened || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        opened = true;
        script = a1();
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.hideGui = true;
        GLFW.glfwSetWindowSize(mc.getWindow().handle(), 1280, 720);
        stage = 1;
        wait = 200;
        // A fresh, normally generated world: no old save's prompts, terrain as a player gets it.
        String name = "wb_atmos_" + Long.toHexString(System.currentTimeMillis());
        long seed = Long.parseLong(System.getenv().getOrDefault("WB_ATMOS_SHOWCASE_SEED", "271828182"));
        var rules = new net.minecraft.world.level.gamerules.GameRules(
                net.minecraft.world.level.WorldDataConfiguration.DEFAULT.enabledFeatures());
        LOGGER.info("Atmosphere showcase: creating {}", name);
        mc.createWorldOpenFlows().createFreshLevel(name, new net.minecraft.world.level.LevelSettings(name,
                        net.minecraft.world.level.GameType.CREATIVE, false, net.minecraft.world.Difficulty.PEACEFUL,
                        true, rules, net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                new net.minecraft.world.level.levelgen.WorldOptions(seed, true, false),
                net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions, new TitleScreen());
    }

    /** Every screen that opens, so a stall on a prompt shows in the log. */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (SET != null && event.getNewScreen() != null) {
            LOGGER.info("Atmosphere showcase: screen {}", event.getNewScreen().getClass().getName());
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (SET == null || stage == 0 || stage == 3) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null) {
            return;
        }
        // Pause, the heritage gate, anything a fresh world opens: the shots want the world alone.
        if (mc.screen != null) {
            mc.setScreen(null);
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
        if (!script.get(step).shot().isEmpty()) {
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
        String name = script.get(step).shot();
        Path dir = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "atmosphere", SET));
        Path file = dir.resolve(name + ".png");
        readout(dir, name);
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Atmosphere showcase: cannot write {}", file, e);
            }
        }));
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            advance(mc, server);
        }
    }

    /** One line per place at this shot: what the layer thought of the spot. */
    private static void readout(Path dir, String shot) {
        StringBuilder line = new StringBuilder(shot).append(" atmosphere=").append(Config.atmosphere);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.getCameraEntity() != null) {
            BlockPos eye = BlockPos.containing(mc.getCameraEntity().getEyePosition());
            line.append(" eye=").append(eye.toShortString()).append(" biome=").append(mc.level.getBiome(eye).unwrapKey()
                    .map(k -> k.identifier().toString()).orElse("?"));
        }
        for (PlaceSense.Place place : PlaceSense.places()) {
            line.append(String.format(Locale.ROOT, " | %s score=%.3f on=%s strength=%.2f anchors=%d gated=%s inBiome=%s",
                    place.id().getPath(), place.score(), place.active(), place.strength(), place.anchorCount(),
                    place.gated(), place.inBiome()));
        }
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("readout.txt"), line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("Atmosphere showcase: cannot write readout", e);
        }
    }

    private static void advance(Minecraft mc, MinecraftServer server) {
        step++;
        if (step >= script.size()) {
            stage = 3;
            LOGGER.info("Atmosphere showcase: done");
            mc.stop();
            return;
        }
        begin(mc, server);
    }

    private static void begin(Minecraft mc, MinecraftServer server) {
        Step current = script.get(step);
        wait = current.ticks();
        if (!current.shot().isEmpty()) {
            return;
        }
        current.client().run();
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                current.server().accept(player);
            }
        });
    }
}
