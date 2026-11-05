package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.block.trunk.TrunkBlock;
import at.koopro.wizardsandbeasts.block.trunk.TrunkBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Photographs a row of placed blocks beside a vanilla chest, so a block's model is judged in the world at the size a
 * player meets it: head-on, from three-quarters, from above, close up -- and, for blocks with animations, mid-clip.
 *
 * <p>Development only, inert unless {@code WB_BLOCK_SHOWCASE} names a set ({@link #SETS}). {@code
 * WB_BLOCK_SHOWCASE_WORLD} names a disposable save (the stage is built into it); shots go to {@code
 * screenshots/block_showcase/<set>}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class BlockShowcaseCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SET = FMLEnvironment.isProduction() ? null : System.getenv("WB_BLOCK_SHOWCASE");

    /** Block ids per set, placed left to right facing the camera. */
    private static final Map<String, List<String>> SETS = Map.of(
            "trunks", List.of("enchanted_trunk", "expanded_trunk", "masters_trunk", "moodys_trunk", "newts_case_item"),
            "stations", List.of("floo_fireplace", "vanishing_cabinet", "settlement_post", "broom_rack",
                    "pocket_configurator"),
            "cabinet", List.of("vanishing_cabinet"));

    private record Step(String shot, int ticks, Consumer<ServerPlayer> server) {}

    private static List<Step> SCRIPT = List.of();

    private static final List<Step> SET_SCRIPT = List.of(
            new Step("", 60, BlockShowcaseCapture::stage),
            new Step("front", 20, p -> {}),
            new Step("", 20, p -> look(p, 4.5, -1.5, 135f, 22f)),
            new Step("three_quarter", 20, p -> {}),
            new Step("", 20, p -> look(p, 0.5, -1.0, 180f, 48f)),
            new Step("above", 20, p -> {}),
            new Step("", 20, p -> look(p, -1.5, -3.6, 180f, 28f)),
            new Step("close", 20, p -> {}),
            // Mid-clip shots: the lid at the top of its swing, the dial halfway through its turn.
            new Step("", 20, p -> look(p, 0.5, 0.0, 180f, 20f)),
            new Step("", 9, p -> trigger(p, TrunkBlockEntity.CLIP_OPEN)),
            new Step("open", 1, p -> {}),
            new Step("", 40, p -> look(p, -0.5, -3.8, 180f, 42f)),
            new Step("", 10, p -> trigger(p, TrunkBlockEntity.CLIP_OPEN)),
            new Step("open_above", 1, p -> {}),
            new Step("", 40, p -> look(p, -1.5, -3.6, 180f, 28f)),
            new Step("", 4, p -> trigger(p, TrunkBlockEntity.CLIP_LOCK)),
            new Step("lock", 1, p -> {}));

    /**
     * {@code WB_BLOCK_SHOWCASE=gallery}: every block of this mod whose model is not a plain cube (by shape or by
     * render type), five to a row, front and three-quarter, so a block-model pass starts from pictures of all of
     * them. Wood and stone families (stairs, slabs, doors, fences ...), leaves and saplings are left out: their
     * shapes are vanilla's. Each batch's ids are written beside its shots as {@code bNN.txt}.
     */
    private static final int GALLERY_ROW = 5;
    private static final List<String> SHAPE_SUFFIXES = List.of("_stairs", "_slab", "_door", "_trapdoor", "_fence",
            "_fence_gate", "_wall", "_button", "_pressure_plate", "_sign", "_hanging_sign", "_wall_sign",
            "_wall_hanging_sign", "_leaves", "_sapling", "_log", "_wood", "_planks", "_pane", "_carpet");
    private static List<List<String>> gallery = List.of();

    private static List<List<String>> galleryBatches() {
        List<String> ids = new ArrayList<>();
        // WB_BLOCK_SHOWCASE_IDS=a,b,c photographs exactly those blocks instead of the non-cube sweep.
        String only = System.getenv("WB_BLOCK_SHOWCASE_IDS");
        if (only != null && !only.isBlank()) {
            ids.addAll(java.util.Arrays.stream(only.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList());
        }
        if (ids.isEmpty()) {
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (!WizardsAndBeastsMod.MODID.equals(id.getNamespace()) || block.asItem() == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            String path = id.getPath();
            if (path.startsWith("potted_") || SHAPE_SUFFIXES.stream().anyMatch(path::endsWith)) {
                continue;
            }
            BlockState state = block.defaultBlockState();
            boolean cube = Block.isShapeFullBlock(state.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
            if (!cube || state.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) {
                ids.add(path);
            }
        }
        java.util.Collections.sort(ids);
        }
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += GALLERY_ROW) {
            batches.add(ids.subList(i, Math.min(ids.size(), i + GALLERY_ROW)));
        }
        LOGGER.info("Block showcase: gallery of {} blocks in {} rows", ids.size(), batches.size());
        return batches;
    }

    private static List<Step> galleryScript() {
        gallery = galleryBatches();
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("", 60, p -> stage(p, List.of())));
        for (int i = 0; i < gallery.size(); i++) {
            List<String> batch = gallery.get(i);
            String tag = String.format("b%02d", i);
            steps.add(new Step("", 30, p -> {
                stage(p, batch);
                writeBatch(tag, batch);
                look(p, 0.5, -1.5, 180f, 20f);
            }));
            steps.add(new Step(tag + "_front", 20, p -> {}));
            steps.add(new Step("", 15, p -> look(p, 4.0, -3.0, 135f, 24f)));
            steps.add(new Step(tag + "_quarter", 20, p -> {}));
        }
        return steps;
    }

    private static void writeBatch(String tag, List<String> batch) {
        Path file = Minecraft.getInstance().gameDirectory.toPath()
                .resolve(Path.of(Screenshot.SCREENSHOT_DIR, "block_showcase", SET, tag + ".txt"));
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, String.join(System.lineSeparator(), batch));
        } catch (IOException e) {
            LOGGER.error("Block showcase: cannot write {}", file, e);
        }
    }

    private static BlockPos origin = BlockPos.ZERO;
    private static final List<BlockPos> placed = new ArrayList<>();

    private static boolean opened;
    private static int stage; // 0 idle, 1 loading, 2 running, 3 done
    private static int wait;
    private static int step;
    private static boolean captureThisFrame;

    private BlockShowcaseCapture() {}

    /** A stone floor and back wall, noon, the set's blocks in a row two apart, and a vanilla chest at the end. */
    private static void stage(ServerPlayer p) {
        stage(p, SETS.getOrDefault(SET, List.of()));
    }

    private static void stage(ServerPlayer p, List<String> ids) {
        ServerLevel level = p.level();
        if (placed.isEmpty() && origin.equals(BlockPos.ZERO)) {
            origin = p.blockPosition();
        }
        level.getEntities(p, p.getBoundingBox().inflate(16), e -> !(e instanceof net.minecraft.world.entity.player.Player))
                .forEach(net.minecraft.world.entity.Entity::discard);
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        for (int x = -8; x <= 8; x++) {
            for (int z = -10; z <= 3; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
                for (int y = 0; y <= 6; y++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
            for (int y = 0; y <= 5; y++) {
                level.setBlock(origin.offset(x, y, -9), Blocks.STONE_BRICKS.defaultBlockState(), 3);
            }
        }
        placed.clear();
        int x = -ids.size();
        for (String id : ids) {
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, id));
            BlockPos at = origin.offset(x, 0, -6);
            place(level, at, facing(block.defaultBlockState()));
            placed.add(at);
            x += 2;
        }
        level.setBlock(origin.offset(x, 0, -6),
                Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), 3);
        if (placed.isEmpty() && !ids.isEmpty()) {
            LOGGER.warn("Block showcase: nothing placed for {}", ids);
        }
        look(p, 0.5, 0.5, 180f, 18f);
    }

    /** Places one block as a player would see it working: both halves of a two-tall block, a Floo hearth lit. */
    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), 3);
            level.setBlock(pos.above(), state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
            return;
        }
        if (state.getBlock() instanceof at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlock) {
            level.setBlock(pos, state.setValue(at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlock.LIT, true), 3);
            at.koopro.wizardsandbeasts.block.floo.FlooFlamesBlock.light(level, pos, Direction.SOUTH);
            return;
        }
        level.setBlock(pos, state, 3);
    }

    private static BlockState facing(BlockState state) {
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH) : state;
    }

    /**
     * Plays each block's own one-shot: every other trunk (so open and closed stand side by side) the given clip; on an
     * "open" step the cabinet passes, the hearth roars and the configurator attunes.
     */
    private static void trigger(ServerPlayer p, String clip) {
        boolean open = TrunkBlockEntity.CLIP_OPEN.equals(clip);
        for (int i = 0; i < placed.size(); i++) {
            var be = p.level().getBlockEntity(placed.get(i));
            if (be instanceof TrunkBlockEntity trunk && trunk.getBlockState().getBlock() instanceof TrunkBlock) {
                if (i % 2 == 0) {
                    trunk.play(clip);
                }
            } else if (open && be instanceof at.koopro.wizardsandbeasts.artefact.cabinet.VanishingCabinetBlockEntity cabinet) {
                cabinet.play(at.koopro.wizardsandbeasts.artefact.cabinet.VanishingCabinetBlockEntity.CLIP_PASS);
            } else if (open && be instanceof at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlockEntity hearth) {
                hearth.play(at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlockEntity.CLIP_ROAR);
            } else if (open && be instanceof at.koopro.wizardsandbeasts.block.ExpansionFocusBlockEntity focus) {
                focus.play(at.koopro.wizardsandbeasts.block.ExpansionFocusBlockEntity.CLIP_ATTUNE);
            }
        }
    }

    private static void look(ServerPlayer p, double dx, double dz, float yaw, float pitch) {
        p.teleportTo(p.level(), origin.getX() + dx, origin.getY(), origin.getZ() + dz, Set.of(), yaw, pitch, false);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (SET == null || opened || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        opened = true;
        SCRIPT = "gallery".equals(SET) ? galleryScript() : SET_SCRIPT;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        GLFW.glfwSetWindowSize(mc.getWindow().handle(), 1280, 720);
        stage = 1;
        wait = 80;
        mc.createWorldOpenFlows().openWorld(
                System.getenv().getOrDefault("WB_BLOCK_SHOWCASE_WORLD", "wb_block_showcase"), () -> stage = 3);
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
        if (mc.screen instanceof PauseScreen) {
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
        if (!SCRIPT.get(step).shot().isEmpty()) {
            mc.getToastManager().clear();
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
        Path file = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "block_showcase", SET))
                .resolve(SCRIPT.get(step).shot() + ".png");
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Block showcase: cannot write {}", file, e);
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
            LOGGER.info("Block showcase: done");
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
