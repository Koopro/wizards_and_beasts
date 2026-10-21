package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.goblin.GoblinTellerEntity;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.location.LocationProgression;
import at.koopro.wizardsandbeasts.location.PlayerLocationStanding;
import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress.ActiveContract;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.registry.ModVillager;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellPractice;
import at.koopro.wizardsandbeasts.treasure.CurseKind;
import at.koopro.wizardsandbeasts.treasure.TreasureArtefact;
import at.koopro.wizardsandbeasts.treasure.TreasureArtefacts;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
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
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Photographs location boards through the real path: a goblin teller spawned beside the player and spoken to
 * ({@link LocationProgression#openAtHost}), the board the server sends, a cursed artefact in hand for the service
 * quotes; then the same customer at Curse-Breaker Associate with contracts in hand; then a wandmaker's board.
 *
 * <p>Development only, inert unless {@code WB_LOCATION_SHOWCASE=1}. {@code WB_LOCATION_SHOWCASE_WORLD} names a
 * disposable save (default {@code wb_equipment_capture}); shots go to {@code screenshots/location_showcase}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class LocationShowcaseCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED =
            System.getenv("WB_LOCATION_SHOWCASE") != null && !FMLEnvironment.isProduction();
    private static final Identifier GRINGOTTS = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "gringotts");

    /** @param keepScreen a shot-less step that acts on the open board (a scroll) instead of replacing it */
    private record Step(String shot, int ticks, Consumer<ServerPlayer> server, boolean keepScreen) {
        Step(String shot, int ticks, Consumer<ServerPlayer> server) {
            this(shot, ticks, server, false);
        }
    }

    private static final List<Step> SCRIPT = List.of(
            new Step("", 40, LocationShowcaseCapture::customer),
            new Step("gringotts_customer", 30, p -> {}),
            new Step("", 30, LocationShowcaseCapture::associate),
            new Step("gringotts_associate", 30, p -> {}),
            new Step("", 30, LocationShowcaseCapture::wandmaker),
            new Step("ollivanders_patron", 30, p -> {}),
            new Step("", 30, LocationShowcaseCapture::apothecary),
            new Step("apothecary_customer", 30, p -> {}),
            new Step("", 10, p -> scrollBoard(-30), true),
            new Step("apothecary_for_sale", 20, p -> {}),
            new Step("", 30, LocationShowcaseCapture::fence),
            new Step("fence_stranger", 30, p -> {}),
            new Step("", 10, p -> scrollBoard(-30), true),
            new Step("fence_for_sale", 20, p -> {}));

    private static boolean opened;
    private static int stage; // 0 idle, 1 loading, 2 running, 3 done
    private static int wait;
    private static int step;
    private static boolean captureThisFrame;

    private LocationShowcaseCapture() {}

    private static GoblinTellerEntity teller(ServerPlayer p) {
        ServerLevel level = p.level();
        GoblinTellerEntity goblin = ModEntities.GOBLIN_TELLER.get().spawn(level,
                BlockPos.containing(p.position().add(p.getLookAngle().scale(2.0))), EntitySpawnReason.COMMAND);
        goblin.setNoAi(true);
        return goblin;
    }

    private static void customer(ServerPlayer p) {
        HeritageAPI.commit(p, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
        p.setData(ModAttachments.LOCATION_STANDING.get(), PlayerLocationStanding.EMPTY);
        p.getData(ModAttachments.VAULT_DATA.get()).credit(3_000);
        ItemStack goblet = new ItemStack(CurrencyItemRegistry.ANCIENT_GOBLET.get());
        TreasureArtefacts.set(goblet, new TreasureArtefact(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                "desert_tomb"), Optional.of(CurseKind.FLAGRANTE), 2, true, 640));
        p.setItemInHand(InteractionHand.OFF_HAND, goblet);
        LocationProgression.openAtHost(p, teller(p));
    }

    private static void associate(ServerPlayer p) {
        long day = SpellPractice.day(p.level().getGameTime());
        var record = LocationProgression.recordOf(p, GRINGOTTS).withXp(1240).withRank(3)
                .withActive(List.of(ActiveContract.taken("ancient_city_hoard", day),
                        ActiveContract.taken("director_diamonds", day)));
        p.setData(ModAttachments.LOCATION_STANDING.get(),
                p.getData(ModAttachments.LOCATION_STANDING.get()).with(GRINGOTTS.toString(), record));
        LocationProgression.openAtHost(p, teller(p));
    }

    private static void wandmaker(ServerPlayer p) {
        ServerLevel level = p.level();
        Villager villager = EntityType.VILLAGER.spawn(level,
                BlockPos.containing(p.position().add(p.getLookAngle().scale(2.0))), EntitySpawnReason.COMMAND);
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(),
                ModVillager.WANDMAKER.getKey()));
        LocationProgression.openAtHost(p, villager);
    }

    /** Scrolls the open board on the client thread (a step runs on the server). */
    private static void scrollBoard(double amount) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen != null) {
                mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, 0, amount);
            }
        });
    }

    /** The apothecary's board with goods in hand to sell: the market section's Buying and For sale lines. */
    private static void apothecary(ServerPlayer p) {
        ServerLevel level = p.level();
        p.getInventory().add(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "dittany")), 12));
        p.getInventory().add(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "bezoar")), 2));
        Villager villager = EntityType.VILLAGER.spawn(level,
                BlockPos.containing(p.position().add(p.getLookAngle().scale(2.0))), EntitySpawnReason.COMMAND);
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(),
                ModVillager.APOTHECARY.getKey()));
        LocationProgression.openAtHost(p, villager);
    }

    /** The fence's board (the Ministry module switched on for the shot, cache only): illicit goods marked. */
    private static void fence(ServerPlayer p) {
        at.koopro.wizardsandbeasts.module.ModuleManager.overrideCached(at.koopro.wizardsandbeasts.module.Module.MINISTRY,
                at.koopro.wizardsandbeasts.module.ModuleState.ENABLED);
        ServerLevel level = p.level();
        var trader = EntityType.WANDERING_TRADER.spawn(level,
                BlockPos.containing(p.position().add(p.getLookAngle().scale(2.0))), EntitySpawnReason.COMMAND);
        trader.setNoAi(true);
        LocationProgression.openAtHost(p, trader);
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
                System.getenv().getOrDefault("WB_LOCATION_SHOWCASE_WORLD", "wb_equipment_capture"), () -> stage = 3);
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
        Path file = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "location_showcase"))
                .resolve(SCRIPT.get(step).shot() + ".png");
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Location showcase: cannot write {}", file, e);
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
            LOGGER.info("Location showcase: done");
            mc.stop();
            return;
        }
        begin(mc, server);
    }

    private static void begin(Minecraft mc, MinecraftServer server) {
        Step current = SCRIPT.get(step);
        wait = current.ticks();
        if (!current.shot().isEmpty()) {
            return; // the board the server opened stays up for the shot
        }
        if (!current.keepScreen()) {
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
