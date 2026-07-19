package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.creature.CreatureDefinition;
import at.koopro.wizardsandbeasts.creature.CreatureDefinitionRegistry;
import at.koopro.wizardsandbeasts.creature.Temperament;
import at.koopro.wizardsandbeasts.creature.Trait;
import at.koopro.wizardsandbeasts.creature.ability.CreatureAbility;
import at.koopro.wizardsandbeasts.creature.ability.FireAffinity;
import at.koopro.wizardsandbeasts.entity.GeoEntityBase;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared base for data-configured GeckoLib creatures. The concrete {@code EntityType} is registered
 * per creature ({@code ModCreatures}); this class learns which creature it is at runtime from its
 * {@code EntityType} registry key, then drives attributes, goals and on-hit effects from its
 * {@link CreatureDefinition} ({@link Temperament} + {@link Trait} vocabulary). No bespoke per-creature
 * subclass exists — only the four locomotion subclasses, which supply movement goals + animation.
 */
public abstract class GenericBeastEntity extends GeoEntityBase {

    /** Niffler-grade theft: stacks lifted from players, carried until the beast dies. */
    private static final int MAX_CARRIED = 8;
    private final List<ItemStack> carried = new ArrayList<>();

    @Nullable
    private Set<Trait> cachedTraits;

    /**
     * Per-entity scratch counters for the ability layer. {@code fireDryTicks}/{@code waterDryTicks} are the
     * {@link FireAffinity}/{@code WaterAffinity} dry-out timers. Cooldown-gated abilities each own a private
     * string key into {@link #abilityCooldowns} (blink, camouflage reveal, signature bursts) so multiple
     * cooldown abilities on one beast never collide. Persisted via save data (mirroring {@code CarriedLoot});
     * abilities own the read/write, the entity owns storage.
     */
    private int fireDryTicks;
    private int waterDryTicks;
    private final Map<String, Integer> abilityCooldowns = new HashMap<>();

