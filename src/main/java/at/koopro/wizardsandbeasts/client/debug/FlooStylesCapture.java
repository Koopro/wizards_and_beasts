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
 * Photographs every Floo hearth style side by side for the P7 look gate (2026-11-26): each built from its pattern's
 * example palette by {@code HearthStamp}, lit and roaring green, a villager in two of them for scale, and logs which
 * style the grate recognises (so a castle that reads as a cottage shows up here). Development only, inert unless
 * {@code WB_FLOO_STYLES=1}; shots go to {@code screenshots/floo_styles}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class FlooStylesCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = "1".equals(System.getenv("WB_FLOO_STYLES")) && !FMLEnvironment.isProduction();

    private record Step(String shot, int ticks, Consumer<ServerPlayer> server) {}

    private static final List<Step> SCRIPT = new ArrayList<>();
    private static BlockPos grate = BlockPos.ZERO;
    private static boolean opened;
    private static int stage;
    private static int wait;
    private static int step;
    private static boolean captureThisFrame;

    private FlooStylesCapture() {}

    private static final String[] STYLES = {"cottage", "castle", "atrium", "shop"};
    private static final int SPACING = 9;

    private static BlockPos site(int i) {
        return grate.offset((i - 1) * SPACING - SPACING / 2, 0, 0);
    }

    private static void script() {
        SCRIPT.add(new Step("", 120, FlooStylesCapture::stageHall));
        SCRIPT.add(new Step("", 30, p -> lightAll(p)));
        SCRIPT.add(new Step("", 40, p -> look(p, 0.5, 4.5, 20.0, 180f, 12f)));
        SCRIPT.add(new Step("0_hall", 10, p -> {}));
        for (int i = 0; i < STYLES.length; i++) {
            int at = i;
            SCRIPT.add(new Step("", 40, p -> {
                lightAll(p);
                BlockPos s = site(at);
                p.teleportTo(p.level(), s.getX() + 0.5 + (STYLES[at].equals("shop") ? 0.5 : 0.0), s.getY() + 0.6,
                        s.getZ() + 8.5, Set.of(), 180f, 6f, false);
            }));
            SCRIPT.add(new Step((at + 1) + "_" + STYLES[at], 10, p -> {}));
        }
    }

    private static void lightAll(ServerPlayer p) {
        ServerLevel level = p.level();
        for (int i = 0; i < STYLES.length; i++) {
            BlockPos s = site(i);
            BlockState state = level.getBlockState(s);
            if (!state.is(ModBlocks.FLOO_GRATE.get())) {
                LOGGER.error("Floo styles: no grate at {}", s);
                continue;
            }
            if (state.getValue(FlooGrateBlock.FIRE) != HearthFire.FLOO) {
                level.setBlock(s, state.setValue(FlooGrateBlock.FUEL, true).setValue(FlooGrateBlock.FIRE, HearthFire.WARM), 3);
                if (level.getBlockEntity(s) instanceof FlooGrateBlockEntity be) {
                    boolean formed = be.recheck(level, level.getBlockState(s));
                    be.throwPinch(level);
                    LOGGER.info("Floo styles: {} at {} formed={} recognised as {} opening {}x{}", STYLES[i], s, formed,
                            be.style().orElse(null), be.openingWidth(), be.openingHeight());
                }
            }
        }
    }

    /** A long stone hall, the four hearths set into its north wall, lanterns, a villager in two of the fires. */
    private static void stageHall(ServerPlayer p) {
        ServerLevel level = p.level();
        grate = p.blockPosition().offset(600, 0, 0);
        BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
        for (int x = -24; x <= 24; x++) {
            for (int z = -3; z <= 24; z++) {
                for (int y = -2; y <= 12; y++) {
                    c.set(grate.getX() + x, grate.getY() + y, grate.getZ() + z);
                    BlockState s = y < 0 ? Blocks.SPRUCE_PLANKS.defaultBlockState()
                            : y == 11 || Math.abs(x) == 24 || z == -2 || z == 24 ? Blocks.STONE_BRICKS.defaultBlockState()
                            : Blocks.AIR.defaultBlockState();
                    level.setBlock(c, s, 2);
                }
            }
        }
        for (int i = 0; i < STYLES.length; i++) {
            BlockPos s = site(i);
            boolean ok = at.koopro.wizardsandbeasts.floo.hearth.HearthStamp.stamp(level, s, Direction.SOUTH,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("wizards_and_beasts", STYLES[i]));
            LOGGER.info("Floo styles: stamped {} at {}: {}", STYLES[i], s, ok);
        }
        for (int x = -20; x <= 20; x += 8) {
            level.setBlock(grate.offset(x, 10, 6), Blocks.LANTERN.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), 3);
        }
        for (int i : new int[]{1, 2}) {
            Entity villager = EntityType.VILLAGER.create(level, EntitySpawnReason.COMMAND);
            if (villager != null) {
                BlockPos s = site(i);
                villager.snapTo(s.getX() + 0.5, s.getY() + 0.25, s.getZ() + 0.5, 0.0F, 0.0F);
                if (villager instanceof Mob mob) {
                    mob.setNoAi(true);
                }
                villager.setSilent(true);
                villager.setInvulnerable(true);
                level.addFreshEntity(villager);
            }
        }
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        p.setGameMode(GameType.CREATIVE);
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        p.getInventory().clearContent();
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
                System.getenv().getOrDefault("WB_FLOO_STYLES_WORLD", "wb_entity_showcase"), () -> stage = 3);
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
        Path file = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "floo_styles"))
                .resolve(SCRIPT.get(step).shot() + ".png");
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Floo styles: cannot write {}", file, e);
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
            LOGGER.info("Floo styles: done");
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
