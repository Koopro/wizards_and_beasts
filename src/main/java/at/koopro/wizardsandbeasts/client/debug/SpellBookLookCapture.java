package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.learning.book.SpellBookService;
import at.koopro.wizardsandbeasts.spell.learning.book.SpellBookStacks;
import at.koopro.wizardsandbeasts.spell.learning.book.SpellBooks;
import at.koopro.wizardsandbeasts.spell.learning.tracing.SpellTraceService;
import at.koopro.wizardsandbeasts.spell.learning.tracing.TraceChallenge;
import at.koopro.wizardsandbeasts.spell.learning.tracing.TracePath;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
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
import java.util.function.Consumer;

/**
 * Photographs the spell textbooks (spell acquisition remake, 2026-11-27): the thirteen covers in the inventory, The
 * Dark Forces read through by someone who knows no spell, the Lumos trace and its pass, the page afterwards with Lumos
 * ticked, the Grade 1 book (open and locked chapters) and Magick Moste Evile's refusals, and a book held in hand.
 *
 * <p>Development only, inert unless {@code WB_SPELLBOOK_LOOK=1}; {@code WB_SPELLBOOK_LOOK_WORLD} names the save. Shots
 * go to {@code screenshots/spellbook_look}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class SpellBookLookCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = "1".equals(System.getenv("WB_SPELLBOOK_LOOK")) && !FMLEnvironment.isProduction();

    private record Step(String shot, int ticks, boolean gui, Consumer<ServerPlayer> server, Consumer<Minecraft> client) {}

    private static final List<Step> SCRIPT = new ArrayList<>();
    private static boolean opened;
    private static int stage;
    private static int wait;
    private static int step;
    private static boolean captureThisFrame;

    private SpellBookLookCapture() {}

    private static Step server(int ticks, Consumer<ServerPlayer> action) {
        return new Step("", ticks, true, action, mc -> {});
    }

    private static Step client(int ticks, Consumer<Minecraft> action) {
        return new Step("", ticks, true, p -> {}, action);
    }

    private static Step shot(String name) {
        return new Step(name, 6, true, p -> {}, mc -> {});
    }

    private static void script() {
        SCRIPT.add(server(80, SpellBookLookCapture::stage));
        SCRIPT.add(client(30, mc -> mc.setScreen(new InventoryScreen(mc.player))));
        SCRIPT.add(shot("inventory_covers"));
        SCRIPT.add(client(10, mc -> mc.setScreen(null)));
        SCRIPT.add(server(20, p -> hold(p, "the_dark_forces")));
        SCRIPT.add(new Step("held_dark_forces", 6, true, p -> {}, mc -> {}));
        SCRIPT.add(server(30, p -> SpellBookService.open(p, InteractionHand.MAIN_HAND)));
        SCRIPT.add(shot("book_dark_forces"));
        SCRIPT.add(server(30, p -> SpellBookService.studyChapter(p, InteractionHand.MAIN_HAND,
                at.koopro.wizardsandbeasts.spell.core.Spells.byId("wizards_and_beasts:lumos").getId())));
        SCRIPT.add(shot("trace_lumos"));
        SCRIPT.add(server(30, SpellBookLookCapture::passTrace));
        SCRIPT.add(shot("trace_passed"));
        SCRIPT.add(client(20, mc -> {
            if (mc.screen != null) {
                mc.screen.onClose();
            }
        }));
        SCRIPT.add(shot("book_after_lumos"));
        SCRIPT.add(client(10, mc -> mc.setScreen(null)));
        SCRIPT.add(server(10, p -> hold(p, "standard_book_of_spells_grade_1")));
        SCRIPT.add(server(30, p -> SpellBookService.open(p, InteractionHand.MAIN_HAND)));
        SCRIPT.add(shot("book_grade_1"));
        SCRIPT.add(client(10, mc -> mc.setScreen(null)));
        SCRIPT.add(server(10, p -> hold(p, "magick_moste_evile")));
        SCRIPT.add(server(30, p -> SpellBookService.open(p, InteractionHand.MAIN_HAND)));
        SCRIPT.add(shot("book_moste_evile"));
    }

    /** Survival, no spell known, an empty wheel, every shipped book in the pack. */
    private static void stage(ServerPlayer p) {
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        PlayerSpellData spells = p.getData(ModAttachments.SPELL_DATA.get());
        for (String id : List.copyOf(spells.getKnownSpells())) {
            spells.forgetSpell(id);
        }
        for (int slot = 0; slot < PlayerSpellData.LOADOUT_SIZE; slot++) {
            spells.setLoadoutSpell(slot, null);
        }
        at.koopro.wizardsandbeasts.network.spell.SpellDataSyncS2CPayload.syncToPlayer(p);
        for (int i = 0; i < SpellBooks.SHIPPED.size(); i++) {
            p.getInventory().setItem(i < 9 ? i : 9 + (i - 9), SpellBookStacks.of(SpellBooks.SHIPPED.get(i)));
        }
        p.level().setDayTime(6000);
        LOGGER.info("Spellbook look: staged, {} books", SpellBooks.SHIPPED.size());
    }

    private static void hold(ServerPlayer p, String book) {
        p.setItemInHand(InteractionHand.MAIN_HAND, SpellBookStacks.of(book));
    }

    /** Traces the live challenge's own centre line, as a careful hand would, a few seconds after it opened. */
    private static void passTrace(ServerPlayer p) {
        TraceChallenge c = SpellTraceService.active(p);
        if (c == null) {
            LOGGER.error("Spellbook look: no trace to pass");
            return;
        }
        SpellTraceService.backdateForTest(p, 80);
        TracePath path = c.path();
        double[] xy = new double[path.size() * 2];
        for (int i = 0; i < path.size(); i++) {
            xy[2 * i] = path.x(i);
            xy[2 * i + 1] = path.y(i);
        }
        LOGGER.info("Spellbook look: trace {}", SpellTraceService.submit(p, c.id(), false, xy));
    }

    // ── driving (as BenchLookCapture) ───────────────────────────────────────────

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
                System.getenv().getOrDefault("WB_SPELLBOOK_LOOK_WORLD", "wb_entity_showcase"), () -> stage = 3);
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
        Path file = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "spellbook_look"))
                .resolve(SCRIPT.get(step).shot() + ".png");
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Spellbook look: cannot write {}", file, e);
            }
        }));
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            advance(mc, server);
        }
    }

    private static void parkCursor(Minecraft mc) {
        GLFW.glfwSetCursorPos(mc.getWindow().handle(), 1.0, 1.0);
        try {
            for (String name : new String[]{"xpos", "ypos"}) {
                var f = net.minecraft.client.MouseHandler.class.getDeclaredField(name);
                f.setAccessible(true);
                f.setDouble(mc.mouseHandler, 1.0);
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Spellbook look: cannot park the cursor", e);
        }
    }

    private static void advance(Minecraft mc, MinecraftServer server) {
        step++;
        if (step >= SCRIPT.size()) {
            stage = 3;
            LOGGER.info("Spellbook look: done");
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
        current.client().accept(mc);
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                current.server().accept(player);
            }
        });
    }
}
