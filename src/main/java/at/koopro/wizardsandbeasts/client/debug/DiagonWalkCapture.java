package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.diagon.DiagonAlley;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.BlockHitResult;
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
 * Walks a real client into Diagon Alley and out again: the bar of a Leaky Cauldron placed from its own template, the
 * back door used the way a player uses it (the block's own use), the passage between the two worlds, the yard and its
 * wall, the alley, and back through the yard's door to the bar. Shots go to {@code screenshots/diagon_walk}.
 *
 * <p>Development only, inert unless {@code WB_DIAGON_WALK} is set; {@code WB_DIAGON_WALK_WORLD} names a save that must
 * exist (default {@code wb_entity_showcase}).
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class DiagonWalkCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = System.getenv("WB_DIAGON_WALK") != null && !FMLEnvironment.isProduction();
    private static final Identifier PUB = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
            "diagon_alley/leaky_cauldron/a");
    /** The pub's back door, lower half, in its template; it faces into the bar (south). */
    private static final BlockPos PUB_DOOR = new BlockPos(2, 1, 0);

    private record Step(String shot, int ticks, Consumer<ServerPlayer> server) {}

    /** {@code WB_DIAGON_WALK=find}: the naturally generated pub, found the way the world finds it, and its Floo entry. */
    private static final List<Step> FIND = List.of(
            new Step("", 40, DiagonWalkCapture::findPub),
            new Step("", 60, p -> lookPub(p, 4, 1, 22, 180f)),
            new Step("natural_front", 10, p -> {}),
            new Step("", 30, p -> lookPub(p, -6, 3, 24, -150f)),
            new Step("natural_quarter", 10, p -> {}),
            new Step("", 30, DiagonWalkCapture::checkHearth),
            new Step("natural_from_hearth", 10, p -> {}));
    private static final List<Step> WALK = List.of(
            new Step("", 60, DiagonWalkCapture::stage),
            new Step("", 30, p -> lookPub(p, 4, 1, 11, 180f)),
            new Step("pub_bar", 10, p -> {}),
            new Step("", 30, p -> lookPub(p, 2, 1, 1, 180f)),
            new Step("", 3, DiagonWalkCapture::useBackDoor),
            new Step("passage", 6, p -> {}),
            new Step("", 40, p -> {}),
            new Step("yard", 10, p -> {}),
            new Step("", 20, p -> lookPocket(p, 5.5, 7, 17.5, -90f, -5f)),
            new Step("wall", 10, p -> {}),
            new Step("", 2, DiagonWalkCapture::tapKey),
            new Step("arch_a", 4, p -> {}),
            new Step("arch_b", 5, p -> {}),
            new Step("arch_c", 5, p -> {}),
            new Step("arch_d", 6, p -> {}),
            new Step("arch_e", 8, p -> {}),
            new Step("arch_open", 14, p -> {}),
            new Step("", 10, p -> lookPocket(p, 15.5, 7, 17.5, 90f, -3f)),
            new Step("arch_from_street", 10, p -> {}),
            new Step("", 5, p -> lookPocket(p, 5.5, 7, 17.5, -90f, -5f)),
            new Step("", 105, p -> {}),
            new Step("close_a", 5, p -> {}),
            new Step("close_b", 5, p -> {}),
            new Step("close_c", 5, p -> {}),
            new Step("close_d", 5, p -> {}),
            new Step("close_e", 8, p -> {}),
            new Step("closed", 20, p -> {}),
            new Step("", 30, p -> lookPocket(p, 12.6, 7, 17.5, -90f, 0f)),
            new Step("alley", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 17.5, 7, 20.5, 180f, -10f)),
            new Step("ollivanders", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 22.5, 7, 17.5, -90f, -6f)),
            new Step("street_1", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 40.5, 7, 18.5, -90f, -6f)),
            new Step("street_bend", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 49.5, 7, 17.5, 0f, -8f)),
            new Step("knockturn_mouth", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 49.5, 7, 30.5, 0f, -10f)),
            new Step("knockturn_lane", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 49.5, 7, 55.5, 0f, -65f)),
            new Step("knockturn_sky", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 49.5, 7, 64.5, 0f, -15f)),
            new Step("borgin_front", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 49.5, 7, 71.5, 0f, -8f)),
            new Step("borgin_inside", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 47.5, 7, 74.5, -90f, -5f)),
            new Step("borgin_cabinet", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 49.5, 7, 62.5, 180f, -6f)),
            new Step("knockturn_back", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 66.5, 7, 21.5, -90f, -14f)),
            new Step("gringotts", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 84.5, 7, 24.5, -90f, -25f)),
            new Step("gringotts_steps", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 98.5, 10, 24.5, -90f, -5f)),
            new Step("gringotts_hall", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 104.5, 10, 24.0, 180f, -10f)),
            new Step("gringotts_counter", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 110.5, 10, 24.5, -90f, 0f)),
            new Step("gringotts_vaults", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 53.5, 7, 22.5, 160f, -12f)),
            new Step("shops_north", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 41.5, 7, 16.0, 20f, -12f)),
            new Step("shops_south", 10, p -> {}),
            new Step("", 30, p -> lookPocket(p, 80.5, 8, 22.5, 90f, -4f)),
            new Step("looking_back", 10, p -> {}),
            new Step("", 40, p -> {}),
            new Step("", 30, p -> lookPocket(p, 40.5, 7, 18.5, -90f, -25f)),
            new Step("", 20, p -> p.level().getServer().getLevel(DiagonAlley.LEVEL).setDayTime(18000)),
            new Step("street_night", 10, p -> {}),
            new Step("", 5, p -> p.level().getServer().getLevel(DiagonAlley.LEVEL).setDayTime(6000)),
            new Step("", 30, p -> lookPocket(p, 1.5, 7, 17.5, 90f, 0f)),
            new Step("yard_door", 10, p -> {}),
            new Step("", 3, DiagonWalkCapture::useYardDoor),
            new Step("", 45, p -> {}),
            new Step("back_in_bar", 10, p -> {}));

    private static final List<Step> SCRIPT = "find".equals(System.getenv("WB_DIAGON_WALK")) ? FIND : WALK;
    private static BlockPos pub = BlockPos.ZERO;
    private static BlockPos hearth = BlockPos.ZERO;
    private static boolean opened;
    private static int stage;
    private static int wait;
    private static int step;
    private static boolean captureThisFrame;

    private DiagonWalkCapture() {}

    /** A Leaky Cauldron a little way off, on the save's ground; noon. */
    private static void stage(ServerPlayer p) {
        ServerLevel level = p.level().getServer().overworld();
        pub = BlockPos.containing(p.position()).offset(40, -1, 0);
        level.getStructureManager().get(PUB).ifPresent(template -> template.placeInWorld(level, pub, pub,
                new StructurePlaceSettings(), level.getRandom(), 2));
        level.setDayTime(6000);
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    }

    /** Logs the hearth's Floo entry and stands where a Floo traveller arrives, on the hearth. */
    private static void checkHearth(ServerPlayer p) {
        ServerLevel level = p.level().getServer().overworld();
        var entry = at.koopro.wizardsandbeasts.floo.FlooNetworkManager.get(level)
                .findByPos(net.minecraft.world.level.Level.OVERWORLD.identifier(), hearth);
        LOGGER.info("Diagon walk: Floo entry at the hearth {}: {}", hearth, entry);
        p.teleportTo(level, hearth.getX() + 0.5, hearth.getY() + 1, hearth.getZ() + 0.5, Set.of(), 0f, 0f, false);
    }

    /** Finds the one Leaky Cauldron, loads its chunk (which puts its hearth on the Floo Network), stands by it. */
    private static void findPub(ServerPlayer p) {
        ServerLevel level = p.level().getServer().overworld();
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        level.setDayTime(6000);
        var structures = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        var holder = structures.getOrThrow(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "leaky_cauldron")));
        BlockPos found = DiagonAlley.locatePub(level);
        if (found == null) {
            LOGGER.warn("Diagon walk: no Leaky Cauldron in this world");
            return;
        }
        BlockPos near = found;
        var start = level.getChunk(near.getX() >> 4, near.getZ() >> 4).getStartForStructure(holder.value());
        if (start == null) {
            LOGGER.warn("Diagon walk: the Leaky Cauldron's chunk {} holds no start (biome?)", near);
            return;
        }
        LOGGER.info("Diagon walk: Leaky Cauldron located at {} ({} blocks from spawn), start valid {}", near,
                (int) Math.sqrt(near.distSqr(p.blockPosition())), start.isValid());
        for (var piece : start.getPieces()) {
            if (piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece element) {
                pub = element.getPosition();
                hearth = pub.offset(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(
                        new BlockPos(5, 1, 1), net.minecraft.world.level.block.Mirror.NONE, element.getRotation(),
                        BlockPos.ZERO));
                LOGGER.info("Diagon walk: pub piece at {} turned {}", pub, element.getRotation());
            }
        }
        p.teleportTo(level, near.getX() + 0.5, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types
                .MOTION_BLOCKING, near.getX(), near.getZ()) + 2, near.getZ() + 0.5, Set.of(), 0f, 0f, false);
    }

    private static void lookPub(ServerPlayer p, int x, int y, int z, float yaw) {
        look(p, pub.offset(x, y, z), yaw, 0f);
    }

    private static void look(ServerPlayer p, BlockPos feet, float yaw, float pitch) {
        ServerLevel overworld = p.level().getServer().overworld();
        p.teleportTo(overworld, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, Set.of(), yaw, pitch, false);
    }

    private static void lookPocket(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        ServerLevel pocket = p.level().getServer().getLevel(DiagonAlley.LEVEL);
        if (pocket == null) {
            LOGGER.warn("Diagon walk: no pocket level");
            return;
        }
        p.teleportTo(pocket, DiagonAlley.ORIGIN.getX() + x, DiagonAlley.ORIGIN.getY() + y, DiagonAlley.ORIGIN.getZ() + z,
                Set.of(), yaw, pitch, false);
    }

    private static void use(ServerPlayer p, ServerLevel level, BlockPos door) {
        BlockState state = level.getBlockState(door);
        LOGGER.info("Diagon walk: using {} at {}", state, door);
        state.useWithoutItem(level, p, new BlockHitResult(Vec3.atCenterOf(door), Direction.NORTH, door, false));
    }

    /** A wand in hand, used on the key brick the way a player's right-click uses it. */
    private static void tapKey(ServerPlayer p) {
        ServerLevel pocket = p.level().getServer().getLevel(DiagonAlley.LEVEL);
        if (pocket == null) {
            return;
        }
        net.minecraft.world.item.ItemStack wand = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
                .filter(item -> item instanceof at.koopro.wizardsandbeasts.item.wand.WandItem).findFirst()
                .map(net.minecraft.world.item.ItemStack::new).orElse(net.minecraft.world.item.ItemStack.EMPTY);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wand);
        BlockPos key = DiagonAlley.ORIGIN.offset(11, 9, 17);
        BlockState state = pocket.getBlockState(key);
        LOGGER.info("Diagon walk: tapping {} at {} with {}", state, key, wand);
        var result = state.useItemOn(wand, pocket, p, net.minecraft.world.InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(key), Direction.WEST, key, false));
        LOGGER.info("Diagon walk: tap -> {}", result);
    }

    private static void useBackDoor(ServerPlayer p) {
        use(p, p.level().getServer().overworld(), pub.offset(PUB_DOOR));
    }

    private static void useYardDoor(ServerPlayer p) {
        ServerLevel pocket = p.level().getServer().getLevel(DiagonAlley.LEVEL);
        if (pocket != null) {
            use(p, pocket, DiagonAlley.ORIGIN.offset(0, 7, 17));
        }
    }

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
        if (SCRIPT == FIND) {
            // A fresh, normally generated world, so the pub generates the way it does for a player.
            String name = "diagon_find_" + Long.toHexString(System.currentTimeMillis());
            long seed = Long.parseLong(System.getenv().getOrDefault("WB_DIAGON_WALK_SEED", "271828182"));
            var rules = new net.minecraft.world.level.gamerules.GameRules(
                    net.minecraft.world.level.WorldDataConfiguration.DEFAULT.enabledFeatures());
            mc.createWorldOpenFlows().createFreshLevel(name, new net.minecraft.world.level.LevelSettings(name,
                            net.minecraft.world.level.GameType.CREATIVE, false, net.minecraft.world.Difficulty.PEACEFUL,
                            true, rules, net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                    new net.minecraft.world.level.levelgen.WorldOptions(seed, true, false),
                    net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions, new TitleScreen());
            wait = 200;
            return;
        }
        mc.createWorldOpenFlows().openWorld(
                System.getenv().getOrDefault("WB_DIAGON_WALK_WORLD", "wb_entity_showcase"), () -> stage = 3);
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
        // A new character meets the heritage screen, and a world may ask before it opens: press on through either.
        if (SCRIPT == FIND && mc.screen != null
                && !(mc.screen instanceof at.koopro.wizardsandbeasts.client.diagon.DiagonPassageScreen)) {
            for (var child : mc.screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().getString().matches("(?i).*(proceed|continue|yes|open).*")) {
                    button.onPress(new net.minecraft.client.input.KeyEvent(
                            com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 0, 0));
                    break;
                }
            }
            if (mc.player != null) {
                mc.setScreen(null);
            }
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
        Path file = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "diagon_walk"))
                .resolve(SCRIPT.get(step).shot() + ".png");
        LOGGER.info("Diagon walk: shot {} in {} (screen {})", SCRIPT.get(step).shot(),
                mc.level == null ? "none" : mc.level.dimension().identifier(), mc.screen);
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Diagon walk: cannot write {}", file, e);
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
            LOGGER.info("Diagon walk: done");
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
        if (!(mc.screen instanceof at.koopro.wizardsandbeasts.client.diagon.DiagonPassageScreen)) {
            mc.setScreen(null);
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
