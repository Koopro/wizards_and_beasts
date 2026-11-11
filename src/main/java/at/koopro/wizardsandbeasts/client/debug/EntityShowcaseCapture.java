package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.creature.profile.CreatureActivity;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.settlement.npc.SettlementNpcEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * creatures, one per line ({@code #} comments): {@code id [height [size]] [key=value …]}, where height and size
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
 *
 * <p><b>Actions (Creature Visual Renaissance).</b> {@code WB_ENTITY_SHOWCASE_ACTIONS} adds, after the views, a frame strip
 * per action from the front three-quarter camera: an activity name ({@code run}, {@code sleep}, {@code eat} ... from
 * {@code CreatureActivity}) sets the synced activity byte on the server copy, exactly what the ecology brain does,
 * so the shot shows the clip the game really plays; {@code walk} drives the client copy's walk cycle (an AI-off mob
 * never moves); {@code vanish}/{@code appear} add and remove invisibility (the fade); {@code look} turns the creature's synced head away from its body (the life pass's head turn);
 * {@code @clip} fires a declared one-shot through {@code GenericBeastEntity.triggerDeclared}, {@code @controller/clip}
 * any triggerable clip of any GeckoLib creature; {@code pace}, {@code fly} and {@code glide} move the client copy so
 * a creature that reads its own movement (the Phoenix) plays those loops.
 * {@code WB_ENTITY_SHOWCASE_STRIP} is {@code frames:interval} (default {@code 4:5}): how many shots per action and
 * how many ticks apart; {@code name*frames:interval} overrides it for one action. Files are {@code <stem>__<action>_f<n>.png}.
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
    private enum View {
        FRONT, SIDE, BACK, FAR, NIGHT, HURT, DEATH, AIR, ASHES, REBORN;

        /** Hurts or kills the subject, so it is shot after everything that needs it alive and calm. */
        boolean destructive() {
            return this == HURT || this == DEATH || this == ASHES || this == REBORN;
        }
    }

    private enum Stage { IDLE, OPENING, LOADING, RUNNING, DONE }

    private static final Request REQUEST = Request.fromEnvironment();
    private static Stage stage = Stage.IDLE;
    private static int wait;
    private static int shot;
    private static boolean captureThisFrame;
    private static boolean opened;
    /** The creature on the stage, by network id, set on the server thread when it is spawned. */
    private static volatile int subjectId = -1;
    /** Where the current view puts the camera player's feet; set on the server thread. */
    private static volatile double[] cameraTarget;
    private static int cameraWaits;

    private EntityShowcaseCapture() {}

    /**
     * One line of the list: the entity id, the size of what is drawn (for framing), and {@code key=value} words. A
     * settlement person takes {@code role=<id> person=<n>} (who they are, so their own skin is drawn) and {@code
     * with=<role>:<n>;…} (more people in a row beside them, for a group shot).
     */
    private record Entry(Identifier id, float height, float size, Map<String, String> props) {
        static Entry parse(String line) {
            String[] parts = line.split("\\s+");
            String spec = parts[0].contains(":") ? parts[0] : WizardsAndBeastsMod.MODID + ":" + parts[0];
            List<Float> numbers = new ArrayList<>();
            Map<String, String> props = new LinkedHashMap<>();
            for (int i = 1; i < parts.length; i++) {
                int eq = parts[i].indexOf('=');
                if (eq > 0) {
                    props.put(parts[i].substring(0, eq), parts[i].substring(eq + 1));
                } else {
                    numbers.add(Float.parseFloat(parts[i]));
                }
            }
            float height = numbers.size() > 0 ? numbers.get(0) : -1.0F;
            float size = numbers.size() > 1 ? numbers.get(1) : -1.0F;
            return new Entry(Identifier.parse(spec), height, size, props);
        }

        /** The file name's stem: the id, and who the person is when the line says. */
        String stem() {
            String stem = id.getPath().replace('/', '_') + (props.containsKey("age") ? "_age" + props.get("age") : "");
            if (props.containsKey("role")) {
                stem += "_" + props.get("role") + "_" + props.getOrDefault("person", "0");
            }
            return props.containsKey("with") ? stem + "_group" : stem;
        }
    }

    /** One photograph: a fixed view, or frame {@code frame} of an action strip. */
    private record Shot(int entry, View view, String action, int frame, int interval) {
        String suffix() {
            return action == null ? view.name().toLowerCase(Locale.ROOT)
                    : action.replace("@", "clip_").replace('/', '_') + "_f" + frame;
        }
    }

    private record Request(List<Entry> entities, List<View> views, List<String> actions, int frames, int interval,
                           String world, Path out) {
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
            List<String> actions = new ArrayList<>();
            for (String name : System.getenv().getOrDefault("WB_ENTITY_SHOWCASE_ACTIONS", "").split(",")) {
                if (!name.isBlank()) {
                    actions.add(name.strip().toLowerCase(Locale.ROOT));
                }
            }
            String[] strip = System.getenv().getOrDefault("WB_ENTITY_SHOWCASE_STRIP", "4:5").split(":");
            String world = System.getenv().getOrDefault("WB_ENTITY_SHOWCASE_WORLD", "wb_entity_showcase");
            String out = System.getenv().getOrDefault("WB_ENTITY_SHOWCASE_OUT", "entity_showcase");
            return new Request(entities, views, actions, Integer.parseInt(strip[0]), Integer.parseInt(strip[1]),
                    world, Path.of(Screenshot.SCREENSHOT_DIR, out));
        }

        List<Shot> shots() {
            List<Shot> shots = new ArrayList<>();
            for (int e = 0; e < entities.size(); e++) {
                // Views that hurt or kill the subject go last, after the strips: a `death` before them
                // left every strip photographing an empty stage (2026-11-06).
                for (View view : views) {
                    if (!view.destructive()) {
                        shots.add(new Shot(e, view, null, 0, 0));
                    }
                }
                for (String spec : actions) {
                    // `name*frames:interval` overrides the strip for one action (a blink needs fine sampling).
                    String action = spec.contains("*") ? spec.substring(0, spec.indexOf('*')) : spec;
                    int count = spec.contains("*") ? Integer.parseInt(spec.substring(spec.indexOf('*') + 1).split(":")[0]) : frames;
                    int every = spec.contains("*") ? Integer.parseInt(spec.split(":")[1]) : interval;
                    for (int f = 0; f < count; f++) {
                        shots.add(new Shot(e, View.FRONT, action, f, every));
                    }
                }
                for (View view : views) {
                    if (view.destructive()) {
                        shots.add(new Shot(e, view, null, 0, 0));
                    }
                }
            }
            return shots;
        }
    }

    private static final List<Shot> SHOTS = REQUEST == null ? List.of() : REQUEST.shots();

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
        LOGGER.info("Entity showcase: {} entities, {} shots into {}",
                REQUEST.entities().size(), SHOTS.size(), REQUEST.out());
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
        // The camera is moved on the server thread. On a loaded machine the move can land after the fixed wait,
        // and the shot is the previous view again (seen 2026-11-06: side, back and far all came out as the front).
        // Hold the shot until the client player is where this view put it, for up to five seconds.
        double[] target = cameraTarget;
        if (stage == Stage.RUNNING && (target == null || mc.player.distanceToSqr(target[0], target[1], target[2]) > 0.25)
                && cameraWaits++ < 100) {
            return;
        }
        cameraWaits = 0;
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

    /**
     * Every tick, not only at the shot: keeps an AI-off subject on the ground (so its controller has blended out
     * of any flight clip by the time it is photographed; the first Phoenix view caught it mid-flight) and keeps its
     * walk cycle running for a {@code walk} strip.
     */
    @SubscribeEvent
    public static void onSubjectTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (REQUEST == null || stage != Stage.RUNNING || mc.level == null || shot >= SHOTS.size() || subjectId < 0) {
            return;
        }
        Entity shown = mc.level.getEntity(subjectId);
        String action = SHOTS.get(shot).action();
        boolean airborne = SHOTS.get(shot).view() == View.AIR || "fly".equals(action) || "glide".equals(action);
        if (shown == null || "fight".equals(action) || "struck".equals(action)) {
            return;   // nothing, or a creature moving by its own goals: leave the client copy alone
        }
        shown.setOnGround(!airborne);
        if ("walk".equals(action) && shown instanceof LivingEntity living) {
            living.walkAnimation.setSpeed(0.9F);
        }
        // A creature whose controller reads its own movement (the Phoenix) only walks, flies or glides when the
        // client copy moves. Move the copy, never the server one: a few hundredths a tick, back and forth, so it
        // stays in frame. `glide` also sinks, and goes back up every second.
        if (action != null && (action.equals("pace") || airborne)) {
            double baseY = STAGE_Y + (airborne ? 0.8 : 0.0);
            double wiggle = (shown.tickCount % 2 == 0 ? 0.04 : -0.04);
            double y = action.equals("glide") ? baseY + 0.6 - (shown.tickCount % 20) * 0.05 : baseY;
            shown.setOldPosAndRot();
            shown.setPos(0.5 + wiggle, y, 0.5 + (action.equals("glide") ? wiggle : 0.0));
        } else if (action != null) {
            // Any other action stands where the server put it: undo what a moving strip did to the client copy.
            shown.setOldPosAndRot();
            shown.setPos(0.5, STAGE_Y, 0.5);
        }
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        if (!captureThisFrame) {
            return;
        }
        captureThisFrame = false;
        Minecraft mc = Minecraft.getInstance();
        Entry entry = entry();
        String name = entry.stem() + "__" + SHOTS.get(shot).suffix() + ".png";
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
        if (shot >= SHOTS.size() || server == null) {
            stage = Stage.DONE;
            LOGGER.info("Entity showcase: done, {} shots", shot);
            mc.stop();
            return;
        }
        present(mc, server);
    }

    private static Entry entry() {
        return REQUEST.entities().get(SHOTS.get(shot).entry());
    }

    private static View view() {
        return SHOTS.get(shot).view();
    }

    /** Spawns the creature on the first view of each entry, then places the camera for this view. */
    private static void present(Minecraft mc, MinecraftServer server) {
        cameraTarget = null;
        Entry entry = entry();
        View view = view();
        Shot current = SHOTS.get(shot);
        boolean first = shot == 0 || SHOTS.get(shot - 1).entry() != current.entry();
        String action = current.action();
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
            if (action != null && current.frame() == 0 && subject instanceof GenericBeastEntity beast) {
                act(beast, action);
            } else if (action != null && current.frame() == 0 && action.startsWith("@") && action.contains("/")
                    && subject instanceof software.bernie.geckolib.animatable.GeoEntity geo) {
                // `@controller/clip`: any triggerable clip, for creatures with their own controllers (the Phoenix).
                String[] parts = action.substring(1).split("/", 2);
                geo.triggerAnim(parts[0], parts[1]);
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
            double baseY = subject != null && !entry.props().containsKey("fight") ? subject.getY() : STAGE_Y;
            double lookY = baseY + height * 0.5;
            double camY = lookY + (view == View.FAR ? 2.0 : height * 0.25 + 0.2);
            double rad = Math.toRadians(angle);
            double camX = 0.5 + Math.sin(rad) * distance;
            double camZ = 0.5 + Math.cos(rad) * distance;
            double dx = 0.5 - camX;
            double dz = 0.5 - camZ;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(lookY - camY, Math.sqrt(dx * dx + dz * dz)));
            cameraTarget = new double[] {camX, camY - player.getEyeHeight(), camZ};
            player.teleportTo(level, camX, camY - player.getEyeHeight(), camZ, Set.of(), yaw,
                    Mth.clamp(pitch, -89.0F, 89.0F), true);
        });
        if (action != null) {
            // The first frame waits for the controller to blend in (and lids or a sit to settle).
            boolean quick = action.startsWith("@") || action.equals("vanish") || action.equals("appear")
                    || action.equals("fight") || action.equals("struck");
            wait = current.frame() == 0 ? (quick ? 2 : 30) : current.interval();
            return;
        }
        wait = switch (view) {
            case HURT -> 4;
            case DEATH -> 11;
            case AIR -> 20;
            case ASHES -> 40;
            case REBORN -> 130;
            default -> first ? SPAWN_TICKS : SETTLE_TICKS;
        };
    }

    /** Puts the subject into one action: an activity, a one-shot, the walk cycle or a turned head. */
    private static void act(GenericBeastEntity beast, String action) {
        beast.setActivity(CreatureActivity.IDLE);
        beast.setYHeadRot(beast.getYRot());
        beast.setXRot(0.0F);
        if (action.startsWith("@")) {
            beast.triggerDeclared(action.substring(1));
        } else if (action.equals("vanish")) {
            beast.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 400, 0, false, false, false));
        } else if (action.equals("appear")) {
            beast.removeEffect(MobEffects.INVISIBILITY);
        } else if (action.equals("fight") || action.equals("struck")) {
            // AI on, against the foe the entry line staged (`fight=<entity> gap=<blocks>`): the strip that
            // follows is the creature fighting by its own goals, not a posed clip.
            if (beast.level() instanceof ServerLevel level && foe(level) instanceof net.minecraft.world.entity.LivingEntity foe) {
                beast.setNoAi(false);
                if (action.equals("fight")) {
                    beast.setTarget(foe);
                } else {
                    beast.invulnerableTime = 0;
                    beast.hurtServer(level, level.damageSources().mobAttack(foe), 1.0F);
                }
            }
        } else if (action.equals("look")) {
            beast.setYHeadRot(beast.getYRot() + 50.0F);
            beast.setXRot(-15.0F);
        } else if (!action.equals("walk") && !action.equals("idle")) {
            try {
                beast.setActivity(CreatureActivity.valueOf(action.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Entity showcase: no activity {}", action);
            }
        }
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
        // A subject that fought with its AI on may have walked anywhere; find it by tag, not by where it stood.
        List<Entity> strays = new ArrayList<>();
        level.getAllEntities().forEach(e -> {
            if (e.getTags().contains(TAG) && !e.getTags().contains(TAG + "_ref")) {
                strays.add(e);
            }
        });
        strays.forEach(Entity::discard);
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
        if (entity instanceof SettlementNpcEntity npc && entry.props().containsKey("role")) {
            dress(npc, entry.props().get("role"), entry.props().getOrDefault("person", "0"));
        }
        String with = entry.props().get("with");
        if (with != null) {
            // The rest of the group, shoulder to shoulder either side of the subject, all facing the same way.
            String[] others = with.split(";");
            for (int i = 0; i < others.length; i++) {
                String[] who = others[i].split(":");
                if (ModEntities.SETTLEMENT_NPC.get().create(level, EntitySpawnReason.COMMAND) instanceof SettlementNpcEntity npc) {
                    npc.setNoAi(true);
                    npc.setSilent(true);
                    npc.addTag(TAG);
                    npc.addTag(TAG + "_crowd");
                    dress(npc, who[0], who.length > 1 ? who[1] : "0");
                    npc.snapTo(0.5 + 1.1 * (i / 2 + 1) * (i % 2 == 0 ? 1 : -1), STAGE_Y, 0.5, 0.0F, 0.0F);
                    npc.setYHeadRot(0.0F);
                    npc.setYBodyRot(0.0F);
                    level.addFreshEntity(npc);
                }
            }
        }
        // Gravity on, so a creature standing on the stage is on the ground and plays its ground clips;
        // the `air` view lifts it and turns gravity off.
        entity.setNoGravity(false);
        entity.setSilent(true);
        entity.addTag(TAG);
        // A Demiguise vanishes when looked at straight on, which is all a camera does; `vanish`/`appear` stage it.
        entity.addTag(at.koopro.wizardsandbeasts.creature.ability.WatchedInvisibility.HOLD_VISIBLE_TAG);
        entity.snapTo(0.5, STAGE_Y, 0.5, 0.0F, 0.0F);
        entity.setYHeadRot(0.0F);
        entity.setYBodyRot(0.0F);
        String fight = entry.props().get("fight");
        if (fight != null) {
            // The foe stands at the centre, where the camera looks; the subject starts `gap` blocks west of it
            // and faces it. The foe never moves or fights back, so what is seen is the subject's own doing.
            float gap = Float.parseFloat(entry.props().getOrDefault("gap", "5"));
            entity.snapTo(0.5 - gap, STAGE_Y, 0.5, -90.0F, 0.0F);
            entity.setYHeadRot(-90.0F);
            entity.setYBodyRot(-90.0F);
            Entity foe = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(fight)).create(level, EntitySpawnReason.COMMAND);
            if (foe instanceof Mob mob) {
                mob.setNoAi(true);
                mob.setSilent(true);
                mob.setPersistenceRequired();
                mob.addTag(TAG);
                mob.addTag(TAG + "_crowd");
                mob.addTag(TAG + "_foe");
                mob.snapTo(0.5, STAGE_Y, 0.5, 90.0F, 0.0F);
                level.addFreshEntity(mob);
            }
        }
        level.addFreshEntity(entity);
        subjectId = entity.getId();
    }

    private static Entity subject(ServerLevel level) {
        AABB area = new AABB(-12, STAGE_Y - 4, -12, 12, STAGE_Y + 20, 12);
        return level.getEntitiesOfClass(Entity.class, area, e -> e.getTags().contains(TAG)
                && !e.getTags().contains(TAG + "_ref") && !e.getTags().contains(TAG + "_crowd")).stream().findFirst().orElse(null);
    }

    private static Entity foe(ServerLevel level) {
        AABB area = new AABB(-12, STAGE_Y - 4, -12, 12, STAGE_Y + 20, 12);
        return level.getEntitiesOfClass(Entity.class, area, e -> e.getTags().contains(TAG + "_foe"))
                .stream().findFirst().orElse(null);
    }

    /** Who a staged settlement person is: the role, and the name a post would give them, which picks their skin. */
    private static void dress(SettlementNpcEntity npc, String role, String person) {
        npc.setRoleId(WizardsAndBeastsMod.MODID + ":" + role);
        npc.setCustomName(Component.translatable("npc." + WizardsAndBeastsMod.MODID + "." + role + ".name." + person));
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
