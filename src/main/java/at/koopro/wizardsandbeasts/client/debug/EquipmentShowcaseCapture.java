package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.character.CharacterSheetScreen;
import at.koopro.wizardsandbeasts.equipment.EquipmentAPI;
import at.koopro.wizardsandbeasts.item.armor.RobeHood;
import at.koopro.wizardsandbeasts.registry.ArmorItemRegistry;
import at.koopro.wizardsandbeasts.registry.DarkArtefactItemRegistry;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
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
 * Photographs the local player in a series of outfits — vanilla armour, robes, hood, cloak, hat, mask,
 * necklace, the Deathly Hallow — from every side, sneaking, walking, in first person, in the inventory
 * preview and on the Character Sheet.
 *
 * <p>Armour hiding and Curios-drawn GeckoLib clothing are only judged by looking at them: clipping,
 * skin gaps, floating pieces and z-fighting have no assertion. This drives the real client through
 * the real equipment path ({@link EquipmentAPI} on the server, Curios sync, the render-state modifier,
 * {@code WornGeoCurioRenderer}) so the pictures show what a player would see.
 *
 * <p>Development only, inert unless asked for: set {@code WB_EQUIPMENT_SHOWCASE=1}.
 * {@code WB_EQUIPMENT_SHOWCASE_WORLD} names a disposable save that must already exist (default
 * {@code wb_equipment_capture}); {@code WB_EQUIPMENT_SHOWCASE_OUT} the folder under
 * {@code screenshots/} (default {@code equipment_showcase}). Shots are {@code <outfit>__<view>.png};
 * the client quits after the last one and logs which player model (slim/classic) it drew.
 *
 * <p>{@code WB_EQUIPMENT_SHOWCASE=cast} photographs the cast poses instead: a robed wizard with a wand, each
 * cast phase parked by the same {@code SpellCastAnimationS2CPayload} a real cast sends, from three sides and in
 * first person, plus a strike caught mid-release from a timed cast.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class EquipmentShowcaseCapture {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final boolean ENABLED =
            System.getenv("WB_EQUIPMENT_SHOWCASE") != null && !FMLEnvironment.isProduction();
    private static final boolean CAST = "cast".equals(System.getenv("WB_EQUIPMENT_SHOWCASE"));
    private static final int LOAD_TICKS = 80;
    private static final int STAGE_Y = 200;
    private static final double START_X = -10.5;

    private enum Stage { IDLE, OPENING, LOADING, RUNNING, DONE }

    /** Where the camera sits relative to an east-facing player; SELF is the player's own eyes. */
    private enum Cam { QUARTER, FRONT, SIDE, BACK, SELF, SCREEN }

    private record Outfit(String name, Consumer<ServerPlayer> dress) {}

    private record Step(String name, int ticks, boolean shoot, Cam cam,
                        Consumer<LocalPlayer> client, Consumer<ServerPlayer> server) {}

    private static final List<Outfit> OUTFITS = List.of(
            new Outfit("armor_only", p -> netherite(p, true, true, true, true)),
            new Outfit("armor_under_robe", p -> {
                netherite(p, true, true, true, true);
                wear(p, new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_CHEST.get()));
            }),
            new Outfit("robe_hood_up", p -> {
                netherite(p, true, true, true, true);
                ItemStack robe = new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_CHEST.get());
                RobeHood.setUp(robe, true);
                wear(p, robe);
            }),
            new Outfit("student_set_hat", p -> {
                p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_LEGS.get()));
                p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_BOOTS.get()));
                wear(p, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_CHEST.get()));
                wear(p, new ItemStack(ArmorItemRegistry.WIZARD_HAT.get()));
            }),
            new Outfit("auror_mask_over_helmet", p -> {
                p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ArmorItemRegistry.AUROR_ROBE_LEGS.get()));
                p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ArmorItemRegistry.AUROR_ROBE_BOOTS.get()));
                wear(p, new ItemStack(ArmorItemRegistry.AUROR_ROBE_CHEST.get()));
                wear(p, new ItemStack(ArmorItemRegistry.DEATH_EATER_MASK_3.get()));
            }),
            new Outfit("full_outfit", p -> {
                netherite(p, true, true, false, false);
                p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_LEGS.get()));
                p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_BOOTS.get()));
                wear(p, new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_CHEST.get()));
                wear(p, new ItemStack(DarkArtefactItemRegistry.INVISIBILITY_CLOAK.get()));
                wear(p, new ItemStack(ArmorItemRegistry.WIZARD_HAT.get()));
                wear(p, new ItemStack(ArmorItemRegistry.DEATH_EATER_MASK_1.get()));
                wear(p, new ItemStack(TrinketItemRegistry.TIME_TURNER.get()));
            }),
            new Outfit("deathly_hallow", p -> {
                netherite(p, true, true, true, true);
                wear(p, new ItemStack(DarkArtefactItemRegistry.DEATHLY_HALLOW_CLOAK.get()));
            }));

    /** The cast mode's one subject: a student with an ash wand, so the arm reads against plain cloth. */
    private static final List<Outfit> CAST_OUTFITS = List.of(new Outfit("cast", p -> {
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_LEGS.get()));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_BOOTS.get()));
        wear(p, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_CHEST.get()));
        ItemStack wand = new ItemStack(at.koopro.wizardsandbeasts.registry.WandItemRegistry.WAND.get());
        wand.set(at.koopro.wizardsandbeasts.wand.WandComponents.WAND_WOOD.get(),
                net.minecraft.resources.Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "ash"));
        p.setItemSlot(EquipmentSlot.MAINHAND, wand);
    }));

    private static List<Outfit> outfits() {
        return CAST ? CAST_OUTFITS : OUTFITS;
    }

    private static final List<Step> SCRIPT = new ArrayList<>();
    private static Stage stage = Stage.IDLE;
    private static int wait;
    private static int step;
    private static int outfit;
    private static boolean opened;
    private static boolean captureThisFrame;
    private static boolean forward;
    private static boolean sneak;
    private static ArmorStand camera;

    private EquipmentShowcaseCapture() {}

    private static Path out() {
        return Path.of(Screenshot.SCREENSHOT_DIR,
                System.getenv().getOrDefault("WB_EQUIPMENT_SHOWCASE_OUT", "equipment_showcase"));
    }

    private static void netherite(ServerPlayer p, boolean head, boolean chest, boolean legs, boolean feet) {
        if (head) p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        if (chest) p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        if (legs) p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        if (feet) p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
    }

    private static void wear(ServerPlayer p, ItemStack stack) {
        if (!EquipmentAPI.tryEquip(p, stack)) {
            LOGGER.warn("Equipment showcase: could not wear {}", stack);
        }
    }

    private static void undress(ServerPlayer p) {
        EquipmentAPI.stripWearables(p, s -> false);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            p.setItemSlot(slot, ItemStack.EMPTY);
        }
        p.setInvisible(false);
    }

    private static final Consumer<LocalPlayer> NONE = p -> {};
    private static final Consumer<ServerPlayer> NOTHING = p -> {};

    private static void buildScript(Outfit current) {
        SCRIPT.clear();
        if (CAST) {
            buildCastScript(current);
            return;
        }
        SCRIPT.add(new Step("dress", 30, false, Cam.QUARTER, p -> moves(false, false), p -> {
            undress(p);
            p.teleportTo(p.level(), START_X, STAGE_Y, 0.5, Set.of(), -90.0F, 0.0F, true);
            current.dress().accept(p);
        }));
        SCRIPT.add(new Step("quarter", 4, true, Cam.QUARTER, NONE, NOTHING));
        SCRIPT.add(new Step("front", 4, true, Cam.FRONT, NONE, NOTHING));
        SCRIPT.add(new Step("side", 4, true, Cam.SIDE, NONE, NOTHING));
        SCRIPT.add(new Step("back", 4, true, Cam.BACK, NONE, NOTHING));
        SCRIPT.add(new Step("sneak_quarter", 10, true, Cam.QUARTER, p -> moves(false, true), NOTHING));
        SCRIPT.add(new Step("walk_side", 12, true, Cam.SIDE, p -> moves(true, false), NOTHING));
        SCRIPT.add(new Step("stop", 6, false, Cam.SIDE, p -> moves(false, false), NOTHING));
        SCRIPT.add(new Step("first_person", 6, true, Cam.SELF, NONE, NOTHING));
        SCRIPT.add(new Step("inventory", 8, true, Cam.SCREEN,
                p -> Minecraft.getInstance().setScreen(new InventoryScreen(p)), NOTHING));
        SCRIPT.add(new Step("character_sheet", 10, true, Cam.SCREEN,
                p -> Minecraft.getInstance().setScreen(new CharacterSheetScreen()), NOTHING));
        SCRIPT.add(new Step("close", 2, false, Cam.QUARTER, p -> Minecraft.getInstance().setScreen(null), NOTHING));
    }

    /** A cast parked on a phase (-1: none), sent exactly as the server sends a real one. */
    private static Consumer<ServerPlayer> castPhase(int phase) {
        return p -> at.koopro.wizardsandbeasts.network.spell.SpellCastAnimationS2CPayload.send(p,
                new at.koopro.wizardsandbeasts.network.spell.SpellCastAnimationS2CPayload(p.getId(),
                        "wizards_and_beasts:capture", phase < 0 ? 0 : 1, 0.35f, 0.6f, phase));
    }

    private static void buildCastScript(Outfit current) {
        SCRIPT.add(new Step("dress", 30, false, Cam.QUARTER, p -> moves(false, false), p -> {
            // The stage world hurt the subject (red flash, damage hearts in every shot): nothing here is about damage.
            p.getAbilities().invulnerable = true;
            p.onUpdateAbilities();
            p.setHealth(p.getMaxHealth());
            p.removeAllEffects();
            undress(p);
            p.teleportTo(p.level(), START_X, STAGE_Y, 0.5, Set.of(), -90.0F, 0.0F, true);
            current.dress().accept(p);
        }));
        String[] phases = {"rest", "windup", "channel", "recovery"};
        for (int i = 0; i < phases.length; i++) {
            String phase = phases[i];
            SCRIPT.add(new Step(phase + "_set", 6, false, Cam.QUARTER, NONE, castPhase(i - 1)));
            SCRIPT.add(new Step(phase + "_quarter", 4, true, Cam.QUARTER, NONE, NOTHING));
            SCRIPT.add(new Step(phase + "_side", 4, true, Cam.SIDE, NONE, NOTHING));
            SCRIPT.add(new Step(phase + "_front", 4, true, Cam.FRONT, NONE, NOTHING));
            SCRIPT.add(new Step(phase + "_first_person", 6, true, Cam.SELF, NONE, NOTHING));
        }
        // Wingardium Leviosa's swish (held, as its channel holds it) and flick, parked under its own spell id.
        for (int i = 1; i <= 2; i++) {
            int held = i;
            SCRIPT.add(new Step("leviosa_" + (i == 1 ? "swish" : "flick") + "_set", 6, false, Cam.QUARTER, NONE, p ->
                    at.koopro.wizardsandbeasts.network.spell.SpellCastAnimationS2CPayload.send(p,
                            new at.koopro.wizardsandbeasts.network.spell.SpellCastAnimationS2CPayload(p.getId(),
                                    "wizards_and_beasts:wingardium_leviosa", 1, 0.35f, 0.6f, held))));
            SCRIPT.add(new Step("leviosa_" + (i == 1 ? "swish" : "flick") + "_front", 4, true, Cam.FRONT, NONE, NOTHING));
            SCRIPT.add(new Step("leviosa_" + (i == 1 ? "swish" : "flick") + "_quarter", 4, true, Cam.QUARTER, NONE, NOTHING));
        }
        // A strike caught in motion: a 40-tick cast, shot just after the release window opens.
        SCRIPT.add(new Step("strike_set", 15, false, Cam.SIDE, NONE, p ->
                at.koopro.wizardsandbeasts.network.spell.SpellCastAnimationS2CPayload.send(p,
                        new at.koopro.wizardsandbeasts.network.spell.SpellCastAnimationS2CPayload(p.getId(),
                                "wizards_and_beasts:capture", 40, 0.35f, 0.6f, -1))));
        SCRIPT.add(new Step("strike_side", 1, true, Cam.SIDE, NONE, NOTHING));

        // A real cast through the gameplay path, not a parked pose: the use key held and let go, so the client sends
        // the real use and release packets and the server's own cast moments send every pose (CastAnimationSync).
        // (Starting the hold on the server alone made the client let go at once: vanilla releases a use whose key
        // is not down.)
        SCRIPT.add(new Step("real_ready", 10, false, Cam.SIDE, NONE, EquipmentShowcaseCapture::readyRealCaster));
        SCRIPT.add(new Step("real_hold_set", 8, false, Cam.SIDE,
                p -> Minecraft.getInstance().options.keyUse.setDown(true), NOTHING));
        SCRIPT.add(new Step("real_hold_side", 2, true, Cam.SIDE, EquipmentShowcaseCapture::logClientCast, p ->
                LOGGER.info("Cast showcase: server hold: usingWand={} session={}",
                        at.koopro.wizardsandbeasts.item.wand.WandItem.isUsingWand(p),
                        at.koopro.wizardsandbeasts.spell.cast.WandCastSessions.peek(p) != null)));
        SCRIPT.add(new Step("real_hold_quarter", 2, true, Cam.QUARTER, NONE, NOTHING));
        SCRIPT.add(new Step("real_release_set", 3, false, Cam.SIDE,
                p -> Minecraft.getInstance().options.keyUse.setDown(false), NOTHING));
        SCRIPT.add(new Step("real_strike_side", 1, true, Cam.SIDE, EquipmentShowcaseCapture::logClientCast, p -> {
            var data = p.getData(at.koopro.wizardsandbeasts.registry.ModAttachments.SPELL_DATA.get());
            LOGGER.info("Cast showcase: real cast of {} {}", REAL_SPELL,
                    data.isOnCooldown(realSpellId(), p.level().getGameTime()) ? "SUCCEEDED (on cooldown)" : "did NOT cast");
        }));
        SCRIPT.add(new Step("real_settle_wait", 4, false, Cam.SIDE, NONE, NOTHING));
        SCRIPT.add(new Step("real_settle_side", 1, true, Cam.SIDE, NONE, NOTHING));
    }

    private static void logClientCast(LocalPlayer player) {
        long now = at.koopro.wizardsandbeasts.client.pose.ClientCastAnimationState.clientTick();
        var cast = at.koopro.wizardsandbeasts.client.pose.ClientCastAnimationState.get(player.getId(), now);
        LOGGER.info("Cast showcase: client cast state: {}", cast == null ? "none" : cast);
    }

    /** The lifecycle game tests' own spell, known to cast through this path ({@code WandCastLifecycleTests}). */
    private static final String REAL_SPELL = "arresto_momentum";

    private static String realSpellId() {
        var spell = at.koopro.wizardsandbeasts.spell.core.Spells.byId(REAL_SPELL);
        return spell == null ? REAL_SPELL : spell.getId();
    }

    private static void readyRealCaster(ServerPlayer p) {
        at.koopro.wizardsandbeasts.gametest.WizardTestSupport.makeWandkind(p);
        at.koopro.wizardsandbeasts.gametest.WizardTestSupport.giveBondedWand(p);
        at.koopro.wizardsandbeasts.gametest.WizardTestSupport.settleAllegiance(p);
        var data = p.getData(at.koopro.wizardsandbeasts.registry.ModAttachments.SPELL_DATA.get());
        data.learnSpell(realSpellId());
        var prerequisite = at.koopro.wizardsandbeasts.spell.core.Spells.byId("wingardium_leviosa");
        if (prerequisite != null) {
            data.learnSpell(prerequisite.getId());
        }
        data.setLoadoutSpell(0, realSpellId());
        data.setActiveSlot(0);
        data.setCooldown(realSpellId(), 0L);
        data.setGlobalCooldownEndTick(0L);
    }

    private static void moves(boolean forward, boolean sneak) {
        EquipmentShowcaseCapture.forward = forward;
        EquipmentShowcaseCapture.sneak = sneak;
        Minecraft.getInstance().options.keyShift.setDown(sneak);
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!ENABLED || stage != Stage.RUNNING || player == null) {
            return;
        }
        player.setYRot(-90.0F);
        player.setYHeadRot(-90.0F);
        player.setYBodyRot(-90.0F);
        player.setXRot(0.0F);
        player.zza = forward ? 1.0F : 0.0F;
        player.xxa = 0.0F;
        player.input.keyPresses = new Input(forward, false, false, false, false, sneak, false);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ENABLED || opened || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        opened = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.bobView().set(false);
        mc.options.hideGui = true;
        mc.options.particles().set(net.minecraft.server.level.ParticleStatus.MINIMAL);
        mc.debugEntries.setStatus(net.minecraft.client.gui.components.debug.DebugScreenEntries.ENTITY_HITBOXES,
                net.minecraft.client.gui.components.debug.DebugScreenEntryStatus.NEVER);
        GLFW.glfwSetWindowSize(mc.getWindow().handle(), 1600, 900);
        stage = Stage.OPENING;
        String world = System.getenv().getOrDefault("WB_EQUIPMENT_SHOWCASE_WORLD", "wb_equipment_capture");
        LOGGER.info("Equipment showcase: {} outfits into {}", outfits().size(), out());
        mc.createWorldOpenFlows().openWorld(world, () -> stage = Stage.DONE);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED || stage == Stage.IDLE || stage == Stage.DONE) {
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
        if (stage == Stage.RUNNING) {
            follow(mc.player, SCRIPT.get(step).cam());
        }
        if (captureThisFrame || --wait > 0) {
            return;
        }
        if (stage == Stage.LOADING) {
            stage = Stage.RUNNING;
            LOGGER.info("Equipment showcase: player model is {}", mc.player.getSkin().model());
            outfit = 0;
            step = 0;
            buildScript(outfits().getFirst());
            begin(mc, server);
            return;
        }
        Step current = SCRIPT.get(step);
        if (current.shoot()) {
            mc.gui.getChat().clearMessages(false);
            mc.getToastManager().clear();
            mc.options.hideGui = current.cam() != Cam.SELF;
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
        String name = outfits().get(outfit).name() + "__" + SCRIPT.get(step).name() + ".png";
        Path file = mc.gameDirectory.toPath().resolve(out()).resolve(name);
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Equipment showcase: cannot write {}", file, e);
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
            step = 0;
            outfit++;
            if (outfit >= outfits().size()) {
                stage = Stage.DONE;
                moves(false, false);
                LOGGER.info("Equipment showcase: done");
                mc.stop();
                return;
            }
            buildScript(outfits().get(outfit));
        }
        begin(mc, server);
    }

    private static void begin(Minecraft mc, MinecraftServer server) {
        Step current = SCRIPT.get(step);
        wait = current.ticks();
        if (mc.player != null) {
            current.client().accept(mc.player);
        }
        var uuid = mc.player != null ? mc.player.getUUID() : null;
        server.execute(() -> {
            ServerPlayer player = uuid != null ? server.getPlayerList().getPlayer(uuid) : null;
            if (player != null) {
                current.server().accept(player);
            }
        });
    }

    private static void follow(LocalPlayer player, Cam cam) {
        Minecraft mc = Minecraft.getInstance();
        if (cam == Cam.SELF || cam == Cam.SCREEN) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (mc.getCameraEntity() != player) {
                mc.setCameraEntity(player);
            }
            return;
        }
        if (camera == null || camera.level() != mc.level) {
            camera = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
            camera.setInvisible(true);
        }
        double distance = 2.1;
        double angle = switch (cam) {
            case SIDE -> 0.0;
            case FRONT -> 90.0;
            case BACK -> 270.0;
            default -> 40.0;
        };
        double rad = Math.toRadians(angle);
        double camX = player.getX() + Math.sin(rad) * distance;
        double camZ = player.getZ() + Math.cos(rad) * distance;
        double lookY = player.getY() + 1.0;
        double camY = lookY + 0.25;
        double dx = player.getX() - camX;
        double dz = player.getZ() - camZ;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(lookY - camY, Math.sqrt(dx * dx + dz * dz)));
        camera.xo = camera.getX();
        camera.yo = camera.getY();
        camera.zo = camera.getZ();
        camera.yRotO = camera.getYRot();
        camera.xRotO = camera.getXRot();
        camera.setPos(camX, camY - camera.getEyeHeight(), camZ);
        camera.setYRot(yaw);
        camera.setXRot(Mth.clamp(pitch, -89.0F, 89.0F));
        camera.setYHeadRot(yaw);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (mc.getCameraEntity() != camera) {
            mc.setCameraEntity(camera);
            camera.xo = camX;
            camera.yo = camY - camera.getEyeHeight();
            camera.zo = camZ;
        }
    }

    /** A stone platform in clear sky, noon, survival. */
    private static void buildStage(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        server.setDifficulty(Difficulty.PEACEFUL, true);
        level.setWeatherParameters(24000, 0, false, false);
        level.setDayTime(6000);
        player.setGameMode(GameType.SURVIVAL);
        for (int x = -16; x <= 16; x++) {
            for (int z = -8; z <= 8; z++) {
                level.setBlockAndUpdate(new BlockPos(x, STAGE_Y - 1, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = 0; y < 5; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, STAGE_Y + y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        player.teleportTo(level, START_X, STAGE_Y, 0.5, Set.of(), -90.0F, 0.0F, true);
    }
}
