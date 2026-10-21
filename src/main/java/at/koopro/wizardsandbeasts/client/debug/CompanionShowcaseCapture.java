package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.character.CharacterSheetScreen;
import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.creature.companion.CompanionCommand;
import at.koopro.wizardsandbeasts.creature.companion.CompanionMemory;
import at.koopro.wizardsandbeasts.creature.companion.CompanionRoster;
import at.koopro.wizardsandbeasts.creature.companion.Companions;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.PathfinderMob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Photographs the Beasts tab through the real path: companions claimed by the bond layer's own
 * {@code increaseBond}, ordered with {@code Companions.command}, and the roster the server syncs. Shots: the empty
 * tab, a keeper's roster with a trained niffler selected, and the same niffler remembered after its death.
 *
 * <p>Development only, inert unless {@code WB_COMPANION_SHOWCASE=1}. {@code WB_COMPANION_SHOWCASE_WORLD} names a
 * disposable save (default {@code wb_equipment_capture}); shots go to {@code screenshots/companion_showcase}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class CompanionShowcaseCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED =
            System.getenv("WB_COMPANION_SHOWCASE") != null && !FMLEnvironment.isProduction();

    private record Step(String shot, int ticks, Consumer<ServerPlayer> server) {}

    private static final List<Step> SCRIPT = List.of(
            new Step("", 40, CompanionShowcaseCapture::reset),
            new Step("beasts_empty", 30, p -> {}),
            new Step("", 40, CompanionShowcaseCapture::keeper),
            new Step("beasts_roster", 30, p -> {}),
            new Step("", 30, CompanionShowcaseCapture::loss),
            new Step("beasts_memorial", 30, p -> {}));

    private static final List<Entity> SPAWNED = new ArrayList<>();

    private static boolean opened;
    private static int stage; // 0 idle, 1 loading, 2 running, 3 done
    private static int wait;
    private static int step;
    private static boolean captureThisFrame;

    private CompanionShowcaseCapture() {}

    private static void reset(ServerPlayer p) {
        HeritageAPI.commit(p, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
        p.setData(ModAttachments.COMPANION_ROSTER.get(), CompanionRoster.EMPTY);
        Companions.markDirty(p);
    }

    private static @Nullable PathfinderMob spawn(ServerPlayer p, String id, double dx, double dz) {
        ServerLevel level = p.level();
        Entity entity = BuiltInRegistries.ENTITY_TYPE
                .getValue(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, id))
                .create(level, EntitySpawnReason.COMMAND);
        if (!(entity instanceof PathfinderMob mob) || !(entity instanceof BondableBeast)) {
            return null;
        }
        mob.setPos(p.getX() + dx, p.getY(), p.getZ() + dz);
        mob.setNoAi(true);
        level.addFreshEntity(mob);
        SPAWNED.add(mob);
        return mob;
    }

    /** Claims {@code mob} for {@code p} at {@code bond}, with fixed traits and {@code training}. */
    private static void own(ServerPlayer p, PathfinderMob mob, int bond, List<String> traits, int training) {
        BondableBeast beast = (BondableBeast) mob;
        beast.increaseBond(p, bond, false);
        CompanionMemory memory = Companions.memory(beast).withTraits(traits);
        beast.bondState().setCompanion(memory.withTraining(training, 0, Companions.day(beast)));
        Companions.afterChange(beast);
    }

    private static void keeper(ServerPlayer p) {
        PathfinderMob niffler = spawn(p, "niffler", 2.5, 1.0);
        if (niffler != null) {
            BondableBeast beast = (BondableBeast) niffler;
            niffler.setCustomName(Component.literal("Goldie"));
            own(p, niffler, 100, List.of("greedy", "playful"), 260);
            Companions.command(p, beast, CompanionCommand.STAY);
            beast.bondState().setCompanion(Companions.memory(beast)
                    .remember(Companions.day(beast), "companion.wizards_and_beasts.history.raised_young", ""));
            Companions.refreshRoster(beast);
        }
        PathfinderMob unicorn = spawn(p, "unicorn", -2.5, 1.5);
        if (unicorn != null) {
            own(p, unicorn, 45, List.of("skittish", "loyal"), 0);
        }
        PathfinderMob bowtruckle = spawn(p, "bowtruckle", 0.0, 3.0);
        if (bowtruckle != null) {
            BondableBeast beast = (BondableBeast) bowtruckle;
            bowtruckle.setCustomName(Component.literal("Twig"));
            own(p, bowtruckle, 100, List.of("territorial", "loyal"), 60);
            beast.bondState().setCompanion(Companions.memory(beast)
                    .withLineage(1, List.of("Bark", "Hazel"), Companions.day(beast)));
            Companions.refreshRoster(beast);
        }
    }

    private static void loss(ServerPlayer p) {
        for (Entity entity : SPAWNED) {
            if (entity instanceof PathfinderMob mob && mob.getCustomName() != null
                    && "Goldie".equals(mob.getCustomName().getString())) {
                mob.kill(p.level());
            }
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
        mc.createWorldOpenFlows().openWorld(
                System.getenv().getOrDefault("WB_COMPANION_SHOWCASE_WORLD", "wb_equipment_capture"), () -> stage = 3);
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
        Path file = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "companion_showcase"))
                .resolve(SCRIPT.get(step).shot() + ".png");
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Companion showcase: cannot write {}", file, e);
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
            server.execute(() -> SPAWNED.forEach(Entity::discard));
            LOGGER.info("Companion showcase: done");
            mc.stop();
            return;
        }
        begin(mc, server);
    }

    private static void begin(Minecraft mc, MinecraftServer server) {
        Step current = SCRIPT.get(step);
        wait = current.ticks();
        if (!current.shot().isEmpty()) {
            mc.setScreen(CharacterSheetScreen.onBeastsTab());
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
