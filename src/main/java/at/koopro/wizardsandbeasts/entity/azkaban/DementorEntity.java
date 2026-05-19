package at.koopro.wizardsandbeasts.entity.azkaban;

import at.koopro.wizardsandbeasts.azkaban.AzkabanDamageTypes;
import at.koopro.wizardsandbeasts.entity.azkaban.goal.DementorKissGoal;
import at.koopro.wizardsandbeasts.entity.azkaban.goal.DementorPatrolGoal;
import at.koopro.wizardsandbeasts.entity.azkaban.goal.DementorPursueGoal;
import at.koopro.wizardsandbeasts.entity.azkaban.goal.FleeFromPatronusGoal;
import at.koopro.wizardsandbeasts.azkaban.structure.AzkabanStructures;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.spell.patronus.PatronusDetection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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

import java.util.ArrayList;
import java.util.List;

/**
 * The Dementor — blind, immortal, emotion-sensing prison guardian.
 *
 * <p>Canon references: HP&PoA chs. 5, 10, 12, 19, 20; Pottermore "Dementors" article.
 */
public class DementorEntity extends Monster implements GeoEntity {

    // --- Animations ---
    private static final RawAnimation IDLE_ANIM    = RawAnimation.begin().thenLoop("animation.dementor.idle_drift");
    private static final RawAnimation PURSUE_ANIM  = RawAnimation.begin().thenLoop("animation.dementor.pursue");
    private static final RawAnimation REPELLED_ANIM  = RawAnimation.begin().thenPlayAndHold("animation.dementor.repelled");
    private static final RawAnimation KISS_WINDUP  = RawAnimation.begin().thenPlayAndHold("animation.dementor.kiss_windup");
    private static final RawAnimation KISS_RESOLVE = RawAnimation.begin().thenPlayAndHold("animation.dementor.kiss_resolve");
    private static final RawAnimation DISSIPATE    = RawAnimation.begin().thenPlayAndHold("animation.dementor.dissipate");

    // GeckoLib
    private final AnimatableInstanceCache animCache = GeckoLibUtil.createInstanceCache(this);

    // Synced behavior flags — server AI goals set these; client reads them for animation selection
    private static final EntityDataAccessor<Boolean> DATA_KISS_WINDUP =
            SynchedEntityData.defineId(DementorEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_KISS_RESOLVE =
            SynchedEntityData.defineId(DementorEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_REPELLED =
            SynchedEntityData.defineId(DementorEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_DISSIPATING =
            SynchedEntityData.defineId(DementorEntity.class, EntityDataSerializers.BOOLEAN);

    // Kiss cooldown (server-only, not synced — goals read it directly)
    private int kissCooldown = 0;

    // Server spawner
    private static final int SPAWN_INTERVAL_TICKS = 600; // 30 s
    private static final int DEMENTOR_CAP_BASE    = 12;
    private static final int DEMENTOR_CAP_PER_PLAYER = 2;
    private static final int DEMENTOR_CAP_MAX     = 24;
    private int spawnCheckCooldown = 0;

    // Memory voice cooldown (client-side)
    private int voiceCooldown = 0;

    public DementorEntity(EntityType<? extends DementorEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.moveControl = new FlyingMoveControl(this, 10, true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NonNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_KISS_WINDUP, false);
        builder.define(DATA_KISS_RESOLVE, false);
        builder.define(DATA_REPELLED, false);
        builder.define(DATA_DISSIPATING, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1024.0)
                .add(Attributes.MOVEMENT_SPEED, 0.45)
                .add(Attributes.FLYING_SPEED, 0.45)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ATTACK_DAMAGE, 0.0);
    }

    @Override
    protected @NonNull PathNavigation createNavigation(@NonNull Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FleeFromPatronusGoal(this));
        this.goalSelector.addGoal(2, new DementorKissGoal(this));
        this.goalSelector.addGoal(3, new DementorPursueGoal(this));
        this.goalSelector.addGoal(4, new DementorPatrolGoal(this));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    // -------------------------------------------------------------------------
    // Immortality — Dementors cannot be killed
    // -------------------------------------------------------------------------

    @Override
    public boolean isInvulnerableTo(@NonNull ServerLevel level, @NonNull DamageSource source) {
        // Only the internal admin dissipate type bypasses immunity
        return !source.is(AzkabanDamageTypes.DEMENTOR_DISSIPATE);
    }

    @Override
    public void die(@NonNull DamageSource cause) {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                    getX(), getY() + 1.0, getZ(), 8, 0.3, 0.5, 0.3, 0.02);
        }
        super.die(cause);
    }

