package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.bond.BondState;
import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.creature.bond.FollowBondedOwnerGoal;
import at.koopro.wizardsandbeasts.creature.wildlife.CreatureSign;
import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules;
import at.koopro.wizardsandbeasts.entity.BeastNavigation;
import at.koopro.wizardsandbeasts.entity.GeoEntityBase;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.util.AnimHelper;
import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Phoenix — a magnificent scarlet bird that lives for centuries, bursts into flame when its body fails, and rises
 * again from the ashes as a chick.
 *
 * <p><b>It does not die.</b> A blow that would kill it sets it alight instead: it is reborn small and grows back over
 * {@link WildlifeRules#REBIRTH_GROWTH_TICKS}. Only damage that nothing survives — the void, a command — ends it.
 * Nothing drops, because nothing has died. Anyone who sees it happen has seen what a phoenix is.
 *
 * <p><b>It is loyal to those who show loyalty.</b> Trust is the bond system's
 * ({@code creature_bonds/phoenix.json}): a player who kills something that just hurt the phoenix earns its notice and
 * becomes the one it attaches to; calm company deepens that; hurting it breaks it. A loyal phoenix weeps healing tears
 * over its person when they are badly hurt, follows them, and gives them a feather now and then. How loyalty is first
 * shown is a gameplay reading; canon says only that phoenixes are loyal to those who are loyal (<i>Chamber of Secrets</i>).
 *
 * <p><b>It sheds.</b> A calm phoenix leaves a feather behind about once a day, so a wand core is found where phoenixes
 * live rather than taken from a body.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public class PhoenixEntity extends GeoEntityBase implements BondableBeast {

    private static final RawAnimation IDLE_ANIM = AnimHelper.loop("phoenix", "idle");
    private static final RawAnimation FLY_ANIM = AnimHelper.loop("phoenix", "fly");

    private static final EntityDataAccessor<Integer> DATA_BOND_LEVEL =
            SynchedEntityData.defineId(PhoenixEntity.class, EntityDataSerializers.INT);

    public static final Identifier FEATHER = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "phoenix_feather");

    /** Bond at which a phoenix weeps for its person. */
    public static final int TEARS_BOND = 60;
    /** Bond earned by killing what just hurt it. */
    public static final int DEFENDED_BOND = 20;
    /** A calm phoenix sheds a feather this often. */
    public static final int SHED_TICKS = 24000;
    /** How near its person must be for its tears to reach them. */
    public static final double TEARS_RANGE = 16.0;

    private static final String KEY_REBIRTH = "RebirthTick";
    private static final String KEY_SHED = "ShedTicks";
    private static final String KEY_TEARS = "TearsCooldown";

    private final BondState bond = new BondState();
    /** Age in ticks at the last rebirth, or -1 if it has not been reborn in this body's memory. */
    private long rebirthAge = -1L;
    private int shedTicks = SHED_TICKS;
    private int tearsCooldown = 0;

    public PhoenixEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 10, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.SCALE, 1.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return BeastNavigation.flying(this, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BOND_LEVEL, 0);
    }

    @Override
    protected void registerGoals() {
        // Keeps well away from whoever last hurt it.
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class,
                living -> living == getLastHurtByMob(), 16.0f, 1.2, 1.5, EntitySelector.NO_SPECTATORS));
        goalSelector.addGoal(2, new FollowBondedOwnerGoal<>(this));
        goalSelector.addGoal(3, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    // ── bond ────────────────────────────────────────────────────────────────

    @Override
    public @NonNull BondState bondState() {
        return bond;
    }

    @Override
    public void setSyncedBondLevel(int level) {
        entityData.set(DATA_BOND_LEVEL, level);
    }

    @Override
    public int getSyncedBondLevel() {
        return entityData.get(DATA_BOND_LEVEL);
    }

    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            onBondedHurt(source);
        }
        return hurt;
    }

    // ── rebirth ─────────────────────────────────────────────────────────────

    /** Whether a phoenix rises again from this: anything short of the void or a command. */
    public static boolean rebornFrom(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public void die(@NonNull DamageSource cause) {
        if (level() instanceof ServerLevel level && rebornFrom(cause) && ModuleManager.isEnabled(Module.CREATURES)) {
            if (cause.getEntity() instanceof ServerPlayer player) {
                BestiaryDiscoveryHandler.encountered(player, this);
            }
            rebirth(level);
            return;
        }
        super.die(cause);
    }

    /** Bursts into flame and rises from the ashes. */
    public void rebirth(ServerLevel level) {
        setHealth(getMaxHealth());
        removeAllEffects();
        clearFire();
        rebirthAge = tickCount;
        applyScale(WildlifeRules.REBORN_SCALE);
        level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.5, getZ(), 60, 0.6, 0.8, 0.6, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 20, 0.4, 0.2, 0.4, 0.01);
        level.playSound(null, blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL, 1.0f, 0.7f);
        BestiaryDiscoveryHandler.signatureSeenByNearby(this, 32.0);
    }

    public boolean isReborn() {
        return rebirthAge >= 0L && tickCount - rebirthAge < WildlifeRules.REBIRTH_GROWTH_TICKS;
    }

    private void applyScale(float scale) {
        AttributeInstance attribute = getAttribute(Attributes.SCALE);
        if (attribute != null && attribute.getBaseValue() != scale) {
            attribute.setBaseValue(scale);
        }
    }

    // ── tick ────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        tickBond();
        if (!(level() instanceof ServerLevel level) || !ModuleManager.isEnabled(Module.CREATURES)) {
            return;
        }
        if (rebirthAge >= 0L) {
            float scale = WildlifeRules.rebirthScale(tickCount - rebirthAge);
            applyScale(scale);
            if (scale >= 1.0f) {
                rebirthAge = -1L;
            }
        }
        if (tearsCooldown > 0) {
            tearsCooldown--;
        }
        if (shedTicks > 0) {
            shedTicks--;
        }
        if (tickCount % 20 == 0) {
            weepIfNeeded(level);
            shedIfDue();
        }
    }

    /** A loyal phoenix's tears heal its person when they are badly hurt. */
    public boolean weepIfNeeded(ServerLevel level) {
        if (tearsCooldown > 0 || bondLevel() < TEARS_BOND) {
            return false;
        }
        Player owner = resolveBondOwner();
        if (owner == null || !owner.isAlive() || owner.distanceToSqr(this) > TEARS_RANGE * TEARS_RANGE
                || owner.getHealth() > owner.getMaxHealth() * WildlifeRules.TEARS_HEALTH_FRACTION) {
            return false;
        }
        owner.heal(owner.getMaxHealth() * 0.5f);
        owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        level.sendParticles(ParticleTypes.FALLING_WATER, owner.getX(), owner.getY() + owner.getBbHeight(), owner.getZ(),
                24, 0.3, 0.2, 0.3, 0.0);
        level.playSound(null, owner.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL,
                1.0f, 1.6f);
        tearsCooldown = WildlifeRules.TEARS_COOLDOWN_TICKS;
        return true;
    }

    private void shedIfDue() {
        boolean calm = getLastHurtByMob() == null || tickCount - getLastHurtByMobTimestamp() > 600;
        Item feather = BuiltInRegistries.ITEM.getValue(FEATHER);
        if (calm && feather != null && WildlifeRules.shedDue(shedTicks, CreatureSign.lyingNearby(this, feather))) {
            CreatureSign.leave(this, feather, 1);
            shedTicks = SHED_TICKS;
        }
    }

    // ── loyalty shown ───────────────────────────────────────────────────────

    /** A player who kills what just hurt a phoenix has shown it loyalty. */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || !(event.getEntity().level() instanceof ServerLevel level)
                || !ModuleManager.isEnabled(Module.CREATURES)) {
            return;
        }
        LivingEntity slain = event.getEntity();
        for (PhoenixEntity phoenix : level.getEntitiesOfClass(PhoenixEntity.class,
                player.getBoundingBox().inflate(TEARS_RANGE))) {
            if (phoenix.getLastHurtByMob() == slain && phoenix.tickCount - phoenix.getLastHurtByMobTimestamp() < 200) {
                phoenix.defendedBy(player);
            }
        }
    }

    public void defendedBy(Player player) {
        increaseBond(player, DEFENDED_BOND, true);
    }

    // ── save ────────────────────────────────────────────────────────────────

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        saveBond(output);
        output.store(KEY_SHED, Codec.INT, shedTicks);
        output.store(KEY_TEARS, Codec.INT, tearsCooldown);
        if (rebirthAge >= 0L) {
            // Stored as growth remaining, because tickCount restarts at zero when the entity is loaded.
            output.store(KEY_REBIRTH, Codec.LONG, tickCount - rebirthAge);
        }
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        loadBond(input);
        shedTicks = input.read(KEY_SHED, Codec.INT).orElse(SHED_TICKS);
        tearsCooldown = input.read(KEY_TEARS, Codec.INT).orElse(0);
        @Nullable Long grown = input.read(KEY_REBIRTH, Codec.LONG).orElse(null);
        rebirthAge = grown == null ? -1L : tickCount - grown;
        if (grown != null) {
            applyScale(WildlifeRules.rebirthScale(grown));
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(AnimHelper.movementController("phoenix", 5, IDLE_ANIM, FLY_ANIM));
    }
}
