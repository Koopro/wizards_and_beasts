package at.koopro.wizardsandbeasts.entity.spell;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.network.spell.SpellImpactBurstS2CPayload;
import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.spell.clash.SpellClashRules;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellFamilies;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Two spells locked together in mid-air — the Priori Incantatem look.
 *
 * <h2>How a clash works</h2>
 * <ol>
 *   <li>{@code SpellProjectileEntity.trySpellClash} finds two live bolts from different casters
 *       whose paths this tick pass within {@link SpellClashRules#CLASH_RADIUS} of each other, and
 *       that {@link SpellClashRules#canClash} allows to
 *       lock. Both projectiles are discarded.</li>
 *   <li>One of them spawns this entity at the midpoint, carrying both spell colours, both caster
 *       ids and the axis running from one bolt to the other.</li>
 *   <li>This entity holds for {@link SpellClashRules#lifetimeTicks} — one to two and a half seconds
 *       — spitting sparks and crackling, and creeping along the axis towards the weaker caster so
 *       the stronger cast visibly wins the push.</li>
 *   <li>The client draws the lightning: {@code SpellClashRenderer} runs the existing
 *       {@code client.beam.Lightning} shape between the two ends of the axis, several bolts at a
 *       time, in each spell's own colour over a white core.</li>
 * </ol>
 *
 * <p>Nothing here damages anybody. The clash is optics: the two spells already cancelled when their
 * projectiles were discarded, and the only thing at stake afterwards is which way the joint drifts.
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

    /** Blocks per tick the joint creeps towards the losing side at a total mismatch. */
    private static final double DRIFT_PER_TICK = 0.02;
    /** Ticks between the extra crackles that sit under the spawn sound. */
    private static final int CRACKLE_INTERVAL = 7;

    private @Nullable UUID casterA;
    private @Nullable UUID casterB;
    private String spellA = "";
    private String spellB = "";
    private float powerA = 1.0f;
    private float powerB = 1.0f;

    private int ticksAlive;
    private int maxLife = SpellClashRules.MIN_LIFETIME_TICKS;

    public SpellClashEntity(EntityType<? extends SpellClashEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    /**
     * Locks two bolts together. Called from the projectile that won the tie-break, after both
     * projectiles have been discarded.
     */
    public static void spawn(ServerLevel level, Vec3 midpoint,
                             SpellProjectileEntity a, SpellProjectileEntity b) {
        Spell spellA = a.getCachedOrResolveSpell();
        Spell spellB = b.getCachedOrResolveSpell();
        if (spellA == null || spellB == null) {
            return;
        }
        SpellClashEntity clash = new SpellClashEntity(ModEntities.SPELL_CLASH.get(), level);
        clash.setPos(midpoint);
        clash.casterA = a.getCasterUuid();
        clash.casterB = b.getCasterUuid();
        clash.spellA = spellA.getId();
        clash.spellB = spellB.getId();
        clash.powerA = a.getDamageMultiplier();
        clash.powerB = b.getDamageMultiplier();
        clash.maxLife = SpellClashRules.lifetimeTicks(clash.powerA, clash.powerB);
        clash.entityData.set(DATA_COLOR_A, spellA.getColor());
        clash.entityData.set(DATA_COLOR_B, spellB.getColor());

        // The axis is the line the two bolts were fighting along, so the lightning can jump between
        // the points they came from rather than flailing around a bare sphere. Taken from where the
        // bolts started this tick: they usually meet mid-tick, so by its end they have already passed
        // each other and their current positions would point the axis — and the drift — backwards.
        Vec3 axis = b.oldPosition().subtract(a.oldPosition());
        axis = axis.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : axis.normalize();
        clash.entityData.set(DATA_AXIS_X, (float) axis.x);
        clash.entityData.set(DATA_AXIS_Y, (float) axis.y);
        clash.entityData.set(DATA_AXIS_Z, (float) axis.z);

        level.addFreshEntity(clash);
        level.playSound(null, clash.blockPosition(), ModSounds.SPELL_CLASH.get(), SoundSource.PLAYERS,
                0.75f, 0.95f + level.random.nextFloat() * 0.15f);
        // Rides the existing impact burst: every tracking client already turns this into particles
        // and a camera kick (see ScreenShakeHandler), so the clash punches the view for free.
        SpellImpactBurstS2CPayload.sendToTracking(clash, midpoint, SpellFamilies.of(spellA),
                spellA.getColor(), 24, 0.4f);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_COLOR_A, 0xFFFFFF);
        builder.define(DATA_COLOR_B, 0xFFFFFF);
        builder.define(DATA_AXIS_X, 1.0f);
        builder.define(DATA_AXIS_Y, 0.0f);
        builder.define(DATA_AXIS_Z, 0.0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ticksAlive++;
        if (ticksAlive > maxLife) {
            burst(serverLevel);
            discard();
            return;
        }

        // The stronger cast pushes the joint towards the weaker one. Positive bias = A is stronger,
        // and the axis points from A to B, so the lock slides the way the loser is standing.
        float bias = SpellClashRules.bias(powerA, powerB);
        if (bias != 0.0f) {
            setPos(position().add(axis().scale(bias * DRIFT_PER_TICK)));
        }

        if (ticksAlive % particleIntervalTicks() == 0) {
            emitParticles(serverLevel);
        }
        if (ticksAlive % CRACKLE_INTERVAL == 0) {
            serverLevel.playSound(null, blockPosition(), ModSounds.SPELL_CLASH.get(), SoundSource.PLAYERS,
                    0.32f, 1.25f + serverLevel.random.nextFloat() * 0.35f);
        }
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

    /** Unit vector from the first bolt's side to the second's. Synced, so the client draws the same line. */
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
        // TRANSIENT: a clash lasts two seconds; reloading into one would be stranger than losing it.
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        // TRANSIENT: not saved
    }
}
