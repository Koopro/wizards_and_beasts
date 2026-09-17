package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.creature.bond.BondState;
import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.creature.bond.FollowBondedOwnerGoal;
import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import at.koopro.wizardsandbeasts.entity.GeoEntityBase;
import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Bowtruckle — a tiny, shy tree-guardian that lives in a wand-quality tree and defends it. Peaceful; flees from threats,
 * tempted by sticks and saplings.
 *
 * <p><b>It has a home tree.</b> It settles on the nearest tree when it first finds itself in the world — a wandwood tree
 * if one is near — and does not wander far from it. Canon: "Bowtruckles live in trees of wand quality".
 *
 * <p><b>It defends it.</b> Break a log or leaves of its tree and every Bowtruckle living there goes for the eyes of whoever
 * did it for {@link WildlifeRules#DEFENCE_TICKS}: a scratch and a moment's blindness. Its bonded wandmaker is spared.
 * Anyone who sees it happen has seen what a Bowtruckle is for.
 *
 * <p><b>It can be won over.</b> Canon's Bowtruckle will lead a wandmaker to wand-quality wood in
 * exchange for a woodlouse, and that trade is what the bond layer buys here: feed one for long
 * enough and it follows you and starts handing over wandwood saplings, which are otherwise obtained
 * only by felling the tree they grow on. Feeding, the bond and the follow all live in
 * {@link BondableBeast}; the numbers are in
 * {@code data/wizards_and_beasts/creature_bonds/bowtruckle.json}.
 */
public class BowtruckleEntity extends GeoEntityBase implements BondableBeast {

    private static final RawAnimation IDLE_ANIM = AnimHelper.loop("bowtruckle", "idle");
    private static final RawAnimation WALK_ANIM = AnimHelper.loop("bowtruckle", "walk");

    /**
     * Bond level, synced so the client can show it. A synched accessor has to be defined on the
     * concrete class that uses it, which is the one piece {@link BondableBeast} cannot inherit.
     */
    private static final EntityDataAccessor<Integer> DATA_BOND_LEVEL =
            SynchedEntityData.defineId(BowtruckleEntity.class, EntityDataSerializers.INT);

    private final BondState bond = new BondState();

    private static final String KEY_HOME = "HomeTree";
    private static final String KEY_ANGRY_AT = "AngryAt";
    private static final String KEY_ANGER = "AngerTicks";
    /** How far a newly placed Bowtruckle looks for a tree to live in. */
    public static final int HOME_SEARCH_RADIUS = 8;
    /** How far from its tree it strays. */
    public static final int HOME_RANGE = 10;
    /** A scratch at the eyes blinds for this long. */
    public static final int BLINDING_TICKS = 60;

    private @Nullable BlockPos homeTree;
    private boolean searchedForHome;
    private @Nullable UUID angryAt;
    private int angerTicks;

    public BowtruckleEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BOND_LEVEL, 0);
    }

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
    public void tick() {
        super.tick();
        tickBond();
        if (!(level() instanceof ServerLevel serverLevel) || !ModuleManager.isEnabled(Module.CREATURES)) {
            return;
        }
        if (!searchedForHome) {
            searchedForHome = true;
            if (homeTree == null) {
                homeTree = findHomeTree(serverLevel, blockPosition());
            }
        }
        if (homeTree != null && !hasHome()) {
            setHomeTo(homeTree, HOME_RANGE);
        }
        if (angerTicks > 0 && --angerTicks == 0) {
            angryAt = null;
            setTarget(null);
        }
    }

    /** The nearest log within reach, preferring wand-quality wood. */
    public static @Nullable BlockPos findHomeTree(ServerLevel level, BlockPos around) {
        BlockPos best = null;
        boolean bestIsWandwood = false;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-HOME_SEARCH_RADIUS, -3, -HOME_SEARCH_RADIUS),
                around.offset(HOME_SEARCH_RADIUS, 6, HOME_SEARCH_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(BlockTags.LOGS)) {
                continue;
            }
            boolean wandwood = state.is(at.koopro.wizardsandbeasts.module.ModuleTags.blocks(Module.WANDWOOD));
            double distance = pos.distSqr(around);
            if ((wandwood && !bestIsWandwood) || (wandwood == bestIsWandwood && distance < bestDistance)) {
                best = pos.immutable();
                bestIsWandwood = wandwood;
                bestDistance = distance;
            }
        }
        return best;
    }

    public @Nullable BlockPos homeTree() {
        return homeTree;
    }

    public void setHomeTree(@Nullable BlockPos pos) {
        homeTree = pos == null ? null : pos.immutable();
        searchedForHome = true;
        clearHome();
    }

    public boolean isDefending() {
        return angryAt != null && angerTicks > 0;
    }

    public @Nullable UUID angryAt() {
        return angryAt;
    }

    /** Someone cut its tree. */
    public void defendAgainst(ServerPlayer culprit) {
        if (culprit.getUUID().equals(bondState().ownerUUID()) || culprit.isSpectator()) {
            return;
        }
        angryAt = culprit.getUUID();
        angerTicks = WildlifeRules.DEFENCE_TICKS;
        setTarget(culprit);
        BestiaryDiscoveryHandler.signatureSeenByNearby(this, 16.0);
    }

    @Override
    public boolean doHurtTarget(@NonNull ServerLevel level, @NonNull Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living) {
            // It goes for the eyes.
            living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLINDING_TICKS, 0));
        }
        return hit;
    }

    /** Breaking part of a tree a Bowtruckle lives in sets every Bowtruckle living there on the one who did it. */
    @EventBusSubscriber(modid = at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID)
    public static final class TreeDefence {
        private TreeDefence() {}

        @SubscribeEvent
        public static void onBreak(BlockEvent.BreakEvent event) {
            if (!(event.getPlayer() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)
                    || !ModuleManager.isEnabled(Module.CREATURES)) {
                return;
            }
            BlockState state = event.getState();
            if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)) {
                return;
            }
            BlockPos broken = event.getPos();
            double reach = WildlifeRules.HOME_TREE_RADIUS + HOME_RANGE;
            for (BowtruckleEntity guardian : level.getEntitiesOfClass(BowtruckleEntity.class,
                    new net.minecraft.world.phys.AABB(broken).inflate(reach))) {
                BlockPos home = guardian.homeTree();
                if (home != null && WildlifeRules.partOfHomeTree(home.distSqr(broken))) {
                    guardian.defendAgainst(player);
                }
            }
        }
    }

    /**
     * Feeding first, then vanilla.
     *
     * <p>{@code offerBondFood} returns {@code PASS} for anything the profile does not list, so the
     * stick that tempts a Bowtruckle across a clearing is still the stick that feeds it, and every
     * other item falls through to the default interaction untouched.
     */
    @Override
    protected @NonNull InteractionResult mobInteract(@NonNull Player player, @NonNull InteractionHand hand) {
        InteractionResult fed = offerBondFood(player, hand);
        return fed != InteractionResult.PASS ? fed : super.mobInteract(player, hand);
    }

    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            onBondedHurt(source);
        }
        return hurt;
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        saveBond(output);
        if (homeTree != null) {
            output.store(KEY_HOME, BlockPos.CODEC, homeTree);
        }
        if (angryAt != null && angerTicks > 0) {
            output.store(KEY_ANGRY_AT, UUIDUtil.CODEC, angryAt);
            output.store(KEY_ANGER, Codec.INT, angerTicks);
        }
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        loadBond(input);
        homeTree = input.read(KEY_HOME, BlockPos.CODEC).orElse(null);
        searchedForHome = homeTree != null;
        angryAt = input.read(KEY_ANGRY_AT, UUIDUtil.CODEC).orElse(null);
        angerTicks = input.read(KEY_ANGER, Codec.INT).orElse(0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 12.0)
                .add(Attributes.TEMPT_RANGE, 10.0)
                // Declared so a bred juvenile can be shrunk: BondableBeast scales young through
                // SCALE rather than registering a baby EntityType, and getAttribute returns null
                // for an attribute the supplier never mentions.
                .add(Attributes.SCALE, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // Defending its tree outranks fear: a Bowtruckle whose tree is being cut stops running.
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.3, true) {
            @Override
            public boolean canUse() {
                return isDefending() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return isDefending() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(2, new PanicGoal(this, 1.4) {
            @Override
            public boolean canUse() {
                return !isDefending() && super.canUse();
            }
        });
        goalSelector.addGoal(3, new TemptGoal(this, 1.1,
                Ingredient.of(Items.STICK, Items.OAK_SAPLING), false));
        goalSelector.addGoal(4, new FollowBondedOwnerGoal<>(this));
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 5.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                (target, level) -> isDefending() && target.getUUID().equals(angryAt)));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(AnimHelper.movementController("bowtruckle", 5, IDLE_ANIM, WALK_ANIM));
    }
}
