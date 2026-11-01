package at.koopro.wizardsandbeasts.chamber;

import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleContentIndex;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import at.koopro.wizardsandbeasts.registry.ModCreatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Runs one Chamber's basilisk encounter ({@link ChamberEncounter}) from the dais at the end of the hall.
 *
 * <p>Once a second it looks at the hall: who stands in it, whether the woken basilisk is still there. Someone entering a
 * sleeping Chamber wakes the basilisk at the hall's middle — persistent (it never despawns), bound to this heart, with a
 * boss bar for everyone in the hall and a warning that names the counterplay. A basilisk that strays out of its hall
 * is brought back. A hall left empty for two minutes puts the basilisk back, whole, for whoever comes next. The
 * basilisk's death ends it for good: everyone who fought it or stood in the hall is credited, and the one who struck
 * the last blow is given the sword ({@link ChamberSword}). Everything here is saved with the block.
 *
 * <p>The hall is measured from the heart's facing, which turns with the structure, so it is found in a Chamber
 * generated at any rotation.
 */
public class ChamberHeartBlockEntity extends BlockEntity {

    /** Ticks between looks. */
    static final int LOOK = 20;
    /** The hall, in blocks from the heart: along its facing, to each side, below and above. */
    static final int FORWARD_MIN = -1;
    static final int FORWARD_MAX = 22;
    static final int LATERAL = 7;
    static final int DOWN = 2;
    static final int UP = 8;
    /** Where the basilisk wakes: the middle of the hall, on its floor. */
    static final int SPAWN_FORWARD = 12;
    static final int SPAWN_UP = -1;

    private static final String KEY = "chamber.wizards_and_beasts.";

    private ChamberEncounter.Phase phase = ChamberEncounter.Phase.SLEEPING;
    private @Nullable UUID basilisk;
    private int emptyTicks;
    private final Set<UUID> participants = new LinkedHashSet<>();
    private @Nullable ServerBossEvent bar;

