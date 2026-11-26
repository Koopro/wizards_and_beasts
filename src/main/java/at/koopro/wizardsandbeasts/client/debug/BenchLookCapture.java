package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.wand.BenchBeatClient;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.bench.BenchMaking;
import at.koopro.wizardsandbeasts.wand.bench.BenchTool;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlock;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlockEntity;
import at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu;
import at.koopro.wizardsandbeasts.wand.making.BenchCraft;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;
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
 * Photographs the remade wandmaker's bench (P4 look gate, 2026-11-26): a solo bench growing its side half, the tools
 * laid, the bench screen with its act row, and each act's beat strip mid-play, played by an autoplay that hits most
 * beats and misses one so both colours show. Ends on the first sparks and the finished wand in the screen.
 *
 * <p>Development only, inert unless {@code WB_BENCH_LOOK=1}; {@code WB_BENCH_LOOK_WORLD} names the save. Shots go to
 * {@code screenshots/bench_look}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class BenchLookCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = "1".equals(System.getenv("WB_BENCH_LOOK")) && !FMLEnvironment.isProduction();

    private record Step(String shot, int ticks, boolean gui, Consumer<ServerPlayer> server) {}

    private static final List<Step> SCRIPT = new ArrayList<>();
    private static BlockPos bench = BlockPos.ZERO;
    private static boolean opened;
    private static int stage;
    private static int wait;
    private static int step;
    private static boolean captureThisFrame;

    private BenchLookCapture() {}

    private static void script() {
        SCRIPT.add(new Step("", 120, false, BenchLookCapture::stageRoom));
        // The bench as placed: solo, then grown into its pair (the side half lies at facing.ccw: east for a south face).
        SCRIPT.add(new Step("", 40, false, p -> look(p, 0.5, 1.9, 3.6, 180f, 20f)));
        SCRIPT.add(new Step("bench_front", 10, false, p -> {}));
        SCRIPT.add(new Step("", 40, false, p -> look(p, -2.2, 2.4, 3.2, 215f, 28f)));
        SCRIPT.add(new Step("bench_angle", 10, false, p -> {}));
        SCRIPT.add(new Step("", 40, false, p -> look(p, 1.0, 3.6, 2.0, 180f, 60f)));
        SCRIPT.add(new Step("bench_above", 10, false, p -> {}));
        // The screen, at the bench, before the first act.
        SCRIPT.add(new Step("", 30, true, p -> {
            look(p, 0.5, 0.0, 2.4, 180f, 25f);
            openBench(p);
        }));
        SCRIPT.add(new Step("screen_turn", 10, true, p -> {}));
        act("turn", 100);
        SCRIPT.add(new Step("", 110, true, p -> {}));
        act("bore", 120);
        SCRIPT.add(new Step("", 90, true, p -> {}));
        act("marry", 90);
        SCRIPT.add(new Step("", 110, true, p -> {}));
        SCRIPT.add(new Step("", 20, true, BenchLookCapture::openBench));
        SCRIPT.add(new Step("screen_seal", 10, true, p -> {}));
        act("seal", 70);
        SCRIPT.add(new Step("", 60, true, p -> {}));
        SCRIPT.add(new Step("sparks", 4, true, p -> {}));
        SCRIPT.add(new Step("", 30, true, BenchLookCapture::openBench));
        SCRIPT.add(new Step("screen_done", 10, true, p -> {}));
    }

    /** Begins the next act and photographs its strip {@code at} ticks in. */
    private static void act(String name, int at) {
        SCRIPT.add(new Step("", at, true, p -> {
            if (p.level().getBlockEntity(bench) instanceof WandmakersBenchBlockEntity be) {
                BenchMaking.Status status = BenchMaking.begin(be, p);
                LOGGER.info("Bench look: {} began: {}", name, status);
            }
        }));
        SCRIPT.add(new Step("act_" + name, 2, true, p -> {}));
    }

    /** A plank floor and walls, a lantern, the bench facing south with its blank, a dragon core and every tool. */
    private static void stageRoom(ServerPlayer p) {
        ServerLevel level = p.level();
        bench = p.blockPosition().offset(200, 0, 0);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockState floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState wall = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
        for (int x = -6; x <= 6; x++) {
            for (int z = -4; z <= 8; z++) {
                for (int y = -2; y <= 6; y++) {
                    cursor.set(bench.getX() + x, bench.getY() + y, bench.getZ() + z);
                    BlockState s = y < 0 ? floor
                            : (z == -2 && y < 5) || (Math.abs(x) == 5 && z < 4 && y < 5) ? wall
                            : Blocks.AIR.defaultBlockState();
                    level.setBlock(cursor, s, 2);
                }
            }
        }
        BlockState solo = ModBlocks.WANDMAKERS_BENCH.get().defaultBlockState()
                .setValue(WandmakersBenchBlock.FACING, Direction.SOUTH);
        level.setBlock(bench, solo, 3);
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        // Creative and flying, so the camera can stand anywhere; a Wandmaker, so the one beat the autoplay misses is
        // forgiven and no risk roll can spoil the run.
        p.setGameMode(GameType.CREATIVE);
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        p.getInventory().clearContent();
        p.setData(at.koopro.wizardsandbeasts.registry.ModAttachments.OWL_DATA.get(),
                new at.koopro.wizardsandbeasts.owl.data.PlayerOWLData(java.util.Map.of(
                        at.koopro.wizardsandbeasts.owl.OWLSubject.CHARMS, at.koopro.wizardsandbeasts.owl.OWLGrade.E), true, 1, 0L));
        LOGGER.info("Bench look: wandlore commit {}", at.koopro.wizardsandbeasts.skill.vocation.VocationManager.commit(p,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "wandlore")));
        level.getServer().execute(() -> {
            if (level.getBlockEntity(bench) instanceof WandmakersBenchBlockEntity be) {
                for (BenchTool tool : BenchTool.values()) {
                    be.placeTool(tool);
                }
                ItemStack blank = new ItemStack(WandItemRegistry.WAND_BLANK.get());
                blank.set(WandComponents.WAND_WOOD.get(), Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "holly"));
                be.getInventory().set(0, ItemResource.of(blank), 1);
                be.getInventory().set(1, ItemResource.of(new ItemStack(WandItemRegistry.DRAGON_HEARTSTRING.get())), 1);
            }
        });
        LOGGER.info("Bench look: bench at {}", bench);
    }

    private static void openBench(ServerPlayer p) {
        if (!(p.level().getBlockEntity(bench) instanceof WandmakersBenchBlockEntity be)) {
            LOGGER.error("Bench look: no bench at {}", bench);
            return;
        }
        LOGGER.info("Bench look: state {}, stage {}", p.level().getBlockState(bench), be.stage());
        BlockPos side = p.level().getBlockState(bench.relative(Direction.EAST)).is(ModBlocks.WANDMAKERS_BENCH.get())
                ? bench.relative(Direction.EAST) : null;
        LOGGER.info("Bench look: side half east of the bench: {}", side != null);
        p.openMenu(new SimpleMenuProvider((id, inv, player) -> new WandmakersBenchMenu(id, inv, be),
                Component.translatable("gui.wizards_and_beasts.wandmakers_bench")), buf -> buf.writeBlockPos(bench));
    }

    private static void look(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.teleportTo(p.level(), bench.getX() + x, bench.getY() + y, bench.getZ() + z, Set.of(), yaw, pitch, false);
    }

    // ── autoplay: hits every beat but the third, keeps the bit in the band ──

    private static void autoplay(Minecraft mc) {
        BenchBeatClient.Snapshot s = BenchBeatClient.snapshot();
        KeyMapping use = mc.options.keyUse;
        if (s == null) {
            use.setDown(false);
            return;
        }
        if (s.odds().act().kind() == BenchCraft.Kind.HOLD) {
            int i = s.tick() - BenchCraft.LEAD;
            if (i >= 0 && i < BenchCraft.BORE_TICKS) {
                BenchCraft.BoreFrame[] frames = BenchCraft.bore(s.odds(), s.seed(), s.held());
                float marker = i == 0 ? 0.5f : frames[i - 1].marker();
                use.setDown(marker < frames[i].center());
            }
            return;
        }
        int[] beats = s.beat().beats();
        for (int b = 0; b < beats.length; b++) {
            if (beats[b] == s.tick() && b != 2) {
                KeyMapping.click(use.getKey());
            }
        }
    }

    // ── driving ──────────────────────────────────────────────────────────────

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
                System.getenv().getOrDefault("WB_BENCH_LOOK_WORLD", "wb_entity_showcase"), () -> stage = 3);
    }

    /** Before the beat client reads the key this tick. */
    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        if (ENABLED && stage == 2) {
            autoplay(Minecraft.getInstance());
        }
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
            parkCursor(mc);
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
        Path file = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "bench_look"))
                .resolve(SCRIPT.get(step).shot() + ".png");
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Bench look: cannot write {}", file, e);
            }
        }));
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            advance(mc, server);
        }
    }

    /**
     * Off every widget, so no tooltip covers the screen. Opening a screen re-centres the cursor, so this runs right
     * before each shot; MouseHandler only learns of a move through its callback, so its fields are set too (as
     * UiScreenCapture does).
     */
    private static void parkCursor(Minecraft mc) {
        GLFW.glfwSetCursorPos(mc.getWindow().handle(), 1.0, 1.0);
        try {
            for (String name : new String[]{"xpos", "ypos"}) {
                var f = net.minecraft.client.MouseHandler.class.getDeclaredField(name);
                f.setAccessible(true);
                f.setDouble(mc.mouseHandler, 1.0);
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Bench look: cannot park the cursor", e);
        }
    }

    private static void advance(Minecraft mc, MinecraftServer server) {
        step++;
        if (step >= SCRIPT.size()) {
            stage = 3;
            LOGGER.info("Bench look: done");
            mc.stop();
            return;
        }
        begin(mc, server);
    }

    private static void begin(Minecraft mc, MinecraftServer server) {
        Step current = SCRIPT.get(step);
        wait = current.ticks();
        mc.options.hideGui = !current.gui();
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
