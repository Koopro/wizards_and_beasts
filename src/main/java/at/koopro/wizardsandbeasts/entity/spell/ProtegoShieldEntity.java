package at.koopro.wizardsandbeasts.entity.spell;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.network.spell.ProtegoAnimationS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoBreach;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoDarkThreats;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoFeedback;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoRules;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoTier;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoWardManager;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.state.AnimationTest;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A raised Shield Charm: the thing that actually stands in the world and decides what gets through.
 *
 * <p>The shield is an <b>absorb pool</b>, not a hit counter. Every spell it turns aside and every
 * blow it takes for someone spends integrity; when the pool runs out it is <em>breached</em>, which
 * is loud, hurts the caster's next cast and (at Horribilis) hurts the caster. Running out of time
 * instead is a quiet fade with no penalty — telling those two apart is most of what makes the charm
 * feel like a shield rather than a timer.
 *
 * <p>Two shapes. {@link ProtegoTier#frontalOnly() Frontal} tiers are a disc the caster holds in
 * front of them and only cover the arc they face; the rest are domes centred on the caster, which
 * either walk with them or — when released while sneaking — are {@link #isPlanted() planted} and
 * hold their ground for everyone standing inside.
 *
 * <p>Transient: never written to disk. A shield that outlived a restart would have no caster and no
 * cast behind it.
 */
public class ProtegoShieldEntity extends Entity implements GeoEntity {

    /** How a shield ended. Synced so the client can draw a fade differently from a break. */
    public enum Collapse {
        /** Still up. */
        NONE,
        /** Ran its time out. */
        EXPIRED,
        /** The caster raised a new one. */
        REPLACED,
        /** The caster died, left, changed dimension, or walked away from a planted dome. */
        CASTER_LOST,
        /** Its integrity was spent — the only ending that costs the caster anything. */
        BREACHED;

        static Collapse byIndex(int index) {
            Collapse[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, index))];
        }

        public boolean isBreach() {
            return this == BREACHED;
        }
    }

    private static final EntityDataAccessor<Integer> DATA_TIER =
            SynchedEntityData.defineId(ProtegoShieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_CASTER_UUID =
            SynchedEntityData.defineId(ProtegoShieldEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_PLANTED =
            SynchedEntityData.defineId(ProtegoShieldEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_INTEGRITY =
            SynchedEntityData.defineId(ProtegoShieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_MAX_INTEGRITY =
            SynchedEntityData.defineId(ProtegoShieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLLAPSE =
            SynchedEntityData.defineId(ProtegoShieldEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE_DISC = RawAnimation.begin().thenLoop("idle_disc");
    private static final RawAnimation IDLE_DOME = RawAnimation.begin().thenLoop("idle_dome");

    /** How far in front of the caster the T0 disc is held. */
    public static final double DISC_OFFSET = 1.2;
    /** The protected sphere is centred on the body, not on the feet the entity stands at. */
    public static final double CENTRE_LIFT = 1.0;
    /** Ticks the wreckage lingers after a collapse before the entity goes away. */
    private static final int COLLAPSE_TICKS = 20;
    /** Broadphase slack for the swept interception — the fastest bolt in the mod moves well under this. */
    private static final double MAX_BOLT_STEP = 5.0;
    /** Vanilla's default {@code AbstractArrow} base damage; the field has a setter and no getter. */
    private static final double VANILLA_ARROW_BASE_DAMAGE = 2.0;
    /** What a fireball, wither skull or wind charge is billed at when the ward turns it. */
    private static final float HURTING_PROJECTILE_DAMAGE = 6.0f;
    /** How much of its speed a turned projectile keeps. The ward absorbs the rest. */
    private static final double DEFLECTION_DAMPING = 0.6;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private int ticksAlive;
    private int collapseTicks;
    private int maxLifetime = 80;
    private boolean warnedLowIntegrity;
    /**
     * Projectiles this shield has already answered. Without it a deflected bolt still inside the
     * sphere gets turned again next tick, and a ward could eat its whole pool ping-ponging one spell.
     */
    private final Set<Integer> answeredProjectiles = new HashSet<>();

    public ProtegoShieldEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    public ProtegoShieldEntity(Level level) {
        this(ModEntities.PROTEGO_SHIELD.get(), level);
    }

    /** Builds a shield for a cast that has already decided its tier, pool and lifetime. */
    public static ProtegoShieldEntity raise(Level level, ServerPlayer caster, ProtegoTier tier,
                                            boolean planted, float integrity, int lifetimeTicks) {
        ProtegoShieldEntity shield = new ProtegoShieldEntity(level);
        shield.entityData.set(DATA_TIER, tier.index());
        shield.entityData.set(DATA_CASTER_UUID, caster.getUUID().toString());
        shield.entityData.set(DATA_PLANTED, planted);
        shield.entityData.set(DATA_MAX_INTEGRITY, Math.max(1.0f, integrity));
        shield.entityData.set(DATA_INTEGRITY, Math.max(1.0f, integrity));
        shield.maxLifetime = lifetimeTicks;
        shield.setYRot(caster.getYRot());
        shield.setPos(caster.position());
        if (!planted) {
            shield.followCaster(caster);
        }
        return shield;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TIER, 0);
        builder.define(DATA_CASTER_UUID, "");
        builder.define(DATA_PLANTED, false);
        builder.define(DATA_INTEGRITY, 1.0f);
        builder.define(DATA_MAX_INTEGRITY, 1.0f);
        builder.define(DATA_COLLAPSE, Collapse.NONE.ordinal());
    }

    // ── lifecycle ───────────────────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();

        if (isCollapsing()) {
            collapseTicks++;
            if (level() instanceof ServerLevel serverLevel) {
                ProtegoFeedback.collapseTrail(serverLevel, this, collapseTicks);
                if (collapseTicks >= COLLAPSE_TICKS) {
                    discard();
                }
            }
            return;
        }

        Player caster = findCaster();
        // Followed on both sides: the client redraws the shield against the caster it can already
        // see every tick, instead of stepping along whatever the entity tracker last sent.
        if (!isPlanted() && caster != null) {
            followCaster(caster);
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ticksAlive++;
        if (caster == null || !caster.isAlive()) {
            collapse(Collapse.CASTER_LOST);
            return;
        }
        if (isPlanted() && caster.distanceToSqr(this) > ProtegoRules.PLANT_LEASH_BLOCKS * ProtegoRules.PLANT_LEASH_BLOCKS) {
            // A planted dome is a place, not a leash — but the caster cannot hold one from another
            // valley either. Walking out of range dissolves it rather than breaking it.
            collapse(Collapse.CASTER_LOST);
            return;
        }
        if (ticksAlive > maxLifetime) {
            collapse(Collapse.EXPIRED);
            return;
        }

        interceptProjectiles(serverLevel);
        ProtegoFeedback.ambient(serverLevel, this, ticksAlive);
    }

    /** Mobile shields ride the caster: a disc out in front on their aim, a dome centred on them. */
    private void followCaster(Player caster) {
        if (tier().frontalOnly()) {
            Vec3 aim = horizontalAim(caster.getYRot());
            setPos(caster.getX() + aim.x * DISC_OFFSET, caster.getY(), caster.getZ() + aim.z * DISC_OFFSET);
            setYRot(caster.getYRot());
        } else {
            setPos(caster.position());
        }
    }

    /**
     * Ends the shield. Idempotent, and the only place the caster's ward bookkeeping is undone.
     *
     * <p>The effect and the registry entry are released only if they still point at <em>this</em>
     * shield. That is what makes a recast safe: the new shield may already own both by the time the
     * old one is told to go, and the old one must not take them with it (the recast self-shatter,
     * AUD-E-001, was exactly this ordering being load-bearing).
     */
    public void collapse(Collapse cause) {
        // Server-owned: the ending is synced down like every other bit of the shield's state, and a
        // client that set it locally would fight the next entity-data update.
        if (isCollapsing() || cause == Collapse.NONE || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        this.entityData.set(DATA_COLLAPSE, cause.ordinal());
        this.entityData.set(DATA_INTEGRITY, 0.0f);
        this.collapseTicks = 0;

        UUID casterId = getCasterUuid();
        ServerPlayer caster = getServerCaster(serverLevel);
        boolean ownsWard = casterId != null && ProtegoWardManager.getEntityId(casterId) == getId();
        if (ownsWard) {
            ProtegoWardManager.remove(casterId);
            if (caster != null && caster.getEffect(ModEffects.PROTEGO_SHIELD) != null) {
                caster.removeEffect(ModEffects.PROTEGO_SHIELD);
            }
        }

        if (cause.isBreach()) {
            ProtegoFeedback.breach(serverLevel, this);
            ProtegoBreach.apply(serverLevel, this, caster);
        } else {
            ProtegoFeedback.fade(serverLevel, this, cause);
        }
        ProtegoAnimationS2CPayload.sendToTracking(this, cause.isBreach() ? "shatter" : "fade");
    }

    // ── interception ────────────────────────────────────────────────────────────────────────────

    /**
     * Turns aside anything in flight that crosses into the ward this tick — spell bolts, and arrows,
     * fireballs and thrown things with them.
     *
     * <p>Swept, over each projectile's last step plus the step it is about to take, so nothing fast
     * can pass through the sphere between two samples. Anything that starts inside was loosed from
     * within the ward and is never stopped by it — the wall is a wall, not a bubble of goodwill.
     */
    private void interceptProjectiles(ServerLevel level) {
        ProtegoTier tier = tier();
        Vec3 centre = centre();
        double radius = tier.radius();
        double reach = radius + MAX_BOLT_STEP;
        AABB area = AABB.ofSize(centre, reach * 2.0, reach * 2.0, reach * 2.0);

        List<Projectile> inFlight = level.getEntitiesOfClass(Projectile.class, area, Entity::isAlive);
        for (Projectile projectile : inFlight) {
            if (isCollapsing()) {
                return;
            }
            if (answeredProjectiles.contains(projectile.getId())
                    || ProtegoWardManager.isFriendlyProjectile(this, projectile)) {
                continue;
            }
            Vec3 from = projectile.oldPosition();
            Vec3 to = projectile.position().add(projectile.getDeltaMovement());
            double t = ProtegoRules.sphereEntry(from.x, from.y, from.z, to.x, to.y, to.z,
                    centre.x, centre.y, centre.z, radius);
            if (t < 0.0) {
                continue;
            }
            Vec3 entry = from.lerp(to, t);
            if (tier.frontalOnly() && !coversDirection(entry)) {
                continue;
            }
            if (projectile instanceof SpellProjectileEntity bolt) {
                receiveSpell(level, bolt, entry, true);
            } else {
                receiveProjectile(level, projectile, entry);
            }
        }
        if (answeredProjectiles.size() > 256) {
            answeredProjectiles.clear();
        }
    }

    /**
     * Answers an ordinary projectile at the wall: it bounces off the ward, and the ward pays for it.
     *
     * <p>An arrow used to fly through a dome untouched and only cost integrity when it reached a body
     * inside, which looks exactly like a shield that does not work. The bounce is a real velocity
     * change on the server; the entity tracker syncs it, so every client sees the arrow turn at the
     * surface without a packet of our own.
     */
    public void receiveProjectile(ServerLevel level, Projectile projectile, Vec3 impact) {
        if (isCollapsing()) {
            return;
        }
        answeredProjectiles.add(projectile.getId());
        boolean dark = ProtegoDarkThreats.isDarkAttacker(projectile.getOwner())
                || ProtegoDarkThreats.isDarkAttacker(projectile);
        float cost = ProtegoRules.projectileImpactCost(tier(), estimateProjectileDamage(projectile), dark);

        Vec3 motion = projectile.getDeltaMovement();
        Vec3 normal = impact.subtract(centre());
        if (motion.lengthSqr() < 1.0e-6 || normal.lengthSqr() < 1.0e-6) {
            projectile.discard();
        } else {
            normal = normal.normalize();
            // Turned aside rather than stopped dead: a shield charm deflects, and an arrow halting in
            // mid-air reads as a bug. Damped, because the ward eats most of the energy.
            Vec3 reflected = motion.subtract(normal.scale(2.0 * motion.dot(normal))).scale(DEFLECTION_DAMPING);
            projectile.setPos(impact.add(normal.scale(0.05)));
            // No impulse flag to set on 1.21.11: ServerEntity compares deltaMovement against what it
            // last sent and pushes a motion packet on its own, which is what makes the bounce visible.
            projectile.setDeltaMovement(reflected);
        }
        ProtegoFeedback.projectileImpact(level, this, impact);
        ProtegoAnimationS2CPayload.sendToTracking(this, "deflect");
        trainCasterReflexes(getServerCaster(level));
        spendIntegrity(level, cost, impact);
    }

    /**
     * What this projectile would have done to whoever it was aimed at.
     *
     * <p>An arrow's damage is its speed times a base the class keeps to itself (there is a setter and
     * no getter), so the vanilla default stands in for it — an approximation that only decides how
     * much integrity a bounce costs. Anything that carries its own explosion is billed as a heavy
     * hit; everything else is a nuisance and pays the floor.
     */
    private static float estimateProjectileDamage(Projectile projectile) {
        double speed = projectile.getDeltaMovement().length();
        if (projectile instanceof AbstractArrow) {
            return (float) (speed * VANILLA_ARROW_BASE_DAMAGE);
        }
        if (projectile instanceof AbstractHurtingProjectile) {
            return HURTING_PROJECTILE_DAMAGE;
        }
        return 0.0f; // snowballs, eggs, pearls: the floor in projectileImpactCost covers them
    }

    /**
     * Answers one spell: swallow it, turn it, or let it pass.
     *
     * @param canDeflect false when the bolt has already reached its target and the ward is catching
     *                   it at the body — there is nothing left to reflect, so it is simply eaten.
     * @return true if the shield dealt with the bolt (it must not go on to do anything else)
     */
    public boolean receiveSpell(ServerLevel level, SpellProjectileEntity bolt, Vec3 impact, boolean canDeflect) {
        if (isCollapsing()) {
            return false;
        }
        Spell spell = bolt.getCachedOrResolveSpell();
        if (spell == null) {
            return false;
        }
        if (spell.isUnblockable()) {
            // Canon: the Killing Curse is not stopped by any shield. It flashes and goes through.
            level.sendParticles(ModParticles.AK_BYPASS_FLASH.get(),
                    impact.x, impact.y, impact.z, 8, 0.12, 0.12, 0.12, 0.01);
            answeredProjectiles.add(bolt.getId());
            return false;
        }

        answeredProjectiles.add(bolt.getId());
        ProtegoTier tier = tier();
        boolean dark = ProtegoDarkThreats.isDarkSpell(spell);
        boolean swallow = dark && tier.absorbsDark();
        float cost = ProtegoRules.spellImpactCost(tier, spell.getBaseDamage() * bolt.getDamageMultiplier(), dark);

        ServerPlayer caster = getServerCaster(level);
        if (swallow || !canDeflect) {
            bolt.discard();
        } else {
            deflect(bolt, impact, caster);
        }
        ProtegoFeedback.spellImpact(level, this, impact, swallow);
        if (swallow) {
            ProtegoAnimationS2CPayload.sendToTracking(this, "absorb");
        } else {
            ProtegoAnimationS2CPayload.sendToTracking(this, "deflect");
        }
        // A swallowed curse is as much a block as a bounced one; training only the bounce would
        // quietly punish the tier that swallows.
        trainCasterReflexes(caster);
        spendIntegrity(level, cost, impact);
        return true;
    }

    /** Reflects a bolt off the sphere at the point it entered, and hands it to the shield's caster. */
    private void deflect(SpellProjectileEntity bolt, Vec3 impact, @Nullable ServerPlayer caster) {
        Vec3 motion = bolt.getDeltaMovement();
        Vec3 normal = impact.subtract(centre());
        if (motion.lengthSqr() < 1.0e-6 || normal.lengthSqr() < 1.0e-6) {
            bolt.discard();
            return;
        }
        normal = normal.normalize();
        Vec3 reflected = motion.subtract(normal.scale(2.0 * motion.dot(normal)));
        // Put it back on the surface facing out, so it leaves rather than re-entering next tick.
        bolt.setPos(impact.add(normal.scale(0.05)));
        bolt.setDeltaMovement(reflected);
        if (caster != null) {
            bolt.setOwner(caster);
        }
    }

    // ── damage absorption ───────────────────────────────────────────────────────────────────────

    /**
     * Takes a blow aimed at someone this shield covers.
     *
     * @param from where the attack came from, for the impact flash and the T0 arc; may be null
     * @return how much of {@code amount} the ward swallowed. Anything left over is real damage and
     *         reaches the victim — an absorb pool that is nearly empty stops nearly nothing.
     */
    public float absorbDamage(ServerLevel level, float amount, @Nullable Vec3 from, boolean darkSource) {
        if (isCollapsing() || amount <= 0.0f) {
            return 0.0f;
        }
        ProtegoTier tier = tier();
        // How much of the blow the pool can stand in front of, and what that costs it — two numbers,
        // because Horribilis pays a third of a point per point of Dark damage and everything else
        // pays a premium for the same blow.
        float absorbed = ProtegoRules.absorbableDamage(tier, getIntegrity(), amount, darkSource);
        float cost = ProtegoRules.damageImpactCost(tier, absorbed, darkSource);
        Vec3 impact = surfacePointToward(from);
        ProtegoFeedback.blowImpact(level, this, impact);
        ProtegoAnimationS2CPayload.sendToTracking(this, "deflect");
        spendIntegrity(level, cost, impact);
        return absorbed;
    }

    /** One place where integrity leaves the pool, so warning, sound and breach cannot disagree. */
    private void spendIntegrity(ServerLevel level, float cost, Vec3 at) {
        float max = getMaxIntegrity();
        float before = getIntegrity();
        float after = Math.max(0.0f, before - Math.max(0.0f, cost));
        this.entityData.set(DATA_INTEGRITY, after);

        if (!warnedLowIntegrity && ProtegoRules.crossedLowIntegrity(before, after, max)) {
            warnedLowIntegrity = true;
            ProtegoFeedback.strain(level, this, at);
        }
        if (after <= 0.0f) {
            collapse(Collapse.BREACHED);
        }
    }

    private void trainCasterReflexes(@Nullable ServerPlayer caster) {
        if (caster != null) {
            at.koopro.wizardsandbeasts.stats.StatTraining.onSpellDeflected(caster);
        }
    }

    // ── coverage ────────────────────────────────────────────────────────────────────────────────

    /**
     * Whether this shield stands between {@code victim} and an attack.
     *
     * <p>Three rules, in order: a caster's own charm never blocks the caster's own attacks; the
     * caster is covered unless they have walked out of their own planted dome; anyone else is
     * covered only by a dome tier, only while inside it, and only if the caster counts them a
     * friend ({@link ProtegoWardManager#isAlly}).
     */
    public boolean protects(LivingEntity victim, @Nullable Entity attacker, @Nullable Vec3 from) {
        if (isCollapsing() || getIntegrity() <= 0.0f || victim.level() != level()) {
            return false;
        }
        UUID casterId = getCasterUuid();
        if (casterId == null) {
            return false;
        }
        if (attacker != null && casterId.equals(attacker.getUUID())) {
            return false;
        }
        if (casterId.equals(victim.getUUID())) {
            if (isPlanted() && !covers(victim)) {
                return false;
            }
        } else {
            ProtegoTier tier = tier();
            if (!tier.coversAllies() || !covers(victim)) {
                return false;
            }
            Player caster = findCaster();
            if (caster == null || !ProtegoWardManager.isAlly(caster, victim)) {
                return false;
            }
        }
        return !tier().frontalOnly() || from == null || coversDirection(from);
    }

    /** Whether a point stands inside the warded sphere. */
    public boolean covers(Entity entity) {
        return covers(entity.getBoundingBox().getCenter());
    }

    public boolean covers(Vec3 point) {
        double r = tier().radius();
        return centre().distanceToSqr(point) <= r * r;
    }

    /** Frontal tiers only cover the arc the caster faces; dome tiers cover every direction. */
    private boolean coversDirection(Vec3 worldPoint) {
        Vec3 aim = horizontalAim(getYRot());
        Vec3 to = worldPoint.subtract(centre());
        return ProtegoRules.isFrontal(aim.x, aim.z, to.x, to.z);
    }

    /** Centre of the protected sphere: the caster's body, not the disc hanging in front of them. */
    public Vec3 centre() {
        Vec3 base = position().add(0.0, CENTRE_LIFT, 0.0);
        if (!tier().frontalOnly()) {
            return base;
        }
        Vec3 aim = horizontalAim(getYRot());
        return base.subtract(aim.scale(DISC_OFFSET));
    }

    /** Where to draw an impact for an attack arriving from {@code from}: on the ward, facing it. */
    private Vec3 surfacePointToward(@Nullable Vec3 from) {
        Vec3 centre = centre();
        if (from == null) {
            return centre;
        }
        Vec3 direction = from.subtract(centre);
        if (direction.lengthSqr() < 1.0e-6) {
            return centre;
        }
        return centre.add(direction.normalize().scale(tier().radius() * 0.9));
    }

    private static Vec3 horizontalAim(float yRot) {
        double radians = yRot * Mth.DEG_TO_RAD;
        return new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
    }

    // ── accessors ───────────────────────────────────────────────────────────────────────────────

    public ProtegoTier tier() {
        return ProtegoTier.byIndex(this.entityData.get(DATA_TIER));
    }

    /** Raw synced tier index, for payloads and render data. */
    public int getTier() {
        return this.entityData.get(DATA_TIER);
    }

    public boolean isPlanted() {
        return this.entityData.get(DATA_PLANTED);
    }

    public float getIntegrity() {
        return this.entityData.get(DATA_INTEGRITY);
    }

    public float getMaxIntegrity() {
        return Math.max(1.0f, this.entityData.get(DATA_MAX_INTEGRITY));
    }

    /** 1 at full pool, 0 at breach — what the renderer and the strain sounds read. */
    public float integrityFraction() {
        return Mth.clamp(getIntegrity() / getMaxIntegrity(), 0.0f, 1.0f);
    }

    public Collapse collapseCause() {
        return Collapse.byIndex(this.entityData.get(DATA_COLLAPSE));
    }

    public boolean isCollapsing() {
        return collapseCause() != Collapse.NONE;
    }

    /** Ticks since this shield started coming apart; 0 while it is still up. */
    public int collapseTicks() {
        return collapseTicks;
    }

    /** Radius this shield covers — the same number the renderer scales to. */
    public double coverRadius() {
        return tier().radius();
    }

    public @Nullable UUID getCasterUuid() {
        String raw = this.entityData.get(DATA_CASTER_UUID);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** The caster as seen from either side — the client needs them too, to follow them smoothly. */
    public @Nullable Player findCaster() {
        UUID casterId = getCasterUuid();
        return casterId == null ? null : level().getPlayerByUUID(casterId);
    }

    public @Nullable ServerPlayer getServerCaster(ServerLevel level) {
        UUID casterId = getCasterUuid();
        return casterId == null ? null : level.getServer().getPlayerList().getPlayer(casterId);
    }

    // ── entity plumbing ─────────────────────────────────────────────────────────────────────────

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
    public void move(MoverType type, Vec3 movement) {
        // The shield is placed, never pushed — it follows its caster or holds its planted spot.
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    /** Transient: a shield that survived a restart would have no cast behind it. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        // TRANSIENT: not saved
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        // TRANSIENT: not saved
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<ProtegoShieldEntity>("shield_controller", 2, this::shieldAnimController)
                .triggerableAnim("deflect", RawAnimation.begin().thenPlay("deflect"))
                .triggerableAnim("shatter", RawAnimation.begin().thenPlay("shatter"))
                .triggerableAnim("fade", RawAnimation.begin().thenPlay("fade"))
                .triggerableAnim("absorb", RawAnimation.begin().thenPlay("horribilis_absorb")));
    }

    private PlayState shieldAnimController(AnimationTest<ProtegoShieldEntity> test) {
        return test.setAndContinue(tier().frontalOnly() ? IDLE_DISC : IDLE_DOME);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
