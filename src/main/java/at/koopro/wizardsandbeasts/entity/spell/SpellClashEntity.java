package at.koopro.wizardsandbeasts.entity.spell;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.network.spell.SpellImpactBurstS2CPayload;
import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.spell.clash.SpellClashLocks;
import at.koopro.wizardsandbeasts.spell.clash.SpellClashRules;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellFamilies;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellScalingProfile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Two spells locked together in mid-air — the Priori Incantatem duel.
 *
 * <h2>How a clash works</h2>
 * <ol>
 *   <li>{@code SpellProjectileEntity.trySpellClash} finds two live bolts from different casters
 *       whose paths this tick pass within {@link SpellClashRules#CLASH_RADIUS} of each other, and
 *       that {@link SpellClashRules#canClash} allows to lock. Both projectiles are discarded and this
 *       entity takes their place.</li>
 *   <li>A bolt fires when the button comes <em>up</em>, so both casters have let go by now. Each has
 *       {@link SpellClashRules#HOLD_GRACE_TICKS} to press and hold the wand again. That hold feeds the
 *       lock and nothing else — see {@link SpellClashLocks}.</li>
 *   <li>The joint sits on the line between the two casters' wands and follows them as they move. While
 *       both hold, the stronger cast pushes it towards the weaker caster.</li>
 *   <li>{@link SpellClashRules#judge} ends it. Whoever lets go, or never picks it up, loses; the joint
 *       reaching a caster's wand loses them the lock too. The winner's spell is then fired from the joint
 *       at the loser as an ordinary bolt, so it lands with its full effect. Both giving up breaks the lock
 *       and nobody is hit.</li>
 *   <li>The client draws a beam from each holding caster's wand tip to the joint, and a knot of lightning
 *       where they meet — {@code SpellClashRenderer}.</li>
 * </ol>
 *
 * <p>Transient like {@link ProtegoShieldEntity}: never saved, and a reload simply ends the lock.
 */
public class SpellClashEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_COLOR_A =
            SynchedEntityData.defineId(SpellClashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COLOR_B =
            SynchedEntityData.defineId(SpellClashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_AXIS_X =
            SynchedEntityData.defineId(SpellClashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AXIS_Y =
            SynchedEntityData.defineId(SpellClashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AXIS_Z =
            SynchedEntityData.defineId(SpellClashEntity.class, EntityDataSerializers.FLOAT);
    /** Entity ids of the two casters, so the client can find their wand tips. {@code -1} for none. */
    private static final EntityDataAccessor<Integer> DATA_CASTER_A_ID =
            SynchedEntityData.defineId(SpellClashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CASTER_B_ID =
            SynchedEntityData.defineId(SpellClashEntity.class, EntityDataSerializers.INT);
    /** Bit 0: A is holding. Bit 1: B is holding. A side that is not holding draws no beam. */
    private static final EntityDataAccessor<Byte> DATA_HOLDING =
            SynchedEntityData.defineId(SpellClashEntity.class, EntityDataSerializers.BYTE);

    private static final byte HOLDING_A = 1;
    private static final byte HOLDING_B = 2;

    /** Ticks between the extra crackles that sit under the spawn sound. */
    private static final int CRACKLE_INTERVAL = 7;
    /** Ticks between reminders to a caster who has not picked the lock up yet. */
    private static final int PROMPT_INTERVAL = 10;
    /** How far in front of the eyes, towards the other caster, the server stands a wand tip. */
    private static final double ARM = 0.6;
    /** Casters further apart than this cannot keep a lock between them; it breaks. */
    private static final double MAX_SPAN = 48.0;
    /**
     * Casters closer than this have no room for a joint that is not already on somebody's wand, so
     * the lock breaks rather than handing the win to whichever side is checked first.
     */
    private static final double MIN_SPAN = 2.0 * (ARM + SpellClashRules.WAND_REACH);
    /** Where along the line the joint may start: never already within reach of a wand. */
    private static final double START_MARGIN = 0.2;
    /** Blocks per tick the winner's spell flies from the joint at the loser. */
    private static final float WINNING_BOLT_SPEED = 2.5f;

    private @Nullable UUID casterA;
    private @Nullable UUID casterB;
    private String spellA = "";
    private String spellB = "";
    private float powerA = 1.0f;
    private float powerB = 1.0f;
    private SpellScalingProfile profileA = SpellScalingProfile.DEFAULT;
    private SpellScalingProfile profileB = SpellScalingProfile.DEFAULT;

    private int ticksAlive;
    private boolean everHeldA;
    private boolean everHeldB;
    /** Where the joint sits on the line from A's wand to B's: {@code 0} on A's, {@code 1} on B's. */
    private double jointT = 0.5;

    public SpellClashEntity(EntityType<? extends SpellClashEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    /**
     * Locks two bolts together, where their paths met.
     *
     * @return whether a lock was made. When it was not — a caster who cannot be found, or one already
     *         locked in another clash — the bolts fly on past each other.
     */
    public static boolean spawn(ServerLevel level, Vec3 meeting,
                                SpellProjectileEntity a, SpellProjectileEntity b) {
        Spell spellA = a.getCachedOrResolveSpell();
        Spell spellB = b.getCachedOrResolveSpell();
        if (spellA == null || spellB == null
                || !(a.getOwner() instanceof LivingEntity ownerA)
                || !(b.getOwner() instanceof LivingEntity ownerB)
                || SpellClashLocks.isLocked(ownerA.getUUID())
                || SpellClashLocks.isLocked(ownerB.getUUID())) {
            return false;
        }
        SpellClashEntity clash = new SpellClashEntity(ModEntities.SPELL_CLASH.get(), level);
        clash.setPos(meeting);
        clash.casterA = ownerA.getUUID();
        clash.casterB = ownerB.getUUID();
        clash.spellA = spellA.getId();
        clash.spellB = spellB.getId();
        clash.powerA = a.getDamageMultiplier();
        clash.powerB = b.getDamageMultiplier();
        clash.profileA = a.getScalingProfile();
        clash.profileB = b.getScalingProfile();
        clash.entityData.set(DATA_COLOR_A, spellA.getColor());
        clash.entityData.set(DATA_COLOR_B, spellB.getColor());
        clash.entityData.set(DATA_CASTER_A_ID, ownerA.getId());
        clash.entityData.set(DATA_CASTER_B_ID, ownerB.getId());

        // Start the joint where the bolts met, measured along the line between the two wands.
        Vec3 wandA = eyeLine(ownerA);
        Vec3 span = eyeLine(ownerB).subtract(wandA);
        double lengthSqr = span.lengthSqr();
        if (lengthSqr > 1.0e-6) {
            double t = meeting.subtract(wandA).dot(span) / lengthSqr;
            clash.jointT = Mth.clamp(t, START_MARGIN, 1.0 - START_MARGIN);
            clash.setAxis(span.normalize());
        }

        level.addFreshEntity(clash);
        SpellClashLocks.enter(ownerA, clash);
        SpellClashLocks.enter(ownerB, clash);
        level.playSound(null, clash.blockPosition(), ModSounds.SPELL_CLASH.get(), SoundSource.PLAYERS,
                0.75f, 0.95f + level.random.nextFloat() * 0.15f);
        // Rides the existing impact burst: every tracking client already turns this into particles
        // and a camera kick (see ScreenShakeHandler), so the clash punches the view for free.
        SpellImpactBurstS2CPayload.sendToTracking(clash, meeting, SpellFamilies.of(spellA),
                spellA.getColor(), 24, 0.4f);
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_COLOR_A, 0xFFFFFF);
        builder.define(DATA_COLOR_B, 0xFFFFFF);
        builder.define(DATA_AXIS_X, 1.0f);
        builder.define(DATA_AXIS_Y, 0.0f);
        builder.define(DATA_AXIS_Z, 0.0f);
        builder.define(DATA_CASTER_A_ID, -1);
        builder.define(DATA_CASTER_B_ID, -1);
        builder.define(DATA_HOLDING, (byte) 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ticksAlive++;

        // A caster who is gone — logged out, in another dimension, dead — has given the lock up.
        LivingEntity a = resolve(serverLevel, casterA);
        LivingEntity b = resolve(serverLevel, casterB);
        boolean holdingA = a != null && SpellClashLocks.isHolding(a);
        boolean holdingB = b != null && SpellClashLocks.isHolding(b);
        everHeldA |= holdingA || a == null;
        everHeldB |= holdingB || b == null;
        entityData.set(DATA_HOLDING, (byte) ((holdingA ? HOLDING_A : 0) | (holdingB ? HOLDING_B : 0)));

        double distToA = Double.MAX_VALUE;
        double distToB = Double.MAX_VALUE;
        if (a != null && b != null) {
            entityData.set(DATA_CASTER_A_ID, a.getId());
            entityData.set(DATA_CASTER_B_ID, b.getId());
            Vec3 eyeA = eyeLine(a);
            Vec3 eyeB = eyeLine(b);
            Vec3 span = eyeB.subtract(eyeA);
            double length = span.length();
            if (length < MIN_SPAN || length > MAX_SPAN) {
                breakLock(serverLevel);
                return;
            }
            Vec3 dir = span.scale(1.0 / length);
            Vec3 wandA = eyeA.add(dir.scale(ARM));
            Vec3 wandB = eyeB.subtract(dir.scale(ARM));
            double wandSpan = length - 2.0 * ARM;
            if (holdingA && holdingB) {
                jointT = Mth.clamp(jointT + SpellClashRules.jointStep(powerA, powerB) / wandSpan, 0.0, 1.0);
            }
            setPos(wandA.lerp(wandB, jointT));
            setAxis(dir);
            distToA = jointT * wandSpan;
            distToB = (1.0 - jointT) * wandSpan;
        }

        switch (SpellClashRules.judge(holdingA, everHeldA, holdingB, everHeldB, ticksAlive, distToA, distToB)) {
            case A_WINS -> finish(serverLevel, a, spellA, powerA, profileA, b);
            case B_WINS -> finish(serverLevel, b, spellB, powerB, profileB, a);
            case BREAK -> breakLock(serverLevel);
            case HOLD -> {
                promptToHold(a, everHeldA);
                promptToHold(b, everHeldB);
                if (ticksAlive % particleIntervalTicks() == 0) {
                    emitParticles(serverLevel);
                }
                if (ticksAlive % CRACKLE_INTERVAL == 0) {
                    serverLevel.playSound(null, blockPosition(), ModSounds.SPELL_CLASH.get(), SoundSource.PLAYERS,
                            0.32f, 1.25f + serverLevel.random.nextFloat() * 0.35f);
                }
            }
        }
    }

    /**
     * The lock is won: the winner's spell leaves the joint for the loser as an ordinary bolt, so it lands
     * with everything a hit carries — damage, effects, a disarm — and can still be blocked like one.
     */
    private void finish(ServerLevel level, @Nullable LivingEntity winner, String spellId, float power,
                        SpellScalingProfile profile, @Nullable LivingEntity loser) {
        Vec3 joint = position();
        breakLock(level);
        if (winner == null || loser == null || !loser.isAlive()) {
            return;
        }
        SpellProjectileEntity bolt = new SpellProjectileEntity(level, winner, spellId);
        bolt.setPos(joint);
        bolt.setDamageMultiplier(power);
        bolt.setScalingProfile(profile);
        bolt.markFromClash();
        Vec3 aim = loser.getBoundingBox().getCenter().subtract(joint);
        bolt.shoot(aim.x, aim.y, aim.z, WINNING_BOLT_SPEED, 0.0f);
        level.addFreshEntity(bolt);
    }

    private void breakLock(ServerLevel level) {
        burst(level);
        discard();
    }

    /** Every exit from a lock runs through here, so nobody is left locked in a clash that is gone. */
    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (level().isClientSide()) {
            return;
        }
        if (casterA != null) {
            SpellClashLocks.leave(casterA, this);
        }
        if (casterB != null) {
            SpellClashLocks.leave(casterB, this);
        }
    }

    /** While a caster still has time to pick the lock up and has not, tell them how. */
    private void promptToHold(@Nullable LivingEntity caster, boolean everHeld) {
        if (!everHeld && caster instanceof ServerPlayer player
                && ticksAlive < SpellClashRules.HOLD_GRACE_TICKS && ticksAlive % PROMPT_INTERVAL == 1) {
            player.displayClientMessage(Component.translatable("spell.wizards_and_beasts.clash.hold_prompt"), true);
        }
    }

    private static @Nullable LivingEntity resolve(ServerLevel level, @Nullable UUID id) {
        if (id == null) {
            return null;
        }
        return level.getEntity(id) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /** The height a bolt leaves a caster at; see {@code SpellProjectileEntity}'s constructor. */
    private static Vec3 eyeLine(LivingEntity caster) {
        return caster.getEyePosition().subtract(0.0, 0.1, 0.0);
    }

    private void setAxis(Vec3 axis) {
        entityData.set(DATA_AXIS_X, (float) axis.x);
        entityData.set(DATA_AXIS_Y, (float) axis.y);
        entityData.set(DATA_AXIS_Z, (float) axis.z);
    }

    /** The burning, spitting core of the lock: both spell colours, sparks, and a little fire. */
    private void emitParticles(ServerLevel level) {
        for (int i = 0; i < 2; i++) {
            int tint = i == 0 ? getColorA() : getColorB();
            level.sendParticles(new SpellTintParticleOptions(ModParticles.SPELL_CLASH.get(), tint),
                    getX(), getY(), getZ(), 3, 0.18, 0.18, 0.18, 0.01);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 4, 0.22, 0.22, 0.22, 0.06);
        level.sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 3, 0.2, 0.2, 0.2, 0.05);
        level.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY(), getZ(), 1, 0.12, 0.12, 0.12, 0.01);
    }

    /** The lock lets go: one bright cough of sparks rather than a silent disappearance. */
    private void burst(ServerLevel level) {
        level.sendParticles(new SpellTintParticleOptions(ModParticles.SPELL_CLASH.get(), getColorA()),
                getX(), getY(), getZ(), 12, 0.3, 0.3, 0.3, 0.06);
        level.sendParticles(new SpellTintParticleOptions(ModParticles.SPELL_CLASH.get(), getColorB()),
                getX(), getY(), getZ(), 12, 0.3, 0.3, 0.3, 0.06);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 16, 0.35, 0.35, 0.35, 0.12);
        level.playSound(null, blockPosition(), ModSounds.SPELL_CLASH.get(), SoundSource.PLAYERS,
                0.6f, 0.8f + level.random.nextFloat() * 0.1f);
    }

    /** Same throttle the projectile trail uses, so a duel cannot out-spend the server's particle budget. */
    private static int particleIntervalTicks() {
        return switch (Config.perfProfile) {
            case LOW -> 3;
            case MEDIUM -> 2;
            case HIGH -> 1;
        };
    }

    public int getColorA() {
        return this.entityData.get(DATA_COLOR_A);
    }

    public int getColorB() {
        return this.entityData.get(DATA_COLOR_B);
    }

    public int getCasterAId() {
        return this.entityData.get(DATA_CASTER_A_ID);
    }

    public int getCasterBId() {
        return this.entityData.get(DATA_CASTER_B_ID);
    }

    public boolean isHoldingA() {
        return (this.entityData.get(DATA_HOLDING) & HOLDING_A) != 0;
    }

    public boolean isHoldingB() {
        return (this.entityData.get(DATA_HOLDING) & HOLDING_B) != 0;
    }

    /** Unit vector from A's side to B's. Synced, so the client draws the same line. */
    public Vec3 axis() {
        return new Vec3(this.entityData.get(DATA_AXIS_X),
                this.entityData.get(DATA_AXIS_Y),
                this.entityData.get(DATA_AXIS_Z));
    }

    public @Nullable UUID getCasterA() {
        return casterA;
    }

    public @Nullable UUID getCasterB() {
        return casterB;
    }

    public String getSpellA() {
        return spellA;
    }

    public String getSpellB() {
        return spellB;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(Entity entity) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        // TRANSIENT: reloading into a lock nobody is holding would be stranger than losing it.
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        // TRANSIENT: not saved
    }
}
