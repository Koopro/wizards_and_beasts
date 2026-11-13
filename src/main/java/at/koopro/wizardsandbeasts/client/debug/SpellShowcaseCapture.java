package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.gametest.WizardTestSupport;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Casts spells for real and photographs them, so a spell's look is judged in the world: the cast at the wand,
 * the bolt or beam in flight, the hit on a duelling dummy and what is left a second later.
 *
 * <p>Every cast goes through the gameplay path — the use key held and let go on the client, so the server's cast
 * session, release funnel, projectile, beam channel and impact packets all run as they do for a player. The stage
 * is a stone lane in clear sky: the caster at its west end facing east, a dummy ten blocks out, and behind it a
 * wall of four materials (stone brick, oak, iron, glass) with a pool in front, rebuilt before every spell so a
 * blasting curse or a flood cannot spoil the next one.
 *
 * <p>Development only, inert unless {@code WB_SPELL_SHOWCASE} is set: {@code 1} or {@code all} for every
 * implemented spell, or a comma list of ids. {@code WB_SPELL_SHOWCASE_WORLD} names a save that must already exist
 * (default {@code wb_entity_showcase}). Shots go to {@code screenshots/spell_showcase/<spell>__<shot>.png}; one
 * log line per spell says whether the server took the cast.
 */
