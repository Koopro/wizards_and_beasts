package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.block.floo.FlooGrateBlock;
import at.koopro.wizardsandbeasts.block.floo.FlooGrateBlockEntity;
import at.koopro.wizardsandbeasts.block.floo.HearthFire;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Photographs the Burrow hearth for the Floo remake's P1 look gate (2026-11-26): a 3 x 3 walk-in opening in a 5-wide
 * sooty stone chimney breast with a spruce mantel beam, the iron grate cold, laid with logs, burning an ordinary fire,
 * then roaring emerald (rising, then full, with a villager standing in it for scale), the powder pot on the mantel
 * shelf, and the roar at night.
 *
 * <p>Development only, inert unless {@code WB_FLOO_LOOK=1}; {@code WB_FLOO_LOOK_WORLD} names the save. Shots go to
 * {@code screenshots/floo_look}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class FlooLookCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = "1".equals(System.getenv("WB_FLOO_LOOK")) && !FMLEnvironment.isProduction();

    private record Step(String shot, int ticks, Consumer<ServerPlayer> server) {}

    private static final List<Step> SCRIPT = new ArrayList<>();
    private static BlockPos grate = BlockPos.ZERO;
    private static boolean opened;
    private static int stage;
    private static int wait;
    private static int step;
    private static boolean captureThisFrame;

    private FlooLookCapture() {}

    private static void script() {
        SCRIPT.add(new Step("", 120, FlooLookCapture::stageRoom));
        SCRIPT.add(new Step("", 40, p -> look(p, 0.5, 0.4, 7.0, 180f, 4f)));
        SCRIPT.add(new Step("1_cold_front", 10, p -> {}));
        SCRIPT.add(new Step("", 30, p -> {
            fire(p, true, HearthFire.NONE);
            look(p, 0.5, 0.6, 2.6, 180f, 28f);
        }));
        SCRIPT.add(new Step("2_logs_close", 10, p -> {}));
        SCRIPT.add(new Step("", 40, p -> fire(p, true, HearthFire.WARM)));
        SCRIPT.add(new Step("3_warm_close", 10, p -> {}));
        SCRIPT.add(new Step("", 30, p -> look(p, 0.5, 0.4, 7.0, 180f, 4f)));
        SCRIPT.add(new Step("4_warm_front", 10, p -> {}));
        // The roar: photographed while it climbs, then at full height.
        SCRIPT.add(new Step("", 3, p -> fire(p, true, HearthFire.FLOO)));
        SCRIPT.add(new Step("5_roar_rising", 1, p -> {}));
        SCRIPT.add(new Step("", 40, p -> {}));
        SCRIPT.add(new Step("6_roar_front", 10, p -> {}));
        SCRIPT.add(new Step("", 30, FlooLookCapture::traveller));
        SCRIPT.add(new Step("7_traveller_front", 10, p -> {}));
        SCRIPT.add(new Step("", 30, p -> look(p, -3.6, 1.4, 4.6, 222f, 12f)));
        SCRIPT.add(new Step("8_traveller_angle", 10, p -> {}));
        SCRIPT.add(new Step("", 30, p -> look(p, -1.0, 3.9, 3.2, 200f, 32f)));
        SCRIPT.add(new Step("9_mantel_pot", 10, p -> {}));
        SCRIPT.add(new Step("", 60, p -> {
            p.level().setDayTime(18000);
            look(p, 0.5, 0.4, 7.0, 180f, 4f);
        }));
        SCRIPT.add(new Step("10_roar_night", 10, p -> {}));
        SCRIPT.add(new Step("", 40, p -> fire(p, true, HearthFire.WARM)));
        SCRIPT.add(new Step("11_warm_night", 10, p -> {}));
    }

    private static void fire(ServerPlayer p, boolean fuel, HearthFire fire) {
        ServerLevel level = p.level();
        BlockState state = level.getBlockState(grate);
        if (!state.is(ModBlocks.FLOO_GRATE.get())) {
            LOGGER.error("Floo look: no grate at {}", grate);
            return;
        }
        level.setBlock(grate, state.setValue(FlooGrateBlock.FUEL, fuel).setValue(FlooGrateBlock.FIRE, fire), 3);
        if (level.getBlockEntity(grate) instanceof FlooGrateBlockEntity be) {
            // The staged room is built to the cottage pattern: if this says false the pattern and the build disagree.
            LOGGER.info("Floo look: hearth formed={} style={} opening={}x{}", be.recheck(level, level.getBlockState(grate)),
                    be.style().orElse(null), be.openingWidth(), be.openingHeight());
            if (fire == HearthFire.FLOO) {
                be.roar(level.getGameTime());
            }
        }
    }

    /** A villager standing on the grate in the green fire: a grown traveller for scale (about 1.95 blocks). */
    private static void traveller(ServerPlayer p) {
        ServerLevel level = p.level();
        Entity villager = EntityType.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        if (villager == null) {
            return;
        }
        villager.snapTo(grate.getX() + 0.5, grate.getY() + 0.25, grate.getZ() + 0.5, 0.0F, 0.0F);
        villager.setYHeadRot(0.0F);
        if (villager instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setYBodyRot(0.0F);
        }
        villager.setSilent(true);
        villager.setInvulnerable(true);
        level.addFreshEntity(villager);
    }

    /**
     * A Burrow-like kitchen wall with the hearth set in it. The grate sits at the origin; the hearth faces south, into
     * the room. Recess (the walk-in opening) 3 wide x 3 high x 1 deep; chimney breast 5 wide, standing one block proud
     * of the wall; sooty blackstone at the back of the firebox; spruce mantel beam over the opening, a shelf in front
     * of it with the powder pot and candles; smooth stone hearth apron.
     */
    private static void stageRoom(ServerPlayer p) {
        ServerLevel level = p.level();
        grate = p.blockPosition().offset(300, 0, 0);
        BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -8; x <= 8; x++) {
            for (int z = -3; z <= 11; z++) {
                for (int y = -2; y <= 8; y++) {
                    c.set(grate.getX() + x, grate.getY() + y, grate.getZ() + z);
                    BlockState s = air;
                    if (y < 0) {
                        s = Blocks.SPRUCE_PLANKS.defaultBlockState();
                    } else if (y == 6) {
                        s = Blocks.DARK_OAK_PLANKS.defaultBlockState();
                    } else if (y < 6 && (z == -1 || Math.abs(x) == 7 || z == 10)) {
                        boolean post = Math.abs(x) % 4 == 3 && z == -1;
                        boolean window = z == 10 && (y == 2 || y == 3) && Math.abs(x) % 4 == 1;
                        s = window ? Blocks.GLASS_PANE.defaultBlockState()
                                : post ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState()
                                : Blocks.SPRUCE_PLANKS.defaultBlockState();
                    }
                    if (y < 6 && Math.abs(x) <= 7 && z > -1 && z < 10 && Math.abs(x) == 7) {
                        s = Blocks.SPRUCE_PLANKS.defaultBlockState();
                    }
                    if (Math.abs(x) > 7 || z < -1 || z > 10) {
                        s = y < 0 || y == 6 ? s : Blocks.SPRUCE_PLANKS.defaultBlockState();
                    }
                    level.setBlock(c, s, 2);
                }
            }
        }
        // The chimney breast: back wall of the firebox (z -1) and the front (z 0), five wide, floor to ceiling.
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y < 6; y++) {
                boolean firebox = Math.abs(x) <= 1 && y <= 2;
                put(level, x, y, -1, firebox ? soot(x, y) : stone(x, y));
                if (Math.abs(x) <= 1 && y <= 2) {
                    continue; // the opening
                }
                if (y == 3) {
                    put(level, x, y, 0, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState()
                            .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
                } else {
                    put(level, x, y, 0, stone(x, y));
                }
            }
        }
        // Firebox floor and the hearth apron in front.
        for (int x = -1; x <= 1; x++) {
            put(level, x, -1, 0, Blocks.STONE_BRICKS.defaultBlockState());
        }
        for (int x = -2; x <= 2; x++) {
            put(level, x, -1, 1, Blocks.SMOOTH_STONE.defaultBlockState());
        }
        // Mantel shelf in front of the beam, the powder pot and two candles on it.
        for (int x = -3; x <= 3; x++) {
            put(level, x, 3, 1, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        }
        put(level, -2, 4, 1, ModBlocks.FLOO_POWDER_POT.get().defaultBlockState());
        put(level, 1, 4, 1, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 2).setValue(CandleBlock.LIT, true));
        put(level, 3, 4, 1, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1).setValue(CandleBlock.LIT, true));
        // Kitchen life either side: a barrel and a log basket.
        put(level, 4, 0, 0, Blocks.BARREL.defaultBlockState());
        put(level, -4, 0, 0, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        put(level, -4, 1, 0, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        // Two lanterns hanging from the ceiling beams, so the room reads in the cold shots too.
        for (int x : new int[]{-4, 4}) {
            put(level, x, 5, 4, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        }
        // The grate, facing the room.
        put(level, 0, 0, 0, ModBlocks.FLOO_GRATE.get().defaultBlockState().setValue(FlooGrateBlock.FACING, Direction.SOUTH));

        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        p.setGameMode(GameType.CREATIVE);
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        p.getInventory().clearContent();
        LOGGER.info("Floo look: grate at {}", grate);
    }

    /** Rough cottage stone: cobble with a few stone bricks, deterministic so every run looks the same. */
    private static BlockState stone(int x, int y) {
        int h = Math.floorMod(x * 7 + y * 13, 5);
        return (h == 0 ? Blocks.STONE_BRICKS : h == 3 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState();
    }

    /** The soot-blackened back of the firebox. */
    private static BlockState soot(int x, int y) {
        return (Math.floorMod(x + y, 3) == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS : Blocks.POLISHED_BLACKSTONE_BRICKS)
                .defaultBlockState();
    }

    private static void put(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(grate.offset(x, y, z), state, 3);
    }

    private static void look(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.teleportTo(p.level(), grate.getX() + x, grate.getY() + y, grate.getZ() + z, Set.of(), yaw, pitch, false);
    }

    // ── driving (as BenchLookCapture) ───────────────────────────────────────

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ENABLED || opened || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        opened = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        GLFW.glfwSetWindowSize(mc.getWindow().handle(), 1280, 720);
        stage = 1;
        wait = 80;
        mc.createWorldOpenFlows().openWorld(
                System.getenv().getOrDefault("WB_FLOO_LOOK_WORLD", "wb_entity_showcase"), () -> stage = 3);
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
        if (captureThisFrame || --wait > 0) {
            return;
        }
        if (stage == 1) {
            stage = 2;
            step = 0;
            script();
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
        Path file = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "floo_look"))
                .resolve(SCRIPT.get(step).shot() + ".png");
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Floo look: cannot write {}", file, e);
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
            LOGGER.info("Floo look: done");
            mc.stop();
            return;
        }
        begin(mc, server);
    }

    private static void begin(Minecraft mc, MinecraftServer server) {
        Step current = SCRIPT.get(step);
        wait = current.ticks();
        mc.options.hideGui = true;
        if (!current.shot().isEmpty()) {
            return;
        }
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                current.server().accept(player);
            }
        });
    }
}
