package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
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
import java.util.Locale;
import java.util.Set;

/**
 * Photographs creatures the way a player meets them, so a rig can be judged by looking at it rather
 * than by reading its geo file.
 *
 * <p>The item counterpart is {@link ItemShowcaseCapture}; this is the same harness pointed at mobs.
 * A creature reads correctly only if its silhouette survives Minecraft distance, its size sits right
 * next to a person, its skin holds together from every side and its glow reads at night — none of
 * which a validator can see. Each creature is summoned alone on a floating stage with its AI off and
 * photographed from fixed views, so a whole roster lands on one contact sheet and a change can be
 * compared against the run before it.
 *
 * <p>Development only, and inert unless asked for. {@code WB_ENTITY_SHOWCASE} names a file of
 * creatures, one per line ({@code #} comments): {@code id [height [size]]}, where height and size
 * are the creature's drawn height and largest extent in blocks (they frame the camera; both default
 * to the hitbox). {@code WB_ENTITY_SHOWCASE_WORLD} names a disposable save, {@code
 * WB_ENTITY_SHOWCASE_OUT} the folder under {@code screenshots/}, and {@code WB_ENTITY_SHOWCASE_VIEWS}
 * the views to take, comma-separated, from {@code front, side, back, far, night, hurt, death, air, ashes,
 * reborn}
 * (default the first five). A villager stands beside the creature in {@code front} and {@code far}
 * as the yardstick. {@code hurt} damages the creature and shoots its hit reaction mid-clip; {@code
 * death} kills it and shoots the fall — both only mean anything for a rig that declares the clip.
 * {@code WB_ENTITY_SHOWCASE_FLAGS} ({@code +flag,-flag}) sets or clears the viewer's ability flags before the first
 * shot, e.g. {@code -witnessed_death} to photograph what someone who cannot see thestrals sees.
 * The client quits when the last shot is written.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class EntityShowcaseCapture {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int SETTLE_TICKS = 8;
    private static final int SPAWN_TICKS = 24;
    private static final int LOAD_TICKS = 80;
    private static final int STAGE_Y = 200;
    private static final String TAG = "wb_entity_showcase";

    /**
     * {@code air} lifts the creature two blocks off the stage so a flier plays its flight clip; {@code ashes}
     * and {@code reborn} shoot 40 and 130 ticks after a preceding {@code death} — what a creature that does not
     * stay dead (the phoenix) is doing by then. They damage nothing themselves.
     */
    private enum View { FRONT, SIDE, BACK, FAR, NIGHT, HURT, DEATH, AIR, ASHES, REBORN }

    private enum Stage { IDLE, OPENING, LOADING, RUNNING, DONE }

    private static final Request REQUEST = Request.fromEnvironment();
    private static Stage stage = Stage.IDLE;
    private static int wait;
    private static int shot;
    private static boolean captureThisFrame;
    private static boolean opened;
    /** The creature on the stage, by network id, set on the server thread when it is spawned. */
    private static volatile int subjectId = -1;

    private EntityShowcaseCapture() {}

    /** One line of the list: the entity id and the size of what is drawn, for framing. */
    private record Entry(Identifier id, float height, float size) {
        static Entry parse(String line) {
            String[] parts = line.split("\\s+");
            String spec = parts[0].contains(":") ? parts[0] : WizardsAndBeastsMod.MODID + ":" + parts[0];
            float height = parts.length > 1 ? Float.parseFloat(parts[1]) : -1.0F;
            float size = parts.length > 2 ? Float.parseFloat(parts[2]) : -1.0F;
            return new Entry(Identifier.parse(spec), height, size);
        }
    }

    private record Request(List<Entry> entities, List<View> views, String world, Path out) {
        static Request fromEnvironment() {
            String list = System.getenv("WB_ENTITY_SHOWCASE");
            if (list == null || FMLEnvironment.isProduction()) {
                return null;
            }
            List<Entry> entities = new ArrayList<>();
            try {
                for (String line : Files.readAllLines(Path.of(list))) {
                    String entry = line.strip();
                    if (!entry.isEmpty() && !entry.startsWith("#")) {
                        entities.add(Entry.parse(entry));
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Entity showcase: cannot read {}", list, e);
                return null;
            }
            List<View> views = new ArrayList<>();
            String spec = System.getenv().getOrDefault("WB_ENTITY_SHOWCASE_VIEWS", "front,side,back,far,night");
            for (String name : spec.split(",")) {
                views.add(View.valueOf(name.strip().toUpperCase(Locale.ROOT)));
            }
            String world = System.getenv().getOrDefault("WB_ENTITY_SHOWCASE_WORLD", "wb_entity_showcase");
            String out = System.getenv().getOrDefault("WB_ENTITY_SHOWCASE_OUT", "entity_showcase");
            return new Request(entities, views, world, Path.of(Screenshot.SCREENSHOT_DIR, out));
        }

        int shots() {
            return entities.size() * views.size();
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (REQUEST == null || opened || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        opened = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.bobView().set(false);
        mc.options.hideGui = true;
        GLFW.glfwSetWindowSize(mc.getWindow().handle(), 1600, 900);
        stage = Stage.OPENING;
        LOGGER.info("Entity showcase: {} entities x {} views into {}",
                REQUEST.entities().size(), REQUEST.views().size(), REQUEST.out());
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
        mc.getToastManager().clear();
        mc.gui.getChat().clearMessages(false);
        // A mob with its AI off never runs `travel`, so nothing ever tells it that it is standing on the
        // stage and a flier plays its flight clip where it stands. The client copy is not simulated, so
        // setting the flag on it is exactly what the animation controllers read and changes nothing else.
        Entity shown = subjectId >= 0 ? mc.level.getEntity(subjectId) : null;
        if (shown != null && view() != View.AIR) {
            shown.setOnGround(true);
        }
        captureThisFrame = true;
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        if (!captureThisFrame) {
            return;
        }
        captureThisFrame = false;
        Minecraft mc = Minecraft.getInstance();
        Entry entry = entry();
        String name = entry.id().getPath().replace('/', '_') + "__" + view().name().toLowerCase(Locale.ROOT) + ".png";
        Path file = mc.gameDirectory.toPath().resolve(REQUEST.out()).resolve(name);
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Entity showcase: cannot write {}", file, e);
            }
        }));
        shot++;
        MinecraftServer server = mc.getSingleplayerServer();
        if (shot >= REQUEST.shots() || server == null) {
            stage = Stage.DONE;
            LOGGER.info("Entity showcase: done, {} shots", shot);
            mc.stop();
            return;
        }
        present(mc, server);
    }

    private static Entry entry() {
        return REQUEST.entities().get(shot / REQUEST.views().size());
    }

    private static View view() {
        return REQUEST.views().get(shot % REQUEST.views().size());
    }

    /** Spawns the creature on the first view of each entry, then places the camera for this view. */
    private static void present(Minecraft mc, MinecraftServer server) {
        Entry entry = entry();
        View view = view();
        boolean first = shot % REQUEST.views().size() == 0;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.hideGui = true;
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                return;
            }
            ServerLevel level = player.level();
            level.setDayTime(view == View.NIGHT ? 18000 : 6000);
            // The server only syncs time once a second; send it now so this view needs no wait.
            player.connection.send(new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), false));
            if (first) {
                spawn(level, entry);
            }
            Entity subject = subject(level);
            Entity villager = yardstick(level);
            float height = entry.height() > 0 ? entry.height() : subject != null ? subject.getBbHeight() : 1.0F;
            float size = Math.max(height, entry.size() > 0 ? entry.size()
                    : subject != null ? subject.getBbWidth() : 1.0F);
            // Beside the creature, clear of its widest point, facing the same way as it does.
            if (villager != null) {
                boolean shown = view == View.FRONT || view == View.FAR;
                villager.snapTo(-(size * 0.5 + 1.0), shown ? STAGE_Y : STAGE_Y - 40, 0.5, 0.0F, 0.0F);
            }
            if (subject instanceof Mob mob && view == View.HURT) {
                mob.hurtServer(level, level.damageSources().generic(), 0.5F);
            }
            if (subject != null && view == View.AIR) {
                subject.setNoGravity(true);
                subject.snapTo(0.5, STAGE_Y + 2.0, 0.5, 0.0F, 0.0F);
            }
            if (subject instanceof Mob mob && view == View.DEATH) {
                // Ordinary lethal damage, not /kill: a creature that does not stay dead (the phoenix) is
                // meant to show what it does instead, and /kill is the one thing it cannot survive.
                mob.invulnerableTime = 0;
                mob.hurtServer(level, level.damageSources().generic(), mob.getMaxHealth() * 10.0F);
            }
            // The creature faces south (+Z). Front views sit south-east of it, the back view north-west.
            double angle = switch (view) {
                case SIDE -> 90.0;
                case BACK -> 215.0;
                default -> 35.0;
            };
            double distance = view == View.FAR ? 20.0 : size * 0.95 + 1.2;
            double baseY = subject != null ? subject.getY() : STAGE_Y;
            double lookY = baseY + height * 0.5;
            double camY = lookY + (view == View.FAR ? 2.0 : height * 0.25 + 0.2);
            double rad = Math.toRadians(angle);
            double camX = 0.5 + Math.sin(rad) * distance;
            double camZ = 0.5 + Math.cos(rad) * distance;
            double dx = 0.5 - camX;
            double dz = 0.5 - camZ;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(lookY - camY, Math.sqrt(dx * dx + dz * dz)));
            player.teleportTo(level, camX, camY - player.getEyeHeight(), camZ, Set.of(), yaw,
                    Mth.clamp(pitch, -89.0F, 89.0F), true);
        });
        wait = switch (view) {
            case HURT -> 4;
            case DEATH -> 11;
            case AIR -> 20;
            case ASHES -> 40;
            case REBORN -> 130;
            default -> first ? SPAWN_TICKS : SETTLE_TICKS;
        };
    }

    private static void spawn(ServerLevel level, Entry entry) {
        AABB area = new AABB(-12, STAGE_Y - 4, -12, 12, STAGE_Y + 20, 12);
        level.getEntitiesOfClass(Entity.class, area, e -> e.getTags().contains(TAG) && !e.getTags().contains(TAG + "_ref"))
                .forEach(Entity::discard);
        // Loot and experience from the previous creature's death shot.
        level.getEntitiesOfClass(ItemEntity.class, area).forEach(Entity::discard);
        level.getEntitiesOfClass(ExperienceOrb.class, area).forEach(Entity::discard);
        // Anything the last creature left behind: offspring, a summoned double, a mob that
        // shrugged off its death shot. Only the yardstick villager stays.
        level.getEntitiesOfClass(Mob.class, area, e -> !e.getTags().contains(TAG + "_ref")).forEach(Entity::discard);
        // Explosive deaths (Erumpent, Blast-Ended Skrewt) crater the stage; lay it again.
        layFloor(level);
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(entry.id());
        Entity entity = type.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) {
            LOGGER.warn("Entity showcase: {} did not create", entry.id());
            return;
        }
        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(0.5, STAGE_Y, 0.5)),
                    EntitySpawnReason.COMMAND, null);
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }
        // Gravity on, so a creature standing on the stage is on the ground and plays its ground clips;
        // the `air` view lifts it and turns gravity off.
        entity.setNoGravity(false);
        entity.setSilent(true);
        entity.addTag(TAG);
        entity.snapTo(0.5, STAGE_Y, 0.5, 0.0F, 0.0F);
        entity.setYHeadRot(0.0F);
        entity.setYBodyRot(0.0F);
        level.addFreshEntity(entity);
        subjectId = entity.getId();
    }

    private static Entity subject(ServerLevel level) {
        AABB area = new AABB(-12, STAGE_Y - 4, -12, 12, STAGE_Y + 20, 12);
        return level.getEntitiesOfClass(Entity.class, area,
                e -> e.getTags().contains(TAG) && !e.getTags().contains(TAG + "_ref")).stream().findFirst().orElse(null);
    }

    private static Entity yardstick(ServerLevel level) {
        AABB area = new AABB(-40, STAGE_Y - 60, -40, 40, STAGE_Y + 20, 40);
        return level.getEntitiesOfClass(Entity.class, area, e -> e.getTags().contains(TAG + "_ref"))
                .stream().findFirst().orElse(null);
    }

    /** A plain light floor in open sky: dark creatures read against it, and nothing stands behind them. */
    private static void layFloor(ServerLevel level) {
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                for (int y = -4; y < 0; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, STAGE_Y + y, z), y == -1
                            ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                for (int y = 0; y < 16; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, STAGE_Y + y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** A stone stage in clear sky, and a villager with its AI off as the size reference. */
    private static void buildStage(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        server.setDifficulty(Difficulty.PEACEFUL, true);
        level.setWeatherParameters(24000, 0, false, false);
        // A flying creative camera, not a spectator: spectators see things players do not (a thestral to someone
        // who has not witnessed death), and the shots are meant to show what a player sees.
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        AABB area = new AABB(-40, STAGE_Y - 60, -40, 40, STAGE_Y + 20, 40);
        level.getEntitiesOfClass(Entity.class, area, e -> e.getTags().contains(TAG)
                || e instanceof ItemEntity || e instanceof ItemFrame).forEach(Entity::discard);
        layFloor(level);
        Entity villager = EntityType.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        if (villager instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
            mob.setSilent(true);
            mob.setNoGravity(true);
            mob.addTag(TAG);
            mob.addTag(TAG + "_ref");
            mob.snapTo(-2.0, STAGE_Y - 40, 0.5, 0.0F, 0.0F);
            level.addFreshEntity(mob);
        }
        player.teleportTo(level, 0.5, STAGE_Y + 2, 6.0, Set.of(), 180.0F, 10.0F, true);
        for (String flag : System.getenv().getOrDefault("WB_ENTITY_SHOWCASE_FLAGS", "").split(",")) {
            String name = flag.strip();
            if (name.length() > 1 && name.charAt(0) == '+') {
                PlayerAbilityHelper.addAbilityFlag(player, name.substring(1));
            } else if (name.length() > 1 && name.charAt(0) == '-') {
                PlayerAbilityHelper.removeAbilityFlag(player, name.substring(1));
            }
        }
    }
}
