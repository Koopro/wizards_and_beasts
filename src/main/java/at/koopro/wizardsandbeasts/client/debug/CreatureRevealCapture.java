package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
import java.util.List;
import java.util.Set;

/**
 * Photographs creatures that hide themselves: each is stood on a stone floor beside a villager for scale, its AI
 * stopped and its invisibility (camouflage, a Boggart's dread) stripped every tick, then shot front, three-quarter,
 * side and back by day and once at night.
 *
 * <p>Development only, inert unless {@code WB_REVEAL_CAPTURE} names a comma list of creature ids.
 * {@code WB_REVEAL_CAPTURE_WORLD} names a save that must already exist (default {@code wb_entity_showcase}).
 * Shots go to {@code screenshots/reveal/<id>__<view>.png}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class CreatureRevealCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MODE = System.getenv("WB_REVEAL_CAPTURE");
    private static final boolean ENABLED = MODE != null && !FMLEnvironment.isProduction();
    private static final int STAGE_Y = 240;

    /** What the server does to the creature before the view is shot: nothing, or one of its one-shot moments. */
    private enum Action { NONE, ATTACK, HIT, DEATH }

    private record View(String name, float camYaw, int dayTime, Action action, int settle) {}

    /**
     * Camera bearings round the creature (degrees, 0 = from the south), the time of day, and the one-shot clips —
     * attack, hit, death — caught a few ticks after the game event that plays them (the death last).
     */
    private static final List<View> VIEWS = List.of(
            new View("front", 0, 6000, Action.NONE, 12), new View("quarter", 40, 6000, Action.NONE, 12),
            new View("side", 90, 6000, Action.NONE, 12), new View("back", 180, 6000, Action.NONE, 12),
            new View("attack", 40, 6000, Action.ATTACK, 6), new View("hit", 40, 6000, Action.HIT, 4),
            // Night after the day's moments: the client's sky lags a set time by a few ticks, and a 4-tick hit shot
            // taken just after night came out dark.
            new View("night", 30, 18000, Action.NONE, 30), new View("death", 40, 6000, Action.DEATH, 14));

    private static final List<String> IDS = new ArrayList<>();
    private static int idIndex;
    private static int viewIndex = -1;
    private static int wait;
    private static int stage; // 0 idle, 1 loading, 2 running, 3 done
    private static boolean opened;
    private static boolean capture;
    private static ArmorStand camera;
    private static double subjectHeight = 1.8;
    /**
     * Cameras set inside a template, {@code x|y|z|yaw|pitch} in its own coordinates, used in turn by the views in place
     * of the orbit: a street is judged from the street, not from over the roofs. Empty for every other scene.
     */
    private static final List<double[]> templateCameras = new java.util.ArrayList<>();
    private static BlockPos templateBase = BlockPos.ZERO;

    private CreatureRevealCapture() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ENABLED || opened || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        opened = true;
        for (String raw : MODE.split(",")) {
            if (!raw.isBlank()) {
                IDS.add(raw.trim());
            }
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.hideGui = true;
        GLFW.glfwSetWindowSize(mc.getWindow().handle(), 1280, 720);
        stage = 1;
        wait = 80;
        mc.createWorldOpenFlows().openWorld(
                System.getenv().getOrDefault("WB_REVEAL_CAPTURE_WORLD", "wb_entity_showcase"), () -> stage = 3);
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
        if (mc.screen != null) {
            mc.setScreen(null);
        }
        server.execute(() -> keepVisible(server));
        if (stage == 2 && viewIndex >= 0) {
            follow(mc, VIEWS.get(viewIndex));
        }
        if (capture || --wait > 0) {
            return;
        }
        if (stage == 1) {
            stage = 2;
            server.execute(() -> buildStage(server));
            idIndex = 0;
            viewIndex = -1;
            next(mc, server);
            return;
        }
        capture = true;
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        if (!capture) {
            return;
        }
        capture = false;
        Minecraft mc = Minecraft.getInstance();
        String name = IDS.get(idIndex).replaceAll("!.*", "").replaceAll("[@:~=/]", "_") + "__" + VIEWS.get(viewIndex).name() + ".png";
        Path file = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "reveal")).resolve(name);
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Reveal capture: cannot write {}", file, e);
            }
        }));
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            next(mc, server);
        }
    }

    /** The next view, or the next creature, or the end. */
    private static void next(Minecraft mc, MinecraftServer server) {
        viewIndex++;
        if (viewIndex >= VIEWS.size()) {
            viewIndex = 0;
            idIndex++;
        }
        if (idIndex >= IDS.size()) {
            stage = 3;
            LOGGER.info("Reveal capture: done");
            mc.stop();
            return;
        }
        String id = IDS.get(idIndex);
        View view = VIEWS.get(viewIndex);
        boolean firstView = viewIndex == 0;
        // A scene is shot for the world's ambient particles, which need a few seconds to gather as they would in play.
        wait = firstView ? 40 : id.startsWith("scene:") ? Math.max(100, view.settle()) : view.settle();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
            ServerLevel level = p.level();
            level.setDayTime(view.dayTime());
            if (firstView) {
                spawn(level, id);
            }
            act(level, view.action());
        });
    }

    private static void buildStage(MinecraftServer server) {
        ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = p.level();
        server.setDifficulty(Difficulty.PEACEFUL, true);
        level.setWeatherParameters(24000, 0, false, false);
        p.setGameMode(GameType.SPECTATOR);
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                level.setBlock(new BlockPos(x, STAGE_Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        // Facing away from the stage: the Hidebehind is invisible to a player looking at it, and the camera here
        // is a stand, not the player's eyes.
        p.teleportTo(level, 0.5, STAGE_Y + 4, 10.5, Set.of(), 0.0F, 0.0F, true);
    }

    private static Entity subject;
    private static Entity scaleFigure;

    /** Plays one of the creature's one-shot moments through the game event that triggers it. */
    private static void act(ServerLevel level, Action action) {
        if (subject == null || !subject.isAlive()) {
            return;
        }
        switch (action) {
            case ATTACK -> {
                if (subject instanceof Mob mob && scaleFigure != null) {
                    mob.doHurtTarget(level, scaleFigure);
                }
            }
            case HIT -> subject.hurtServer(level, level.damageSources().generic(), 1.0F);
            case DEATH -> subject.kill(level);
            default -> { }
        }
    }

    /**
     * Clears the stage and stands the creature in the middle, with a villager beside it for scale. An id may carry an
     * age, {@code basilisk@0}, for a creature that grows.
     */
    private static void spawn(ServerLevel level, String spec) {
        templateCameras.clear();
        if (spec.startsWith("scene:template=")) {
            String rest = spec.substring("scene:template=".length());
            if (rest.contains("!")) {
                for (String cam : rest.substring(rest.indexOf('!') + 1).split(";")) {
                    templateCameras.add(java.util.Arrays.stream(cam.split("[|]")).mapToDouble(Double::parseDouble).toArray());
                }
                rest = rest.substring(0, rest.indexOf('!'));
            }
            template(level, rest);
            return;
        }
        if (spec.equals("scene:swamp")) {
            swamp(level);
            return;
        }
        if (spec.equals("scene:vault")) {
            vault(level);
            return;
        }
        if (spec.startsWith("scene:")) {
            meadow(level);
            return;
        }
        String id = spec.contains("@") ? spec.substring(0, spec.indexOf('@')) : spec;
        long age = spec.contains("@") ? Long.parseLong(spec.substring(spec.indexOf('@') + 1)) : -1;
        lawn(level);
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, id));
        Entity creature = type.create(level, EntitySpawnReason.COMMAND);
        if (creature == null) {
            LOGGER.warn("Reveal capture: cannot create {}", id);
            return;
        }
        creature.snapTo(0.5, STAGE_Y, 0.5, 0.0F, 0.0F);
        if (creature instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setYHeadRot(0.0F);
            mob.yBodyRot = 0.0F;
        }
        // A moon-bound werewolf would leave the daylit stage within two seconds.
        creature.addTag(at.koopro.wizardsandbeasts.creature.ability.MoonBound.CAPTURE_HOLD);
        level.addFreshEntity(creature);
        if (age >= 0 && creature instanceof at.koopro.wizardsandbeasts.entity.creature.BasiliskEntity basilisk) {
            basilisk.setAgeTicks(age);
        }
        subject = creature;
        subjectHeight = Math.max(1.0, Math.max(creature.getBbHeight(), creature.getBbWidth() * 0.6));
        Entity villager = EntityType.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        if (villager != null) {
            villager.snapTo(-0.5 - Math.max(1.5, creature.getBbWidth()), STAGE_Y, 0.5, 0.0F, 0.0F);
            if (villager instanceof Mob mob) {
                mob.setNoAi(true);
            }
            level.addFreshEntity(villager);
            scaleFigure = villager;
        }
        LOGGER.info("Reveal capture: {} spawned, {}x{}", spec, creature.getBbWidth(), creature.getBbHeight());
    }

    /**
     * {@code scene:vault}: a heap of gold on the lawn, gold blocks under coin heaps with gilt and a pair of Gringotts
     * scales beside it, for the glints that light catches off gold ({@code AmbientMagic}).
     */
    private static void vault(ServerLevel level) {
        lawn(level);
        net.minecraft.util.RandomSource random = level.random;
        var count = at.koopro.wizardsandbeasts.block.deco.DecoPileBlock.COUNT;
        Block[] coins = {at.koopro.wizardsandbeasts.registry.DecoBlocks.GALLEON_PILE.get(),
                at.koopro.wizardsandbeasts.registry.DecoBlocks.MIXED_COIN_PILE.get(),
                at.koopro.wizardsandbeasts.registry.DecoBlocks.GALLEON_STACKS.get()};
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                int height = Math.max(0, 3 - (Math.abs(x) + Math.abs(z) + 1) / 2);
                for (int y = 0; y < height; y++) {
                    Block block = random.nextFloat() < 0.15f ? Blocks.GILDED_BLACKSTONE : Blocks.GOLD_BLOCK;
                    level.setBlock(new BlockPos(x, STAGE_Y + y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                if (height > 0 || random.nextFloat() < 0.5f) {
                    Block pile = coins[random.nextInt(coins.length)];
                    level.setBlock(new BlockPos(x, STAGE_Y + height, z),
                            pile.defaultBlockState().setValue(count, 1 + random.nextInt(4)), Block.UPDATE_CLIENTS);
                }
            }
        }
        level.setBlock(new BlockPos(-6, STAGE_Y, 1),
                at.koopro.wizardsandbeasts.registry.DecoBlocks.GRINGOTTS_SCALES.get().defaultBlockState(),
                Block.UPDATE_CLIENTS);
        subject = null;
        subjectHeight = 1.4;   // close: the glints spawn round the camera, as they do round a player
        LOGGER.info("Reveal capture: vault heaped");
    }

    /** {@code scene:meadow}: the stage planted with grass and flowers, for the world's own night magic. */
    private static void meadow(ServerLevel level) {
        lawn(level);
        net.minecraft.util.RandomSource random = level.random;
        net.minecraft.world.level.block.Block[] flora = {Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.FERN,
                Blocks.POPPY, Blocks.DANDELION, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.AZURE_BLUET};
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                if (random.nextFloat() < 0.45f) {
                    level.setBlock(new BlockPos(x, STAGE_Y, z), flora[random.nextInt(flora.length)].defaultBlockState(),
                            Block.UPDATE_CLIENTS);
                }
            }
        }
        subject = null;
        subjectHeight = 2.0;
        LOGGER.info("Reveal capture: meadow planted");
    }

    /**
     * Resets the stage to a bare lawn: no entities, grass underfoot, air above, so a template or a flooded scene from an
     * earlier run cannot wall the camera in.
     */
    private static void lawn(ServerLevel level) {
        AABB box = new AABB(-24, STAGE_Y - 8, -24, 24, STAGE_Y + 34, 24);
        for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
            e.discard();
        }
        for (int x = -24; x <= 24; x++) {
            for (int z = -24; z <= 24; z++) {
                for (int y = STAGE_Y - 7; y <= STAGE_Y - 2; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                level.setBlock(new BlockPos(x, STAGE_Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = STAGE_Y; y <= STAGE_Y + 32; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /**
     * {@code scene:template=<id>}: a structure template (a hamlet house, say) stood on a cleared lawn in the middle of
     * the stage, its south side toward the front view, framed by its size. Ids may use {@code ~} for {@code :} so the
     * list stays comma-and-colon safe, e.g. {@code scene:template=wizards_and_beasts~wizarding_hamlet/wandmaker/a}. A
     * {@code @n} suffix sinks it n blocks, for a house on a cellar.
     */
    private static void template(ServerLevel level, String spec) {
        String id = spec.contains("@") ? spec.substring(0, spec.indexOf('@')) : spec;
        int sink = spec.contains("@") ? Integer.parseInt(spec.substring(spec.indexOf('@') + 1)) : 0;
        lawn(level);
        Identifier key = Identifier.parse(id.replace('~', ':'));
        var found = level.getStructureManager().get(key);
        if (found.isEmpty()) {
            LOGGER.warn("Reveal capture: no template {}", key);
            return;
        }
        var template = found.get();
        net.minecraft.core.Vec3i size = template.getSize();
        BlockPos base = new BlockPos(-size.getX() / 2, STAGE_Y - 1 - sink, -size.getZ() / 2);
        templateBase = base;
        template.placeInWorld(level, base, base, new net.minecraft.world.level.levelgen.structure.templatesystem
                .StructurePlaceSettings(), level.random, Block.UPDATE_CLIENTS);
        subject = null;
        subjectHeight = Math.max(size.getY(), Math.max(size.getX(), size.getZ()) * 0.8) * 0.6;   // fills the frame
        LOGGER.info("Reveal capture: template {} placed, {}", key, size);
    }

    /**
     * {@code scene:swamp}: the stage flooded into a pond with lily pads and its biome turned to swamp, for the bog wisps
     * that hang over swamp water at night. {@code fillbiome} sends the new biome to the client.
     */
    private static void swamp(ServerLevel level) {
        meadow(level);
        net.minecraft.util.RandomSource random = level.random;
        // Wider than the meadow: wisps appear up to 24 blocks out and only over water.
        for (int x = -22; x <= 22; x++) {
            for (int z = -22; z <= 22; z++) {
                if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                    continue;   // a tussock for the camera's subject point
                }
                level.setBlock(new BlockPos(x, STAGE_Y - 1, z), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(new BlockPos(x, STAGE_Y, z), random.nextFloat() < 0.12f
                        ? Blocks.LILY_PAD.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        MinecraftServer server = level.getServer();
        // Kept under the command's 32,768-block limit (a 65 x 29 x 65 box failed silently).
        try {
            int changed = server.getCommands().getDispatcher().execute(
                    "fillbiome -24 " + (STAGE_Y - 2) + " -24 24 " + (STAGE_Y + 4) + " 24 minecraft:swamp",
                    server.createCommandSourceStack().withSuppressedOutput());
            LOGGER.info("Reveal capture: fillbiome swamp -> {}", changed);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            LOGGER.warn("Reveal capture: fillbiome failed: {}", e.getMessage());
        }
        LOGGER.info("Reveal capture: swamp flooded");
    }

    /** Strips invisibility from every creature on the stage, every tick, so camouflage cannot hide it. */
    private static void keepVisible(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(-14, STAGE_Y - 2, -14, 14, STAGE_Y + 20, 14), e -> !(e instanceof Player))) {
            if (e.hasEffect(MobEffects.INVISIBILITY)) {
                e.removeEffect(MobEffects.INVISIBILITY);
            }
            e.setInvisible(false);
        }
    }

    private static void follow(Minecraft mc, View view) {
        if (camera == null || camera.level() != mc.level) {
            camera = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
            camera.setInvisible(true);
        }
        if (!templateCameras.isEmpty()) {
            double[] c = templateCameras.get(viewIndex % templateCameras.size());
            camera.setPos(templateBase.getX() + c[0], templateBase.getY() + c[1] - camera.getEyeHeight(),
                    templateBase.getZ() + c[2]);
            camera.setYRot((float) c[3]);
            camera.setXRot((float) c[4]);
            camera.setYHeadRot((float) c[3]);
            camera.xo = camera.getX();
            camera.yo = camera.getY();
            camera.zo = camera.getZ();
            camera.yRotO = (float) c[3];
            camera.xRotO = (float) c[4];
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (mc.getCameraEntity() != camera) {
                mc.setCameraEntity(camera);
            }
            return;
        }
        double distance = 3.0 + subjectHeight * 1.6;
        double rad = Math.toRadians(view.camYaw());
        double lookY = STAGE_Y + subjectHeight * 0.55;
        double ex = 0.5 + Math.sin(rad) * distance;
        double ez = 0.5 + Math.cos(rad) * distance;
        double ey = lookY + subjectHeight * 0.35 + 0.4;
        double dx = 0.5 - ex;
        double dz = 0.5 - ez;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(lookY - ey, Math.sqrt(dx * dx + dz * dz)));
        camera.setPos(ex, ey - camera.getEyeHeight(), ez);
        camera.setYRot(yaw);
        camera.setXRot(Mth.clamp(pitch, -89.0F, 89.0F));
        camera.setYHeadRot(yaw);
        camera.xo = camera.getX();
        camera.yo = camera.getY();
        camera.zo = camera.getZ();
        camera.yRotO = yaw;
        camera.xRotO = camera.getXRot();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (mc.getCameraEntity() != camera) {
            mc.setCameraEntity(camera);
        }
    }
}
