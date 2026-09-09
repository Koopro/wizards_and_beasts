package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.creature.bond.BondState;
import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.creature.bond.FollowBondedOwnerGoal;
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
 * Bowtruckle — a tiny, shy tree-guardian beast. Peaceful; flees from threats,
 * tempted by sticks and saplings.
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
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        loadBond(input);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0)
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
        goalSelector.addGoal(1, new PanicGoal(this, 1.4));
        goalSelector.addGoal(2, new TemptGoal(this, 1.1,
                Ingredient.of(Items.STICK, Items.OAK_SAPLING), false));
        goalSelector.addGoal(3, new FollowBondedOwnerGoal<>(this));
        goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 5.0f));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(AnimHelper.movementController("bowtruckle", 5, IDLE_ANIM, WALK_ANIM));
    }
}