@EventBusSubscriber(modid = at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class SpellShowcaseCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MODE = System.getenv("WB_SPELL_SHOWCASE");
    private static final boolean ENABLED = MODE != null && !FMLEnvironment.isProduction();

    private static final int LOAD_TICKS = 80;
    private static final int STAGE_Y = 200;
    /** The caster's feet, the dummy's and the wall's x; the lane runs east along z = 0.5. */
    private static final double CASTER_X = 0.5;
    private static final double DUMMY_X = 10.5;
    private static final int WALL_X = 16;

    /** WIDE sees the whole lane from the south; SHOULDER sits behind the caster; TARGET looks at the dummy. */
    private enum Cam { WIDE, SHOULDER, TARGET, CLOSE }

    private record Step(String shot, int ticks, Cam cam, Consumer<LocalPlayer> client, Consumer<ServerPlayer> server) {
        boolean shoots() {
            return !shot.isEmpty();
        }
    }

    private static final Consumer<LocalPlayer> NONE = p -> {};
    private static final Consumer<ServerPlayer> NOTHING = p -> {};

    private enum Stage { IDLE, LOADING, RUNNING, DONE }

    private static Stage stage = Stage.IDLE;
    private static boolean opened;
    private static int wait;
    private static int step;
    private static int spellIndex;
    private static boolean captureThisFrame;
    private static boolean holding;
    private static List<String> spells = List.of();
    private static final List<Step> SCRIPT = new ArrayList<>();
    private static ArmorStand camera;
    private static Cam lastCam;

    private SpellShowcaseCapture() {}

    private static List<String> chooseSpells() {
        if (!"1".equals(MODE) && !"all".equalsIgnoreCase(MODE)) {
            List<String> ids = new ArrayList<>();
            for (String raw : MODE.split(",")) {
                Spell spell = Spells.byId(raw.trim());
                if (spell == null) {
                    LOGGER.warn("Spell showcase: no spell '{}'", raw.trim());
                } else {
                    ids.add(spell.getId());
                }
            }
            return ids;
        }
        return Spells.all().stream()
                .filter(Spell::isImplemented)
                .sorted(Comparator.comparing(s -> s.getProperties().getCastType().name() + "/" + s.getId()))
                .map(Spell::getId)
                .toList();
    }

    private static String shotName(String spellId) {
        String id = spellId.contains(":") ? spellId.substring(spellId.indexOf(':') + 1) : spellId;
        return id.toLowerCase(Locale.ROOT);
    }

    /**
     * One spell's script. A beam is held while it channels and photographed in the hold; anything else is a quick
     * hold and release, photographed as it leaves the wand, in flight and on the hit.
     */
    private static void buildScript(String spellId) {
        SCRIPT.clear();
        Spell spell = Spells.byId(spellId);
        boolean beam = spell != null && spell.getProperties().getCastType().name().startsWith("BEAM");
        SCRIPT.add(new Step("", 24, Cam.WIDE, p -> {
            setUse(false);
            // Embers and smoke of the spell before must not stand in this one's shots.
            Minecraft.getInstance().particleEngine.clearParticles();
        }, p -> prepare(p, spellId)));
        SCRIPT.add(new Step("", 6, Cam.WIDE, p -> setUse(true), NOTHING));
        if (beam) {
            SCRIPT.add(new Step("hold_wide", 6, Cam.WIDE, NONE, NOTHING));
            SCRIPT.add(new Step("hold_shoulder", 4, Cam.SHOULDER, NONE, NOTHING));
            SCRIPT.add(new Step("hold_target", 4, Cam.TARGET, NONE, NOTHING));
            SCRIPT.add(new Step("", 1, Cam.WIDE, p -> setUse(false), NOTHING));
            SCRIPT.add(new Step("after_wide", 10, Cam.WIDE, NONE, p -> report(p, spellId)));
            return;
        }
        SCRIPT.add(new Step("hold_wide", 1, Cam.WIDE, NONE, NOTHING));
        SCRIPT.add(new Step("", 1, Cam.WIDE, p -> setUse(false), NOTHING));
        SCRIPT.add(new Step("release_shoulder", 2, Cam.SHOULDER, NONE, NOTHING));
        SCRIPT.add(new Step("flight_wide", 1, Cam.WIDE, NONE, NOTHING));
        SCRIPT.add(new Step("flight_close", 2, Cam.CLOSE, NONE, NOTHING));
        SCRIPT.add(new Step("arrive_wide", 3, Cam.WIDE, NONE, NOTHING));
        SCRIPT.add(new Step("hit_target", 3, Cam.TARGET, NONE, NOTHING));
        SCRIPT.add(new Step("after_wide", 20, Cam.WIDE, NONE, p -> report(p, spellId)));
    }

    private static void setUse(boolean down) {
        holding = down;
        Minecraft.getInstance().options.keyUse.setDown(down);
    }

    // ------------------------------------------------------------------ server side

    /** Rebuilds the lane, clears it of everything but the caster, stands a fresh dummy and readies the spell. */
    private static void prepare(ServerPlayer p, String spellId) {
        ServerLevel level = p.level();
        buildLane(level);
        AABB lane = new AABB(-8, STAGE_Y - 4, -12, WALL_X + 8, STAGE_Y + 16, 12);
        for (Entity e : level.getEntities((Entity) null, lane, e -> !(e instanceof Player))) {
            e.discard();
        }
        var dummy = ModEntities.DUELLING_DUMMY.get().create(level, EntitySpawnReason.COMMAND);
        if (dummy != null) {
            dummy.snapTo(DUMMY_X, STAGE_Y, 0.5, 90.0F, 0.0F);
            level.addFreshEntity(dummy);
        }
        p.setGameMode(GameType.SURVIVAL);
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        p.clearFire();
        p.setDeltaMovement(0, 0, 0);
        p.teleportTo(level, CASTER_X, STAGE_Y, 0.5, Set.of(), -90.0F, AIM_PITCH, true);
        if (!(p.getMainHandItem().getItem() instanceof at.koopro.wizardsandbeasts.item.wand.WandItem)) {
            WizardTestSupport.giveBondedWand(p);
        }
        WizardTestSupport.settleAllegiance(p);
        var data = p.getData(ModAttachments.SPELL_DATA.get());
        data.setLoadoutSpell(0, spellId);
        data.setActiveSlot(0);
        data.setCooldown(spellId, 0L);
        data.setGlobalCooldownEndTick(0L);
        castsBefore = data.getCastCount(spellId);
    }

    /** The spell's cast count when it was readied, so the report can tell whether the server took the cast. */
    private static int castsBefore;

    /** The caster looks just above the dummy's chest from eye height, ten blocks off. */
    private static final float AIM_PITCH = 3.0F;

    private static void report(ServerPlayer p, String spellId) {
        var data = p.getData(ModAttachments.SPELL_DATA.get());
        // The cast counter, not the cooldown: a short cooldown has run out by the time the aftermath is shot.
        boolean cast = data.getCastCount(spellId) > castsBefore || data.isOnCooldown(spellId, p.level().getGameTime());
        Spell spell = Spells.byId(spellId);
        LOGGER.info("Spell showcase: {} [{}] {}", spellId,
                spell == null ? "?" : spell.getProperties().getCastType(), cast ? "CAST" : "NOT CAST");
    }

    /** A stone lane, open sky above, a four-material wall at its end and a pool in front of the wall. */
    private static void buildLane(ServerLevel level) {
        for (int x = -8; x <= WALL_X + 6; x++) {
            for (int z = -10; z <= 10; z++) {
                level.setBlock(new BlockPos(x, STAGE_Y - 1, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(new BlockPos(x, STAGE_Y - 2, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = 0; y <= 10; y++) {
                    level.setBlock(new BlockPos(x, STAGE_Y + y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        Block[] materials = {Blocks.STONE_BRICKS, Blocks.OAK_PLANKS, Blocks.IRON_BLOCK, Blocks.GLASS};
        for (int z = -6; z <= 6; z++) {
            Block material = materials[Math.floorMod(z + 6, 13) * materials.length / 13];
            for (int y = 0; y <= 5; y++) {
                level.setBlock(new BlockPos(WALL_X, STAGE_Y + y, z), material.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        for (int x = WALL_X - 3; x <= WALL_X - 1; x++) {
            for (int z = 2; z <= 6; z++) {
                level.setBlock(new BlockPos(x, STAGE_Y - 1, z), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void buildStage(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        server.setDifficulty(Difficulty.PEACEFUL, true);
        level.setWeatherParameters(24000, 0, false, false);
        level.setDayTime(6000);
        buildLane(level);
        player.teleportTo(level, CASTER_X, STAGE_Y, 0.5, Set.of(), -90.0F, AIM_PITCH, true);
        WizardTestSupport.makeWandkind(player);
        WizardTestSupport.grantNewts(player);
        WizardTestSupport.giveBondedWand(player);
        WizardTestSupport.settleAllegiance(player);
        var data = player.getData(ModAttachments.SPELL_DATA.get());
        for (Spell spell : Spells.all()) {
            data.learnSpell(spell.getId());
        }
    }

    // ------------------------------------------------------------------ client side

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ENABLED || opened || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        opened = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.bobView().set(false);
        // Spell particles are the subject: every one of them, not the capped few other harnesses keep.
        mc.options.particles().set(net.minecraft.server.level.ParticleStatus.ALL);
        mc.debugEntries.setStatus(net.minecraft.client.gui.components.debug.DebugScreenEntries.ENTITY_HITBOXES,
                net.minecraft.client.gui.components.debug.DebugScreenEntryStatus.NEVER);
        GLFW.glfwSetWindowSize(mc.getWindow().handle(), 1280, 720);
        stage = Stage.LOADING;
        wait = -1;
        String world = System.getenv().getOrDefault("WB_SPELL_SHOWCASE_WORLD", "wb_entity_showcase");
        mc.createWorldOpenFlows().openWorld(world, () -> stage = Stage.DONE);
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!ENABLED || stage != Stage.RUNNING || player == null) {
            return;
        }
        player.setYRot(-90.0F);
        player.setYHeadRot(-90.0F);
        player.setYBodyRot(-90.0F);
        player.setXRot(AIM_PITCH);
        // Vanilla lets go of a use whose key is not down; keep it down for the whole hold.
        Minecraft.getInstance().options.keyUse.setDown(holding);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED || stage == Stage.IDLE || stage == Stage.DONE) {
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
        if (stage == Stage.LOADING && wait < 0) {
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
            spells = chooseSpells();
            LOGGER.info("Spell showcase: {} spells into {}", spells.size(), out());
            if (spells.isEmpty()) {
                stage = Stage.DONE;
                mc.stop();
                return;
            }
            stage = Stage.RUNNING;
            spellIndex = 0;
            step = 0;
            buildScript(spells.getFirst());
            begin(mc, server);
            return;
        }
        if (SCRIPT.get(step).shoots()) {
            mc.gui.getChat().clearMessages(false);
            mc.getToastManager().clear();
            mc.options.hideGui = true;
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
        String name = shotName(spells.get(spellIndex)) + "__" + SCRIPT.get(step).shot() + ".png";
        Path file = mc.gameDirectory.toPath().resolve(out()).resolve(name);
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Spell showcase: cannot write {}", file, e);
            }
        }));
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            advance(mc, server);
        }
    }

    private static Path out() {
        return Path.of(Screenshot.SCREENSHOT_DIR, "spell_showcase");
    }

    private static void advance(Minecraft mc, MinecraftServer server) {
        step++;
        if (step >= SCRIPT.size()) {
            step = 0;
            spellIndex++;
            if (spellIndex >= spells.size()) {
                stage = Stage.DONE;
                setUse(false);
                LOGGER.info("Spell showcase: done");
                mc.stop();
                return;
            }
            buildScript(spells.get(spellIndex));
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

    /** Puts the client's view on an invisible stand placed for the step's camera. */
    private static void follow(LocalPlayer player, Cam cam) {
        Minecraft mc = Minecraft.getInstance();
        if (camera == null || camera.level() != mc.level) {
            camera = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
            camera.setInvisible(true);
        }
        double y = STAGE_Y;
        double camX;
        double camY;
        double camZ;
        double lookX;
        double lookY;
        double lookZ;
        switch (cam) {
            case SHOULDER -> {
                camX = CASTER_X - 2.6;
                camY = y + 2.3;
                camZ = 1.6;
                lookX = DUMMY_X;
                lookY = y + 1.2;
                lookZ = 0.5;
            }
            case CLOSE -> {
                camX = (CASTER_X + DUMMY_X) / 2.0;
                camY = y + 1.9;
                camZ = 4.0;
                lookX = camX;
                lookY = y + 1.4;
                lookZ = 0.5;
            }
            case TARGET -> {
                camX = DUMMY_X - 2.5;
                camY = y + 1.8;
                camZ = 4.2;
                lookX = DUMMY_X;
                lookY = y + 1.1;
                lookZ = 0.5;
            }
            default -> {
                camX = (CASTER_X + DUMMY_X) / 2.0;
                camY = y + 2.2;
                camZ = 9.0;
                lookX = camX;
                lookY = y + 1.2;
                lookZ = 0.5;
            }
        }
        double dx = lookX - camX;
        double dy = lookY - camY;
        double dz = lookZ - camZ;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
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
        // A new camera position is a cut, not a pan: without the snap a shot one tick after a switch caught the
        // stand halfway between the two places.
        if (mc.getCameraEntity() != camera || cam != lastCam) {
            lastCam = cam;
            mc.setCameraEntity(camera);
            camera.xo = camX;
            camera.yo = camY - camera.getEyeHeight();
            camera.zo = camZ;
            camera.yRotO = yaw;
            camera.xRotO = camera.getXRot();
        }
    }
}
