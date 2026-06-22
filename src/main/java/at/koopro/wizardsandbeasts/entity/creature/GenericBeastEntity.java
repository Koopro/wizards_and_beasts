package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.creature.CreatureDefinition;
import at.koopro.wizardsandbeasts.creature.CreatureDefinitionRegistry;
import at.koopro.wizardsandbeasts.creature.Temperament;
import at.koopro.wizardsandbeasts.creature.Trait;
import at.koopro.wizardsandbeasts.entity.GeoEntityBase;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
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
import java.util.List;
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

    protected GenericBeastEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        if (!level.isClientSide()) {
            applyDefinition();
        }
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

    protected boolean has(Trait trait) {
        return traits().contains(trait);
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
        return has(Trait.FIRE_IMMUNE) || super.fireImmune();
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity victim) {
            applyHitTraits(victim);
        }
        return hit;
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
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        carried.clear();
        carried.addAll(input.read("CarriedLoot", ItemStack.CODEC.listOf()).orElse(List.of()));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && has(Trait.REGEN) && this.tickCount % 40 == 0
                && getHealth() < getMaxHealth() && isAlive()) {
            heal(1.0f);
        }
    }

    @Override
    public void die(DamageSource cause) {
        if (!level().isClientSide()) {
            dropCarried();
            if (has(Trait.EXPLODE_ON_DEATH)) {
                level().explode(this, getX(), getY(), getZ(), 2.0f, Level.ExplosionInteraction.MOB);
            }
        }
        super.die(cause);
    }
}