    /** Codec for the keyed cooldown map's save form ({@code {key:ticks}}). */
    private static final Codec<Map<String, Integer>> COOLDOWNS_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT);

    /**
     * Render-only model scale, synced to the client for the Occamy's choranaptyxic size-shift (and any
     * future size ability). Default {@code 1.0} = no change; the hitbox stays registry-frozen (this never
     * touches collision), surfaced to the renderer via render-state — never read live at render time.
     */
    private static final EntityDataAccessor<Float> DATA_RENDER_SCALE =
            SynchedEntityData.defineId(GenericBeastEntity.class, EntityDataSerializers.FLOAT);

    /**
     * Render-only ARGB tint, synced to the client for {@code Tint}-style colour abilities (obscurus smoke,
     * ashwinder ember-pulse). Default {@code 0xFFFFFFFF} (opaque white) = no tint; multiplied into the model
     * colour by the renderer through render-state, never read live at render time.
     */
    private static final EntityDataAccessor<Integer> DATA_TINT =
            SynchedEntityData.defineId(GenericBeastEntity.class, EntityDataSerializers.INT);

    /**
     * Render-only disguise flag, synced to the client, for {@code LureDisguise}-style abilities (the
     * Kelpie's tame-horse guise). Default {@code false} = true form. Also gates {@link #mobInteract} —
     * while disguised and riderless, interacting mounts the player, matching a normal rideable mob.
     */
    private static final EntityDataAccessor<Boolean> DATA_DISGUISED =
            SynchedEntityData.defineId(GenericBeastEntity.class, EntityDataSerializers.BOOLEAN);

    protected GenericBeastEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        if (!level.isClientSide()) {
            applyDefinition();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_RENDER_SCALE, 1.0f);
        builder.define(DATA_TINT, 0xFFFFFFFF);
        builder.define(DATA_DISGUISED, false);
    }

    /** Render-only model scale (1.0 = unscaled). Set server-side by size abilities, read on the client renderer. */
    public float getRenderScale() {
        return this.entityData.get(DATA_RENDER_SCALE);
    }

    public void setRenderScale(float scale) {
        this.entityData.set(DATA_RENDER_SCALE, scale);
    }

    /** Render-only ARGB tint (0xFFFFFFFF = none). Set server-side by colour abilities, read on the renderer. */
    public int getTint() {
        return this.entityData.get(DATA_TINT);
    }

    public void setTint(int argb) {
        this.entityData.set(DATA_TINT, argb);
    }

    /** True while a {@code LureDisguise} ability has this beast hiding as something harmless. */
    public boolean isDisguised() {
        return this.entityData.get(DATA_DISGUISED);
    }

    public void setDisguised(boolean disguised) {
        this.entityData.set(DATA_DISGUISED, disguised);
    }

    @Override
    protected net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        if (isDisguised() && getPassengers().isEmpty() && !level().isClientSide()) {
            player.startRiding(this);
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    /** The creature id == this entity type's registry key (e.g. {@code wizards_and_beasts:unicorn}). */
    public Identifier creatureId() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(getType());
    }

    /** Short path used for the animation/model/texture name convention (e.g. {@code unicorn}). */
    protected String assetName() {
        return creatureId().getPath();
    }

    @Nullable
    protected CreatureDefinition definition() {
        return CreatureDefinitionRegistry.get(creatureId());
    }

    protected Temperament temperament() {
        CreatureDefinition def = definition();
        return def != null ? def.temperament() : Temperament.NEUTRAL;
    }

    protected Set<Trait> traits() {
        if (cachedTraits == null) {
            CreatureDefinition def = definition();
            cachedTraits = (def == null || def.traits().isEmpty())
                    ? EnumSet.noneOf(Trait.class)
                    : EnumSet.copyOf(def.traits());
        }
        return cachedTraits;
    }

    public boolean has(Trait trait) {
        return traits().contains(trait);
    }

    // ── ability layer ─────────────────────────────────────────────────────────

    /** This creature's datapack-declared abilities (empty when the definition is missing or omits them). */
    protected List<CreatureAbility> abilities() {
        CreatureDefinition def = definition();
        return def != null ? def.abilities() : List.of();
    }

    /** {@link FireAffinity} dry-out counter accessor — owned here, mutated by the ability. */
    public int getFireDryTicks() {
        return fireDryTicks;
    }

    public void setFireDryTicks(int ticks) {
        this.fireDryTicks = ticks;
    }

    /** {@code WaterAffinity} dry-out counter accessor. */
    public int getWaterDryTicks() {
        return waterDryTicks;
    }

    public void setWaterDryTicks(int ticks) {
        this.waterDryTicks = ticks;
    }

    /**
     * Remaining ticks on the named ability cooldown ({@code 0} when absent). Each cooldown-gated ability
     * owns a private key so abilities on the same beast never share a timer. Decremented each server tick.
     */
    public int getCooldown(String key) {
        return abilityCooldowns.getOrDefault(key, 0);
    }

    /** Arm (or clear, when {@code ticks <= 0}) the named ability cooldown. */
    public void setCooldown(String key, int ticks) {
        if (ticks > 0) {
            abilityCooldowns.put(key, ticks);
        } else {
            abilityCooldowns.remove(key);
        }
    }

    // ── attributes ──────────────────────────────────────────────────────────

    /** Override the registered baseline attributes with this creature's definition values (server-side). */
    protected void applyDefinition() {
        CreatureDefinition def = definition();
        if (def == null) {
            return;
        }
        setBase(Attributes.MAX_HEALTH, def.maxHealth());
        setBase(Attributes.MOVEMENT_SPEED, def.movementSpeed());
        setBase(Attributes.FOLLOW_RANGE, def.followRange());
        if (def.flyingSpeed() > 0) {
            setBase(Attributes.FLYING_SPEED, def.flyingSpeed());
        }
        if (def.attackDamage() > 0) {
            setBase(Attributes.ATTACK_DAMAGE, def.attackDamage());
        }
        setHealth(getMaxHealth());
    }

    private void setBase(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    // ── goals ───────────────────────────────────────────────────────────────

    @Override
    protected final void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        addMovementGoals();
        wireBehaviourGoals();
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 7.0f));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        // Ability-contributed goals. Always registered; each goal self-gates on Module.CREATURES at canUse().
        for (CreatureAbility ability : abilities()) {
            ability.registerGoals(this, goalSelector);
        }
    }

    /** Subclass hook: add the locomotion-specific wander goal (stroll / fly / swim). */
    protected abstract void addMovementGoals();

    private void wireBehaviourGoals() {
        Temperament temperament = temperament();
        boolean canMelee = definition() != null && definition().attackDamage() > 0;
        boolean charge = has(Trait.CHARGE);

        if (temperament == Temperament.PASSIVE || has(Trait.FEARFUL)) {
            goalSelector.addGoal(1, new PanicGoal(this, 1.4));
        }
        if (has(Trait.FEARFUL)) {
            goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 8.0f, 1.0, 1.3));
        }
        if (has(Trait.FIRE_BREATH)) {
            goalSelector.addGoal(3, new at.koopro.wizardsandbeasts.entity.creature.ai.BreatheFireGoal(this));
        }
        if (has(Trait.DEATH_GAZE)) {
            goalSelector.addGoal(3, new at.koopro.wizardsandbeasts.entity.creature.ai.DeathGazeGoal(this));
        }
        if (has(Trait.COCKCROW_WEAKNESS)) {
            goalSelector.addGoal(3, new at.koopro.wizardsandbeasts.entity.creature.ai.RoosterCrowWeaknessGoal(this));
        }
        if (canMelee && temperament != Temperament.PASSIVE) {
            goalSelector.addGoal(4, new MeleeAttackGoal(this, charge ? 1.45 : 1.2, true));
            HurtByTargetGoal retaliate = new HurtByTargetGoal(this);
            if (has(Trait.PACK)) {
                retaliate.setAlertOthers();
            }
            targetSelector.addGoal(1, retaliate);
            if (temperament == Temperament.HOSTILE) {
                targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
            }
        }
    }

    // ── combat / trait effects ────────────────────────────────────────────────

    @Override
    public boolean fireImmune() {
        return has(Trait.FIRE_IMMUNE) || hasFireImmuneAbility() || super.fireImmune();
    }

    /** True if any {@link FireAffinity} ability declares fire immunity (so fire/lava damage is ignored). */
    private boolean hasFireImmuneAbility() {
        for (CreatureAbility ability : abilities()) {
            if (ability instanceof FireAffinity fire && fire.fireImmune()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity victim) {
            applyHitTraits(victim);
            if (ModuleManager.isEnabled(Module.CREATURES)) {
                for (CreatureAbility ability : abilities()) {
                    ability.onMeleeContact(this, victim);
                }
            }
        }
        return hit;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && ModuleManager.isEnabled(Module.CREATURES)) {
            for (CreatureAbility ability : abilities()) {
                ability.onHurt(this, source, amount);
            }
        }
        return hurt;
    }

    private void applyHitTraits(LivingEntity victim) {
        if (has(Trait.FIRE_ATTACK)) {
            victim.igniteForSeconds(5);
        }
        if (has(Trait.POISON_ATTACK)) {
            victim.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 0));
        }
        if (has(Trait.PETRIFY)) {
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 4));
            victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
        }
        if (has(Trait.CHARGE) || has(Trait.KNOCKBACK)) {
            double strength = has(Trait.CHARGE) ? 1.4 : 0.7;
            victim.knockback(strength, this.getX() - victim.getX(), this.getZ() - victim.getZ());
        }
        if (has(Trait.THIEF) && victim instanceof Player player) {
            stealFrom(player);
        }
    }

    private void stealFrom(Player player) {
        List<Integer> filled = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (!player.getInventory().getItem(i).isEmpty()) {
                filled.add(i);
            }
        }
        if (filled.isEmpty()) {
            return;
        }
        int slot = filled.get(this.getRandom().nextInt(filled.size()));
        ItemStack stolen = player.getInventory().removeItem(slot, 1);
        if (stolen.isEmpty()) {
            return;
        }
        // Niffler-grade: pocket the loot and bolt; overflow falls to the ground.
        if (carried.size() < MAX_CARRIED) {
            carried.add(stolen);
        } else {
            player.drop(stolen, false, false);
        }
        addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 1));
        setTarget(null);
    }

    private void dropCarried() {
        if (carried.isEmpty() || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (ItemStack stack : carried) {
            if (!stack.isEmpty()) {
                spawnAtLocation(serverLevel, stack);
            }
        }
        carried.clear();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (!carried.isEmpty()) {
            output.store("CarriedLoot", ItemStack.CODEC.listOf(), List.copyOf(carried));
        }
        if (fireDryTicks > 0) {
            output.putInt("FireDryTicks", fireDryTicks);
        }
        if (waterDryTicks > 0) {
            output.putInt("WaterDryTicks", waterDryTicks);
        }
        if (!abilityCooldowns.isEmpty()) {
            output.store("AbilityCooldowns", COOLDOWNS_CODEC, Map.copyOf(abilityCooldowns));
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        carried.clear();
        carried.addAll(input.read("CarriedLoot", ItemStack.CODEC.listOf()).orElse(List.of()));
        fireDryTicks = input.getIntOr("FireDryTicks", 0);
        waterDryTicks = input.getIntOr("WaterDryTicks", 0);
        abilityCooldowns.clear();
        abilityCooldowns.putAll(input.read("AbilityCooldowns", COOLDOWNS_CODEC).orElse(Map.of()));
        // Back-compat: fold a legacy single-counter save into a generic key so old worlds don't desync.
        int legacy = input.getIntOr("AbilityCooldown", 0);
        if (legacy > 0) {
            abilityCooldowns.put("legacy", legacy);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && has(Trait.REGEN) && this.tickCount % 40 == 0
                && getHealth() < getMaxHealth() && isAlive()) {
            heal(1.0f);
        }
        // Datapack-driven ability dispatch (server-side, gated on Module.CREATURES — access not registration).
        if (!level().isClientSide() && isAlive() && ModuleManager.isEnabled(Module.CREATURES)) {
            if (!abilityCooldowns.isEmpty()) {
                Iterator<Map.Entry<String, Integer>> it = abilityCooldowns.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Integer> e = it.next();
                    int next = e.getValue() - 1;
                    if (next > 0) {
                        e.setValue(next);
                    } else {
                        it.remove();
                    }
                }
            }
            for (CreatureAbility ability : abilities()) {
                ability.tick(this);
            }
        }
    }

    @Override
    public void die(DamageSource cause) {
        if (!level().isClientSide()) {
            dropCarried();
            if (has(Trait.EXPLODE_ON_DEATH)) {
                level().explode(this, getX(), getY(), getZ(), 2.0f, Level.ExplosionInteraction.MOB);
            }
            if (ModuleManager.isEnabled(Module.CREATURES)) {
                for (CreatureAbility ability : abilities()) {
                    ability.onDeath(this);
                }
            }
        }
        super.die(cause);
    }
}
