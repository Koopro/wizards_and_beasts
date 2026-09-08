package at.koopro.wizardsandbeasts.heritage.werewolf;

import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The thing that drives a werewolf's body while its player does not.
 *
 * <p>One instance per feral player, held in a {@link PlayerScopedState} so it dies with the session and
 * cannot leak. {@link #tick(ServerPlayer, ServerLevel)} is called once per server tick by
 * {@code WerewolfControlHandler}, for exactly the players whose
 * {@link WerewolfState#FLAG_LOSS_OF_CONTROL} flag is set — Wolfsbane clearing that flag, or the moon
 * ending, therefore switches this off with no further cooperation needed from anything here.
 *
 * <h2>Handing control back</h2>
 * {@link #release(ServerPlayer)} is the only way out, and it is called from every site that clears the
 * flag ({@code WerewolfTransformService.setLossOfControl} routes them all through one place). It drops
 * the instance <em>and</em> undoes the two pieces of state the drive leaves on the player: the sprint
 * flag and the current velocity. Without the velocity reset a wolf released mid-charge would keep the
 * last driven delta and shoot forward as a human, which is exactly the kind of thing that makes a
 * takeover feel like it never fully ended. After a release, nothing in this class touches the player
 * again until the flag is set anew: normal movement is 100% the player's.
 *
 * <h2>How control is actually taken</h2>
 * Three layers, because no single one of them is sufficient:
 * <ol>
 *   <li><b>Velocity.</b> Every tick the controller overwrites the player's delta movement and sets
 *       {@code hurtMarked}, which is what makes the server send a velocity packet the client must
 *       apply. This is the same mechanism {@code TransitionManager} uses to freeze a player mid-change.</li>
 *   <li><b>Rotation.</b> The client sends its own yaw and pitch every tick and the server accepts them,
 *       so a server-side {@code setYRot} alone is overwritten before anyone sees it. The controller
 *       therefore also pushes {@link ClientboundPlayerRotationPacket}, which sets the client's rotation
 *       absolutely. It goes out on an interval rather than every tick: every tick is a hard camera lock
 *       that fights the mouse into visible jitter, and every few ticks reads as a head that keeps
 *       snapping back to the prey.</li>
 *   <li><b>The drift guard.</b> Layers 1 and 2 assume a cooperating client. A client that ignores them
 *       — a hacked one, or simply one with the mod's input theft disabled — would walk away. So the
 *       controller watches where the player <em>actually</em> ends up: sustained movement against the
 *       direction it is driving counts as defiance, and past
 *       {@link WerewolfConfig#driftCorrectionBlocks} the server teleports them back to the last place
 *       they were compliant. That is what makes the loss-of-control contract server-authoritative
 *       rather than a request.</li>
 * </ol>
 *
 * <h2>Why there is no pathfinding</h2>
 * There is no vanilla navigation to borrow. {@code PathNavigation}, {@code PathFinder} and every
 * {@code NodeEvaluator} in the game are built around a {@link net.minecraft.world.entity.Mob}: the
 * navigation constructor takes one, the evaluator asks it for its path types and its
 * {@code Attributes.FOLLOW_RANGE}, and a {@link ServerPlayer} is not a {@code Mob}. Making a real path
 * would mean spawning and maintaining a proxy mob per transformed werewolf purely to ask it questions —
 * an entity that can be hit, targeted, persisted and desynced, for a hunt that happens a few times a
 * month.
 *
 * <p>So the movement is direct, with local obstacle avoidance: {@link #probe} asks whether the player's
 * own bounding box fits one step along a heading, {@link #steer} fans out to either side when it does
 * not, and a fall check keeps the wolf from charging off a cliff after something standing on level
 * ground. That handles doorways, fences, one-block steps and ledges, which is the whole of what a
 * charge across open terrain actually meets. It will not solve a maze — a werewolf is not supposed to.
 *
 * <h2>Extending this</h2>
 * Every step is a {@code protected} method and the target choice lives in {@link FeralTargeting} and
 * {@link FeralTargetSelection}, so a pack layer overrides {@link #selectTarget}, a frenzy tier reads
 * {@link #getRage()} from {@link #onBite}, and a stalking gait replaces {@link #drive} — none of them
 * need to touch the constraint layer or the transform sequence.
 */
public class FeralController {

    // ── tuning ─────────────────────────────────────────────────────────

    /** How close the wolf must be to bite. Slightly over vanilla melee reach: it has a longer neck. */
    protected static final double BITE_REACH = 3.2;
    /** Ticks between target scans. Also the cadence at which a higher-priority target can steal focus. */
    protected static final int RETARGET_INTERVAL_TICKS = 10;
    /** Ticks between forced rotation packets. See the class javadoc on why this is not 1. */
    protected static final int ROTATION_SYNC_INTERVAL_TICKS = 3;
    /** Consecutive ticks of movement against the drive before the drift guard fires. */
    protected static final int DEFIANCE_TICKS = 20;
    /** Ticks between wander heading changes when there is nothing to hunt. */
    protected static final int PROWL_INTERVAL_TICKS = 40;
    /** Speed the wolf moves at with no target, as a fraction of its charge speed. */
    protected static final double PROWL_SPEED_FACTOR = 0.45;
    /** Upward impulse used to clear a block the wolf has walked into. Vanilla's own jump power. */
    protected static final double HOP_VELOCITY = 0.42;
    /** Upward impulse used to climb while swimming. */
    protected static final double SWIM_VELOCITY = 0.14;
    /** How far ahead {@link #probe} looks, in blocks. Roughly one tick of a charge. */
    protected static final double PROBE_DISTANCE = 0.6;
    /** The height of a step the wolf will take without treating it as a wall. */
    protected static final double STEP_UP_HEIGHT = 1.0;
    /** A drop this deep or deeper is a cliff, not a step, unless the prey is down it. */
    protected static final int MAX_SAFE_DROP = 4;
    /** Headings tried, in order, when the wolf cannot go straight at its target. */
    protected static final double[] STEER_OFFSETS_DEG = {0.0, 35.0, -35.0, 70.0, -70.0, 105.0, -105.0};

    // ── rage ───────────────────────────────────────────────────────────

    /** Ceiling for {@link #rage}. */
    public static final int MAX_RAGE = 100;
    /** Rage banked when a target enters the wolf's attention. */
    protected static final int RAGE_ON_PREY_SIGHTED = 8;
    /** Rage banked per landed bite. */
    protected static final int RAGE_ON_BITE = 5;
    /** Rage per half-heart taken. Being hurt is what winds a wolf up fastest. */
    protected static final int RAGE_PER_DAMAGE = 3;
    /** Cap on the rage a single blow can bank, so one big hit is not an instant frenzy. */
    protected static final int RAGE_PER_HIT_CAP = 25;
    /** Ticks between rage decay steps. */
    protected static final int RAGE_DECAY_INTERVAL_TICKS = 40;
    /** Rage shed per decay step while hunting, and while idle. Calm comes faster with nothing to chase. */
    protected static final int RAGE_DECAY_HUNTING = 1;
    /** @see #RAGE_DECAY_HUNTING */
    protected static final int RAGE_DECAY_IDLE = 3;
    /** Fraction added to charge speed at full rage. */
    protected static final double RAGE_SPEED_BONUS = 0.35;
    /** Fraction added to the aggro radius at full rage: a furious wolf notices more. */
    protected static final double RAGE_RADIUS_BONUS = 0.5;
    /** Rage per tick shaved off the bite cooldown. */
    protected static final int RAGE_PER_BITE_COOLDOWN_TICK = 25;
    /** Floor for the bite cooldown however furious the wolf is. */
    protected static final int MIN_BITE_COOLDOWN_TICKS = 4;

    private static final PlayerScopedState<FeralController> CONTROLLERS =
            PlayerScopedState.create("werewolf-feral-controllers");

    // ── state ──────────────────────────────────────────────────────────

    @Nullable
    protected UUID targetId;
    protected int retargetCooldown;
    protected int biteCooldown;
    protected int prowlCooldown;
    protected int rotationCooldown;
    protected int rageDecayCooldown = RAGE_DECAY_INTERVAL_TICKS;
    protected int defiance;
    /** 0–{@link #MAX_RAGE}. Drives speed, reach of attention and bite cadence. */
    protected int rage;
    @Nullable
    protected Vec3 lastPos;
    @Nullable
    protected Vec3 lastDriveDir;
    @Nullable
    protected Vec3 compliantPos;
    protected double prowlYaw;

    /** The controller driving this player, created on first use. */
    public static FeralController of(ServerPlayer player) {
        return CONTROLLERS.computeIfAbsent(player.getUUID(), id -> new FeralController());
    }

    /** The controller already driving this player, or null. Does not create one. */
    @Nullable
    public static FeralController peek(ServerPlayer player) {
        return CONTROLLERS.get(player.getUUID());
    }

    /**
     * Hands the body back. Idempotent, and safe to call on a player who was never feral.
     *
     * <p>Called from {@code WerewolfTransformService.setLossOfControl} whenever the flag goes false —
     * Wolfsbane, dawn, death, the heritage module being switched off — so there is one place that has
     * to remember to do it.
     */
    public static void release(ServerPlayer player) {
        FeralController controller = CONTROLLERS.remove(player.getUUID());
        if (controller != null) {
            controller.onReleased(player);
        }
    }

    /**
     * Undoes what the drive leaves on the player.
     *
     * <p>Only two things outlive a tick of driving: the sprint flag and the delta movement. Both are
     * cleared, the second because a wolf released mid-charge would otherwise keep the last driven
     * velocity and shoot off as a human. Rotation needs no undo — the client owns it again the moment
     * this stops sending rotation packets.
     */
    protected void onReleased(ServerPlayer player) {
        player.setSprinting(false);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
    }

    // ── the tick ───────────────────────────────────────────────────────

    /** Drives one tick. Called only for players whose loss-of-control flag is set. */
    public void tick(ServerPlayer player, ServerLevel level) {
        enforceDrift(player);

        FeralTargeting.Acquired target = resolveTarget(player, level);
        if (target == null) {
            prowl(player, level);
        } else {
            face(player, target.entity());
            drive(player, level, target.entity());
            attemptBite(player, level, target.entity());
        }

        recordPosition(player);
        decayCooldowns(target != null);
    }

    // ── targeting ──────────────────────────────────────────────────────

    /**
     * Keeps or replaces the current target.
     *
     * <p>The switching rule is entirely {@link FeralTargetSelection#shouldSwitch}: the wolf drops what
     * it is chasing when that target dies, is removed or leaves the leash, and otherwise only when a
     * <em>strictly higher band</em> walks into range. A nearer animal never steals it from a further
     * one. Between scans a still-valid target is returned without a query at all, so the entity search
     * runs at most once every {@link #RETARGET_INTERVAL_TICKS}.
     */
    protected FeralTargeting.@Nullable Acquired resolveTarget(ServerPlayer player, ServerLevel level) {
        LivingEntity held = lookupTarget(level);
        boolean heldValid = held != null && FeralTargeting.isStillValid(player, held, aggroRadius());
        if (!heldValid) {
            targetId = null;
        }

        if (retargetCooldown > 0) {
            return heldValid ? new FeralTargeting.Acquired(held, FeralTargeting.classify(held)) : null;
        }
        retargetCooldown = RETARGET_INTERVAL_TICKS;

        FeralTargeting.Acquired best = selectTarget(player, level);
        FeralTargetSelection.Candidate current =
                heldValid ? FeralTargeting.candidateFor(player, held) : null;
        FeralTargetSelection.Candidate challenger =
                best == null ? null : FeralTargeting.candidateFor(player, best.entity());

        if (!FeralTargetSelection.shouldSwitch(current, challenger)) {
            return heldValid ? new FeralTargeting.Acquired(held, current.priority()) : null;
        }
        targetId = best.entity().getUUID();
        onTargetAcquired(player, level, best);
        return best;
    }

    /** Override to change who the wolf hunts (pack focus fire, rage-widened prey, and so on). */
    protected FeralTargeting.@Nullable Acquired selectTarget(ServerPlayer player, ServerLevel level) {
        return FeralTargeting.select(player, level, aggroRadius());
    }

    /** Hook: the moment a target is locked on. Default howls and banks a little rage. */
    protected void onTargetAcquired(ServerPlayer player, ServerLevel level, FeralTargeting.Acquired target) {
        addRage(RAGE_ON_PREY_SIGHTED);
        level.playSound(null, player.blockPosition(), ModSounds.WEREWOLF_HOWL.get(),
                SoundSource.PLAYERS, 1.0f, 0.9f + level.random.nextFloat() * 0.2f);
    }

    @Nullable
    private LivingEntity lookupTarget(ServerLevel level) {
        if (targetId == null) {
            return null;
        }
        Entity entity = level.getEntity(targetId);
        return entity instanceof LivingEntity living ? living : null;
    }

    // ── movement ───────────────────────────────────────────────────────

    /** Charges the target. Override for a different gait — stalking, circling, leaping. */
    protected void drive(ServerPlayer player, ServerLevel level, LivingEntity target) {
        Vec3 flat = new Vec3(target.getX() - player.getX(), 0.0, target.getZ() - player.getZ());
        if (flat.lengthSqr() < 1.0E-4) {
            lastDriveDir = null;
            return;
        }
        advance(player, level, flat.normalize(), target, chargeSpeed());
    }

    /** No target: keep moving, so a feral wolf is never a statue with a locked inventory. */
    protected void prowl(ServerPlayer player, ServerLevel level) {
        if (prowlCooldown <= 0) {
            prowlCooldown = PROWL_INTERVAL_TICKS;
            prowlYaw = level.random.nextDouble() * Math.PI * 2.0;
        }
        Vec3 heading = new Vec3(Math.cos(prowlYaw), 0.0, Math.sin(prowlYaw));
        advance(player, level, heading, null, chargeSpeed() * PROWL_SPEED_FACTOR);
    }

    /** Steers around whatever is in the way, then writes the movement. */
    protected void advance(ServerPlayer player, ServerLevel level, Vec3 desired,
                           @Nullable LivingEntity target, double speed) {
        Heading heading = steer(player, level, desired, target);
        if (heading == null) {
            // Boxed in on every heading. Stop pushing into the wall rather than grinding against it,
            // but keep lastDriveDir null so the drift guard does not read a stationary tick as defiance.
            lastDriveDir = null;
            if (player.isInWater()) {
                climb(player, SWIM_VELOCITY);
            }
            return;
        }
        applyDrive(player, heading.direction(), speed, heading.needsLift());
    }

    /**
     * Picks a heading the wolf can actually take.
     *
     * <p>Fans out from the desired direction through {@link #STEER_OFFSETS_DEG}, alternating sides, and
     * returns the first that is passable. Straight ahead is always tried first, so on open ground this
     * costs one probe and the wolf runs dead at its prey.
     */
    @Nullable
    protected Heading steer(ServerPlayer player, ServerLevel level, Vec3 desired,
                            @Nullable LivingEntity target) {
        for (double offset : STEER_OFFSETS_DEG) {
            Vec3 candidate = offset == 0.0 ? desired : rotateY(desired, offset);
            Advance advance = probe(player, level, candidate, target);
            if (advance != Advance.BLOCKED) {
                return new Heading(candidate, advance == Advance.STEP_UP);
            }
        }
        return null;
    }

    /**
     * Whether the wolf can take one step along {@code direction}.
     *
     * <p>Asks the world the same question vanilla movement does — does this bounding box fit — rather
     * than inspecting block shapes by hand, so fences, carpets, open trapdoors and modded geometry all
     * answer correctly without a special case each.
     */
    protected Advance probe(ServerPlayer player, ServerLevel level, Vec3 direction,
                            @Nullable LivingEntity target) {
        AABB ahead = player.getBoundingBox().move(
                direction.x * PROBE_DISTANCE, 0.0, direction.z * PROBE_DISTANCE);

        if (!level.noCollision(player, ahead)) {
            AABB stepped = ahead.move(0.0, STEP_UP_HEIGHT, 0.0);
            return level.noCollision(player, stepped) ? Advance.STEP_UP : Advance.BLOCKED;
        }
        return wouldFall(player, level, ahead, target) ? Advance.BLOCKED : Advance.CLEAR;
    }

    /**
     * True when stepping there is a fall rather than a step.
     *
     * <p>The prey clause is what stops this becoming cowardice: a wolf will happily go over a ledge
     * after something standing at the bottom of it. It refuses only when the drop leads nowhere it
     * wanted to go. Water counts as ground — a wolf will jump in after you.
     */
    protected boolean wouldFall(ServerPlayer player, ServerLevel level, AABB ahead,
                                @Nullable LivingEntity target) {
        if (player.isInWater() || player.getAbilities().flying || player.isFallFlying()) {
            return false;
        }
        double x = (ahead.minX + ahead.maxX) * 0.5;
        double z = (ahead.minZ + ahead.maxZ) * 0.5;
        BlockPos below = BlockPos.containing(x, ahead.minY - 0.1, z);
        for (int i = 0; i <= MAX_SAFE_DROP; i++) {
            BlockPos pos = below.below(i);
            if (!level.getFluidState(pos).isEmpty()) {
                return false;
            }
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return false;
            }
        }
        return target == null || target.getY() > player.getY() - MAX_SAFE_DROP;
    }

    /**
     * Writes the movement. {@code hurtMarked} is what turns this into a packet the client must obey;
     * without it the server would move a body the client never sees leave.
     */
    protected void applyDrive(ServerPlayer player, Vec3 direction, double speed, boolean lift) {
        lastDriveDir = direction;
        double vertical = player.getDeltaMovement().y;
        if (lift && player.onGround()) {
            vertical = HOP_VELOCITY;
        } else if (player.isInWater()) {
            vertical = Math.max(vertical, lift ? SWIM_VELOCITY : vertical);
        }
        player.setDeltaMovement(direction.x * speed, vertical, direction.z * speed);
        player.hurtMarked = true;
        player.setSprinting(speed >= WerewolfConfig.chargeSpeed);
        // Fall distance is deliberately left alone. Driving a wolf off a cliff should cost it the fall;
        // resetting here every tick would have made loss of control quietly include fall immunity.
    }

    private void climb(ServerPlayer player, double amount) {
        Vec3 delta = player.getDeltaMovement();
        player.setDeltaMovement(delta.x, Math.max(delta.y, amount), delta.z);
        player.hurtMarked = true;
    }

    /** Points the body and the camera at the target, on an interval. */
    protected void face(ServerPlayer player, LivingEntity target) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double dy = target.getEyeY() - player.getEyeY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-4) {
            return;
        }
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        player.setXRot(pitch);

        if (rotationCooldown <= 0) {
            rotationCooldown = ROTATION_SYNC_INTERVAL_TICKS;
            player.connection.send(new ClientboundPlayerRotationPacket(yaw, false, pitch, false));
        }
    }

    // ── the bite ───────────────────────────────────────────────────────

    /**
     * Bites when in reach.
     *
     * <p>{@link net.minecraft.world.entity.player.Player#attack} is used rather than a hand-rolled
     * {@code hurt}, which is what makes the wolf's teeth read from
     * {@code Attributes.ATTACK_DAMAGE} — the attribute {@link WerewolfAttributes} raises for the form.
     * Enchantments, knockback, sweep, the damage tick and the attack-strength curve all come with it,
     * so a wolf hits with exactly the rules a player's own swing does.
     */
    protected void attemptBite(ServerPlayer player, ServerLevel level, LivingEntity target) {
        if (biteCooldown > 0 || player.distanceToSqr(target) > BITE_REACH * BITE_REACH) {
            return;
        }
        biteCooldown = biteCooldownTicks();
        player.swing(InteractionHand.MAIN_HAND, true);
        player.attack(target);
        player.resetAttackStrengthTicker();
        onBite(player, level, target);
    }

    /** Hook: a bite landed. Default banks rage and snarls. */
    protected void onBite(ServerPlayer player, ServerLevel level, LivingEntity target) {
        addRage(RAGE_ON_BITE);
        level.playSound(null, player.blockPosition(), ModSounds.WEREWOLF_HOWL.get(),
                SoundSource.PLAYERS, 0.5f, 1.4f);
    }

    // ── rage ───────────────────────────────────────────────────────────

    /**
     * Winds the wolf up in proportion to a blow it just took, capped so one hit is not a frenzy.
     *
     * <p>Called from {@code WerewolfControlHandler} on {@code LivingDamageEvent.Post}, i.e. after
     * resistances and armour, so the rage tracks what actually hurt rather than what was aimed.
     */
    public void onDamaged(float amount) {
        if (amount <= 0.0f) {
            return;
        }
        addRage(Math.min(RAGE_PER_HIT_CAP, Math.round(amount * RAGE_PER_DAMAGE)));
    }

    /** How worked up this wolf is, 0–{@link #MAX_RAGE}. */
    public int getRage() {
        return rage;
    }

    public void addRage(int amount) {
        rage = Math.max(0, Math.min(MAX_RAGE, rage + amount));
    }

    /** Rage as a 0–1 scalar, which is the form every derived value below actually wants. */
    protected double rageScalar() {
        return rage / (double) MAX_RAGE;
    }

    protected double chargeSpeed() {
        return WerewolfConfig.chargeSpeed * (1.0 + rageScalar() * RAGE_SPEED_BONUS);
    }

    protected double aggroRadius() {
        return WerewolfConfig.aggroRadius * (1.0 + rageScalar() * RAGE_RADIUS_BONUS);
    }

    protected int biteCooldownTicks() {
        return Math.max(MIN_BITE_COOLDOWN_TICKS,
                WerewolfConfig.biteCooldownTicks - rage / RAGE_PER_BITE_COOLDOWN_TICK);
    }

    // ── the drift guard ────────────────────────────────────────────────

    /**
     * Snaps a player back who has been moving against the drive for {@link #DEFIANCE_TICKS} straight
     * and has got further than {@link WerewolfConfig#driftCorrectionBlocks} from where they last
     * complied.
     *
     * <p>Both conditions are needed. Distance alone would punish a wolf being knocked back or falling;
     * direction alone would fire on every glancing collision. And the snap target is a position the
     * player genuinely occupied, never an extrapolated one, so this can never teleport somebody into a
     * wall.
     */
    protected void enforceDrift(ServerPlayer player) {
        Vec3 previous = lastPos;
        Vec3 driveDir = lastDriveDir;
        if (previous == null || driveDir == null) {
            compliantPos = player.position();
            return;
        }
        Vec3 moved = new Vec3(player.getX() - previous.x, 0.0, player.getZ() - previous.z);
        if (moved.lengthSqr() > 1.0E-6) {
            if (moved.normalize().dot(driveDir) < 0.0) {
                defiance++;
            } else {
                defiance = 0;
                compliantPos = player.position();
            }
        }

        Vec3 anchor = compliantPos;
        if (defiance < DEFIANCE_TICKS || anchor == null) {
            return;
        }
        if (player.position().distanceTo(anchor) <= WerewolfConfig.driftCorrectionBlocks) {
            return;
        }
        defiance = 0;
        player.setDeltaMovement(Vec3.ZERO);
        player.connection.teleport(anchor.x, anchor.y, anchor.z, player.getYRot(), player.getXRot());
    }

    private void recordPosition(ServerPlayer player) {
        lastPos = player.position();
        if (compliantPos == null) {
            compliantPos = lastPos;
        }
    }

    private void decayCooldowns(boolean hunting) {
        if (retargetCooldown > 0) retargetCooldown--;
        if (biteCooldown > 0) biteCooldown--;
        if (prowlCooldown > 0) prowlCooldown--;
        if (rotationCooldown > 0) rotationCooldown--;
        if (--rageDecayCooldown <= 0) {
            rageDecayCooldown = RAGE_DECAY_INTERVAL_TICKS;
            addRage(-(hunting ? RAGE_DECAY_HUNTING : RAGE_DECAY_IDLE));
        }
    }

    @Nullable
    public UUID getTargetId() {
        return targetId;
    }

    // ── small types ────────────────────────────────────────────────────

    /** What {@link #probe} found one step along a heading. */
    protected enum Advance {
        /** Walkable, and not a cliff. */
        CLEAR,
        /** Blocked at foot height but clear one block up: a step, a slab, a fence gate's frame. */
        STEP_UP,
        /** A wall, or a drop the wolf has no reason to take. */
        BLOCKED
    }

    /** A heading the wolf can take, and whether taking it needs a hop. */
    protected record Heading(Vec3 direction, boolean needsLift) {}

    /** Rotates a horizontal vector about the Y axis. Degrees, because the tuning table is in degrees. */
    protected static Vec3 rotateY(Vec3 vector, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(
                vector.x * cos - vector.z * sin,
                0.0,
                vector.x * sin + vector.z * cos);
    }
}