    // -------------------------------------------------------------------------
    // Main tick
    // -------------------------------------------------------------------------

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            clientTick();
            return;
        }
        if (!ModuleManager.isEnabled(Module.AZKABAN)) return;

        if (kissCooldown > 0) kissCooldown--;

        // Aura: every 20 ticks
        if (tickCount % 20 == 0) {
            applyEnvironmentalAura();
            applyChillToNearbyPlayers();
        }

        // Despair-driven spawner: every ~30 s, if inside Azkaban
        if (spawnCheckCooldown-- <= 0) {
            spawnCheckCooldown = SPAWN_INTERVAL_TICKS;
            trySpawnAdditionalDementor();
        }
    }

    // -------------------------------------------------------------------------
    // Environmental aura — §6.4
    // -------------------------------------------------------------------------

    private void applyEnvironmentalAura() {
        if (!(level() instanceof ServerLevel sl)) return;
        BlockPos center = blockPosition();
        int r = 8;
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-r, -1, -r), center.offset(r, 1, r))) {
            BlockState state = sl.getBlockState(pos);
            // Freeze surface water
            if (state.is(Blocks.WATER)) {
                if (sl.canSeeSky(pos)) {
                    sl.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
                }
            }
            // Wilt crops
            if (state.getBlock() instanceof CropBlock crop) {
                int age = state.getValue(CropBlock.AGE);
                if (age == 0) {
                    sl.removeBlock(pos, false);
                } else {
                    sl.setBlockAndUpdate(pos, state.setValue(CropBlock.AGE, age - 1));
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // DEMENTOR_CHILL aura — §6.5, §7.1
    // -------------------------------------------------------------------------

    private void applyChillToNearbyPlayers() {
        if (!(level() instanceof ServerLevel sl)) return;

        // Collect all players within 24 blocks (emotion sense — no LOS)
        List<Player> nearPlayers = sl.getEntitiesOfClass(Player.class,
                getBoundingBox().inflate(24), LivingEntity::isAlive);

        for (Player player : nearPlayers) {
            if (PatronusDetection.isPatronusNearby(level(), player.position(), 24.0)) continue;

            // Count Dementors within 16 blocks of this player
            int dementorCount = sl.getEntitiesOfClass(DementorEntity.class,
                    player.getBoundingBox().inflate(16), LivingEntity::isAlive).size();
            int amplifier = Math.min(dementorCount - 1, 3);
            if (amplifier < 0) continue;

            player.addEffect(new MobEffectInstance(
                    ModEffects.DEMENTOR_CHILL, 60, amplifier, false, true, true));
        }
    }

    // -------------------------------------------------------------------------
    // Memory voices — §6.11 (client-side)
    // -------------------------------------------------------------------------

    private void clientTick() {
        var localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null || !localPlayer.hasEffect(ModEffects.DEMENTOR_CHILL)) return;
        if (voiceCooldown-- <= 0) {
            voiceCooldown = 80 + random.nextInt(120);
            // TODO: replace with custom whisper sounds in future audio prompt
            level().playLocalSound(localPlayer.getX(), localPlayer.getY(), localPlayer.getZ(),
                    SoundEvents.AMBIENT_CAVE.value(), getSoundSource(), 0.15f,
                    0.4f + random.nextFloat() * 0.2f, false);
        }
    }

    // -------------------------------------------------------------------------
    // Despair-driven spawner — §6.7
    // -------------------------------------------------------------------------

    private void trySpawnAdditionalDementor() {
        if (!(level() instanceof ServerLevel sl)) return;
        if (!AzkabanStructures.isInsideAzkabanArea(sl, getX(), getZ(), 16)) return;

        // Count players inside Azkaban
        List<Player> insidePlayers = sl.getEntitiesOfClass(Player.class,
                getBoundingBox().inflate(96), p -> AzkabanStructures.isInsideAzkabanArea(sl, p.getX(), p.getZ(), 32));
        if (insidePlayers.isEmpty()) return;

        int cap = Math.min(DEMENTOR_CAP_BASE + insidePlayers.size() * DEMENTOR_CAP_PER_PLAYER, DEMENTOR_CAP_MAX);
        AABB searchBox = getBoundingBox().inflate(200);
        int existing = sl.getEntitiesOfClass(DementorEntity.class, searchBox, LivingEntity::isAlive).size();
        if (existing >= cap) return;

        // Spawn near high-security wing (upper fortress)
        BlockPos spawnPos = pickSpawnPoint(sl);
        if (spawnPos == null) return;
        DementorEntity newDementor = new DementorEntity(ModEntities.DEMENTOR.get(), sl);
        newDementor.setPos(spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5);
        sl.addFreshEntity(newDementor);
    }

    private @Nullable BlockPos pickSpawnPoint(ServerLevel sl) {
        BlockPos center = AzkabanStructures.cachedFortressCenter;
        if (center == null) return null;
        // Prefer upper floors (high-security wing)
        int dy = random.nextInt(3) == 0 ? 60 + random.nextInt(20) : random.nextInt(50);
        return center.offset(
                random.nextInt(14) - 7,
                dy,
                random.nextInt(14) - 7);
    }

    // -------------------------------------------------------------------------
    // Felt-player API (blind emotion sensing — no LOS)
    // -------------------------------------------------------------------------

    /**
     * Returns all living players within {@code radius} blocks regardless of
     * line-of-sight. Masked players (Patronus nearby) are excluded.
     */
    public List<Player> getFeltPlayers(double radius) {
        List<Player> all = level().getEntitiesOfClass(
                Player.class, getBoundingBox().inflate(radius), LivingEntity::isAlive);
        List<Player> felt = new ArrayList<>();
        for (Player p : all) {
            if (!PatronusDetection.isPatronusNearby(level(), p.position(), 24.0)) {
                felt.add(p);
            }
        }
        return felt;
    }

    // -------------------------------------------------------------------------
    // Kiss state accessors (used by goals + renderer)
    // -------------------------------------------------------------------------

    public int  getKissCooldown() { return kissCooldown; }
    public void resetKissCooldown() { kissCooldown = 300; }
    public void setKissWindup(boolean v)  { entityData.set(DATA_KISS_WINDUP, v); }
    public void setKissResolve(boolean v) { entityData.set(DATA_KISS_RESOLVE, v); }
    public boolean isKissWindup()  { return entityData.get(DATA_KISS_WINDUP); }
    public boolean isKissResolve() { return entityData.get(DATA_KISS_RESOLVE); }
    public void setRepelled(boolean v)    { entityData.set(DATA_REPELLED, v); }
    public boolean isRepelled()           { return entityData.get(DATA_REPELLED); }
    public void setDissipating(boolean v) { entityData.set(DATA_DISSIPATING, v); }
    public boolean isDissipating()        { return entityData.get(DATA_DISSIPATING); }

    // -------------------------------------------------------------------------
    // GeckoLib — §6.2
    // -------------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<DementorEntity>("main", 3, this::animController));
    }

    private PlayState animController(AnimationTest<DementorEntity> test) {
        if (isDissipating()) return test.setAndContinue(DISSIPATE);
        if (isRepelled())    return test.setAndContinue(REPELLED_ANIM);
        if (isKissResolve()) return test.setAndContinue(KISS_RESOLVE);
        if (isKissWindup())  return test.setAndContinue(KISS_WINDUP);
        if (!getFeltPlayers(24.0).isEmpty()) return test.setAndContinue(PURSUE_ANIM);
        return test.setAndContinue(IDLE_ANIM);
    }

    @Override
    public @NonNull AnimatableInstanceCache getAnimatableInstanceCache() {
        return animCache;
    }

    // -------------------------------------------------------------------------
    // Misc overrides
    // -------------------------------------------------------------------------

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        // Persistent inside Azkaban, despawn normally outside
        if (level() instanceof ServerLevel sl) {
            return !AzkabanStructures.isInsideAzkabanArea(sl, getX(), getZ(), 16);
        }
        return true;
    }

    @Override
    public void checkDespawn() {
        // Dementors are immortal — never despawn automatically
    }
}
