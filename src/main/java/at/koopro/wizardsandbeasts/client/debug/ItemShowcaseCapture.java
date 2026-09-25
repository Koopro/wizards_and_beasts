package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.bus.api.SubscribeEvent;
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

/**
 * Photographs items the way a player meets them, so an item model can be judged by looking at it
 * rather than by reading its JSON.
 *
 * <p>An item model is right only if it reads correctly in six places at once — the slot, the
 * hotbar, the ground, an item frame, the first-person hand and another player's hand — and the
 * display transforms that decide those six are easy to get wrong in ways no validator sees: a model
 * can be perfect in a slot and fill half the screen in first person. This drives a real client
 * through every one of them and writes screenshots, so a whole roster can be reviewed on one
 * contact sheet and a change can be compared against the run before it.
 *
 * <p>Development only, and inert unless asked for: it does nothing in production and nothing
 * unless {@code WB_ITEM_SHOWCASE} names a file of items, one per line ({@code #} comments): a bare
 * id, or {@code label=id[components]} in {@code /give} syntax for a stack that needs components (a
 * wand of a given wood), shot as {@code <label>}.
 * {@code WB_ITEM_SHOWCASE_WORLD} names the save to open (a disposable copy — the scene is built
 * into it) and {@code WB_ITEM_SHOWCASE_OUT} the folder under {@code screenshots/} to write to.
 * The client quits when the last item is done. Two shots per item: {@code <id>__fp.png} (hotbar,
 * first-person hand, the item on the ground and in a frame) and {@code <id>__tp.png} (third person,
 * from the front).
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class ItemShowcaseCapture {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Ticks after a change before the frame is taken: long enough for the hand's equip swing. */
    private static final int SETTLE_TICKS = 12;
    private static final int LOAD_TICKS = 80;
    private static final int STAGE_Y = 200;

    private enum View { FIRST_PERSON, THIRD_PERSON }

    private enum Stage { IDLE, OPENING, LOADING, RUNNING, DONE }

    private static final Request REQUEST = Request.fromEnvironment();
    private static Stage stage = Stage.IDLE;
    private static int wait;
    private static int shot;
    private static boolean captureThisFrame;
    private static boolean opened;

    private ItemShowcaseCapture() {}

    /** One line of the item list: the file name it is shot as, and the stack in /give syntax. */
    private record Entry(String label, String spec) {
        static Entry parse(String line) {
            int eq = line.indexOf('=');
            int bracket = line.indexOf('[');
            if (eq > 0 && (bracket < 0 || eq < bracket)) {
                return new Entry(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
            }
            String spec = line.contains(":") ? line : WizardsAndBeastsMod.MODID + ":" + line;
            return new Entry(Identifier.parse(spec).getPath(), spec);
        }
    }

    private record Request(List<Entry> items, String world, Path out) {
        static Request fromEnvironment() {
            String list = System.getenv("WB_ITEM_SHOWCASE");
            if (list == null || FMLEnvironment.isProduction()) {
                return null;
            }
            List<Entry> items = new ArrayList<>();
            try {
                for (String line : Files.readAllLines(Path.of(list))) {
                    String id = line.strip();
                    if (!id.isEmpty() && !id.startsWith("#")) {
                        items.add(Entry.parse(id));
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Item showcase: cannot read {}", list, e);
                return null;
            }
            String world = System.getenv().getOrDefault("WB_ITEM_SHOWCASE_WORLD", "wb_item_showcase");
            String out = System.getenv().getOrDefault("WB_ITEM_SHOWCASE_OUT", "item_showcase");
            return new Request(items, world, Path.of(Screenshot.SCREENSHOT_DIR, out));
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        // Resizing the window re-initialises the title screen; open the world once, not per init.
        if (REQUEST == null || opened || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        opened = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.bobView().set(false);
        mc.options.guiScale().set(3);
        GLFW.glfwSetWindowSize(mc.getWindow().handle(), 1600, 900);
        stage = Stage.OPENING;
        LOGGER.info("Item showcase: {} items into {}", REQUEST.items().size(), REQUEST.out());
        mc.createWorldOpenFlows().openWorld(REQUEST.world(), () -> stage = Stage.DONE);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (REQUEST == null || stage == Stage.IDLE || stage == Stage.DONE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || mc.level == null || server == null) {
            return;
        }
        if (stage == Stage.OPENING) {
            stage = Stage.LOADING;
            wait = LOAD_TICKS;
            server.execute(() -> buildStage(server));
            return;
        }
        if (captureThisFrame || --wait > 0) {
            return;
        }
        if (stage == Stage.LOADING) {
            stage = Stage.RUNNING;
            shot = 0;
            present(mc, server);
            return;
        }
        // Toasts and chat from the world itself (advancements, recipes) would sit over the shot.
        mc.getToastManager().clear();
        mc.gui.getChat().clearMessages(false);
        captureThisFrame = true;
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        if (!captureThisFrame) {
            return;
        }
        captureThisFrame = false;
        Minecraft mc = Minecraft.getInstance();
        Entry entry = REQUEST.items().get(shot / 2);
        String suffix = view() == View.FIRST_PERSON ? "__fp.png" : "__tp.png";
        Path file = mc.gameDirectory.toPath().resolve(REQUEST.out()).resolve(entry.label() + suffix);
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Item showcase: cannot write {}", file, e);
            }
        }));
        shot++;
        MinecraftServer server = mc.getSingleplayerServer();
        if (shot >= REQUEST.items().size() * 2 || server == null) {
            stage = Stage.DONE;
            LOGGER.info("Item showcase: done, {} shots", shot);
            mc.stop();
            return;
        }
        present(mc, server);
    }

    private static View view() {
        return shot % 2 == 0 ? View.FIRST_PERSON : View.THIRD_PERSON;
    }

    /** Puts the current item in the hand, the frame and on the ground, and turns the camera. */
    private static void present(Minecraft mc, MinecraftServer server) {
        Entry entry = REQUEST.items().get(shot / 2);
        ItemStack stack;
        try {
            ItemParser.ItemResult parsed = new ItemParser(server.registryAccess()).parse(new StringReader(entry.spec()));
            stack = new ItemStack(parsed.item(), 1, parsed.components());
        } catch (CommandSyntaxException e) {
            LOGGER.warn("Item showcase: cannot parse {}: {}", entry.spec(), e.getMessage());
            stack = ItemStack.EMPTY;
        }
        final ItemStack shown = stack;
        View view = view();
        mc.options.setCameraType(view == View.FIRST_PERSON ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_FRONT);
        mc.options.hideGui = view != View.FIRST_PERSON;
        // The selected slot is the client's to choose; the server only hears about it afterwards.
        mc.player.getInventory().setSelectedSlot(0);
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                return;
            }
            ServerLevel level = player.level();
            level.setDayTime(6000);
            player.getInventory().setItem(0, shown.copy());
            // Vanilla neighbours in the hotbar: the yardstick the new icon is read against.
            player.getInventory().setItem(1, new ItemStack(Items.IRON_SWORD));
            player.getInventory().setItem(2, new ItemStack(Items.BOOK));
            player.getInventory().setItem(3, new ItemStack(Items.SPYGLASS));
            player.getInventory().setItem(4, new ItemStack(Items.POTION));
            AABB area = new AABB(-8, STAGE_Y - 2, -10, 8, STAGE_Y + 6, 6);
            level.getEntitiesOfClass(ItemFrame.class, area).forEach(frame -> frame.setItem(shown.copy(), false));
            level.getEntitiesOfClass(ItemEntity.class, area).forEach(item -> item.setItem(shown.copy()));
        });
        wait = SETTLE_TICKS;
    }

    /**
     * A floating stage in clear sky: a floor, a back wall with an item frame at eye height, one
     * dropped item between, and the player standing at the front looking at them.
     */
    private static void buildStage(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        server.setDifficulty(Difficulty.PEACEFUL, true);
        level.setWeatherParameters(24000, 0, false, false);
        player.setGameMode(GameType.CREATIVE);
        AABB area = new AABB(-8, STAGE_Y - 2, -10, 8, STAGE_Y + 6, 6);
        level.getEntitiesOfClass(ItemFrame.class, area).forEach(e -> e.discard());
        level.getEntitiesOfClass(ItemEntity.class, area).forEach(e -> e.discard());
        for (int x = -4; x <= 4; x++) {
            for (int z = -6; z <= 3; z++) {
                level.setBlockAndUpdate(new BlockPos(x, STAGE_Y - 1, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = 0; y < 5; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, STAGE_Y + y, z), Blocks.AIR.defaultBlockState());
                }
            }
            for (int y = 0; y < 4; y++) {
                level.setBlockAndUpdate(new BlockPos(x, STAGE_Y + y, -4), Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
        }
        ItemFrame frame = new ItemFrame(level, new BlockPos(1, STAGE_Y + 1, -3), Direction.SOUTH);
        level.addFreshEntity(frame);
        ItemEntity dropped = new ItemEntity(level, -1.0, STAGE_Y, -1.5, new ItemStack(Items.STONE), 0, 0, 0);
        dropped.setUnlimitedLifetime();
        level.addFreshEntity(dropped);
        player.teleportTo(level, 0.5, STAGE_Y, 0.5, Set.of(), 180.0F, 15.0F, true);
    }
}