    public ChamberHeartBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHAMBER_HEART.get(), pos, state);
    }

    public ChamberEncounter.Phase phase() {
        return phase;
    }

    public @Nullable UUID basilisk() {
        return basilisk;
    }

    void serverTick(Level level, BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server && (level.getGameTime() + pos.hashCode()) % LOOK == 0) {
            look(server, state);
        }
    }

    /** One look at the hall. Public so a test can take one without waiting for the second to come round. */
    public void look(ServerLevel level, BlockState state) {
        var type = ModCreatures.ENTITIES.get("basilisk");
        // The basilisk is a creature: a server with creatures off has none to wake, whatever the Chamber's module says.
        boolean moduleOn = ModuleManager.isEnabled(Module.CHAMBER_OF_SECRETS)
                && type != null && ModuleContentIndex.isAccessible(type.get());
        AABB hall = hall(getBlockPos(), facing(state));
        List<ServerPlayer> inHall = level.getEntitiesOfClass(ServerPlayer.class, hall,
                p -> p.isAlive() && !p.isSpectator());
        if (moduleOn && phase != ChamberEncounter.Phase.SLAIN) {
            for (ServerPlayer player : inHall) {
                ChamberQuestService.reach(player, ChamberStage.HALL_ENTERED);
            }
        }
        LivingEntity snake = livingBasilisk(level);
        if (phase == ChamberEncounter.Phase.AWAKE) {
            emptyTicks = inHall.isEmpty() ? emptyTicks + LOOK : 0;
        }
        switch (ChamberEncounter.decide(phase, moduleOn, !inHall.isEmpty(), snake != null, emptyTicks)) {
            case WAKE -> snake = wake(level, state, inHall);
            case PUT_BACK -> {
                if (snake != null) {
                    snake.discard();
                }
                snake = null;
                sleep();
            }
            case FORGET -> sleep();
            case NOTHING -> { }
        }
        // An entity's chunk can load before its heart's, past the check in ChamberEvents.onJoin: a basilisk that is not
        // the one this heart is waiting on goes, so a Chamber never holds two and a kill always counts.
        for (LivingEntity stale : level.getEntitiesOfClass(LivingEntity.class, hall.inflate(4.0), this::isStale)) {
            stale.discard();
        }
        if (phase == ChamberEncounter.Phase.AWAKE && snake != null) {
            if (!hall.inflate(2.0).contains(snake.position())) {
                Vec3 spawn = spawnPoint(getBlockPos(), facing(state));
                snake.teleportTo(spawn.x, spawn.y, spawn.z);
            }
            showBar(snake, inHall);
        } else {
            hideBar();
        }
    }

    private boolean isStale(LivingEntity entity) {
        return entity.hasData(ModAttachments.CHAMBER_HEART.get())
                && entity.getData(ModAttachments.CHAMBER_HEART.get()) == getBlockPos().asLong()
                && !entity.getUUID().equals(basilisk);
    }

    private @Nullable LivingEntity livingBasilisk(ServerLevel level) {
        if (basilisk == null) {
            return null;
        }
        Entity entity = level.getEntity(basilisk);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    @Nullable LivingEntity wake(ServerLevel level, BlockState state, List<ServerPlayer> inHall) {
        var type = ModCreatures.ENTITIES.get("basilisk");
        if (type == null) {
            return null;
        }
        Entity created = type.get().create(level, EntitySpawnReason.TRIGGERED);
        if (!(created instanceof LivingEntity snake)) {
            return null;
        }
        Direction facing = facing(state);
        Vec3 spawn = spawnPoint(getBlockPos(), facing);
        // It wakes facing the dais' way: towards whoever came in through the gate.
        snake.snapTo(spawn.x, spawn.y, spawn.z, facing.toYRot(), 0.0f);
        if (snake instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        snake.setData(ModAttachments.CHAMBER_HEART.get(), getBlockPos().asLong());
        basilisk = snake.getUUID();
        level.addFreshEntity(snake);
        phase = ChamberEncounter.Phase.AWAKE;
        emptyTicks = 0;
        participants.clear();
        setChanged();
        level.playSound(null, BlockPos.containing(spawn), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f, 0.5f);
        for (ServerPlayer player : inHall) {
            PlayerFeedback.toast(player, NoticeKind.WARN, Component.translatable(KEY + "wakes"),
                    Component.translatable(KEY + "wakes.body"));
            player.sendSystemMessage(Component.translatable(KEY + "wakes.counsel"));
        }
        return snake;
    }

    private void sleep() {
        phase = ChamberEncounter.Phase.SLEEPING;
        basilisk = null;
        emptyTicks = 0;
        participants.clear();
        hideBar();
        setChanged();
    }

    /** A player harmed this Chamber's basilisk: they are in the fight, and are credited however it ends. */
    public void noteParticipant(ServerPlayer player) {
        if (phase == ChamberEncounter.Phase.AWAKE && participants.add(player.getUUID())) {
            setChanged();
        }
    }

    /**
     * This Chamber's basilisk died. The Chamber is cleared for good; everyone who fought it or stands in the hall reaches
     * the stage, and the one who struck the last blow is given the sword if it is not already out in the world.
     */
    public void basiliskDied(ServerLevel level, LivingEntity snake, DamageSource cause) {
        if (phase == ChamberEncounter.Phase.SLAIN) {
            return;
        }
        phase = ChamberEncounter.Phase.SLAIN;
        basilisk = null;
        hideBar();
        setChanged();
        ChamberBasilisk.slainAt(level, getBlockPos());
        List<ServerPlayer> credited = new ArrayList<>();
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, hall(getBlockPos(), facing(getBlockState())),
                p -> p.isAlive() && !p.isSpectator())) {
            credited.add(player);
        }
        for (UUID id : participants) {
            if (level.getPlayerByUUID(id) instanceof ServerPlayer player && !credited.contains(player)) {
                credited.add(player);
            }
        }
        for (ServerPlayer player : credited) {
            ChamberQuestService.reach(player, ChamberStage.BASILISK_SLAIN);
        }
        ServerPlayer killer = killer(snake, cause, credited);
        if (killer != null) {
            ChamberSword.award(level, getBlockPos(), killer, credited);
        }
        level.playSound(null, getBlockPos(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 0.8f, 0.6f);
    }

    /** The last blow's player; failing that (a rooster's crow), the credited player nearest the dais. */
    private @Nullable ServerPlayer killer(LivingEntity snake, DamageSource cause, List<ServerPlayer> credited) {
        if (cause.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        if (snake.getKillCredit() instanceof ServerPlayer player) {
            return player;
        }
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : credited) {
            double distance = player.distanceToSqr(Vec3.atCenterOf(getBlockPos()));
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private void showBar(LivingEntity snake, List<ServerPlayer> inHall) {
        if (bar == null) {
            bar = new ServerBossEvent(snake.getDisplayName(), BossEvent.BossBarColor.GREEN,
                    BossEvent.BossBarOverlay.NOTCHED_10);
        }
        bar.setProgress(Math.max(0.0f, Math.min(1.0f, snake.getHealth() / snake.getMaxHealth())));
        for (ServerPlayer shown : List.copyOf(bar.getPlayers())) {
            if (!inHall.contains(shown)) {
                bar.removePlayer(shown);
            }
        }
        for (ServerPlayer player : inHall) {
            bar.addPlayer(player);
        }
    }

    /** Who is being shown the boss bar now. */
    public Collection<ServerPlayer> barViewers() {
        return bar == null ? List.of() : bar.getPlayers();
    }

    private void hideBar() {
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    @Override
    public void setRemoved() {
        hideBar();
        super.setRemoved();
    }

    // ── geometry ──────────────────────────────────────────────────────────

    static Direction facing(BlockState state) {
        return state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
    }

    /** The hall the heart watches, from its facing. */
    public static AABB hall(BlockPos heart, Direction facing) {
        Direction right = facing.getClockWise();
        BlockPos a = heart.relative(facing, FORWARD_MIN).relative(right, -LATERAL).below(DOWN);
        BlockPos b = heart.relative(facing, FORWARD_MAX).relative(right, LATERAL).above(UP);
        return AABB.encapsulatingFullBlocks(a, b);
    }

    public static Vec3 spawnPoint(BlockPos heart, Direction facing) {
        return Vec3.atBottomCenterOf(heart.relative(facing, SPAWN_FORWARD).above(SPAWN_UP));
    }

    // ── saving ────────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store("phase", ChamberEncounter.Phase.CODEC, phase);
        if (basilisk != null) {
            output.store("basilisk", UUIDUtil.CODEC, basilisk);
        }
        output.putInt("empty_ticks", emptyTicks);
        output.store("participants", UUIDUtil.CODEC.listOf(), List.copyOf(participants));
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        phase = input.read("phase", ChamberEncounter.Phase.CODEC).orElse(ChamberEncounter.Phase.SLEEPING);
        basilisk = input.read("basilisk", UUIDUtil.CODEC).orElse(null);
        emptyTicks = input.getIntOr("empty_ticks", 0);
        participants.clear();
        input.read("participants", UUIDUtil.CODEC.listOf()).ifPresent(participants::addAll);
    }
}
