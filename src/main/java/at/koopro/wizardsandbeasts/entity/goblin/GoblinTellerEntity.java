package at.koopro.wizardsandbeasts.entity.goblin;

import at.koopro.wizardsandbeasts.entity.GeoEntityBase;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import at.koopro.wizardsandbeasts.ministry.licence.MinistryLicenceGate;
import at.koopro.wizardsandbeasts.currency.dragot.DragotQuotes;
import at.koopro.wizardsandbeasts.network.currency.DragotQuoteS2CPayload;
import at.koopro.wizardsandbeasts.network.currency.GringottsOpenS2CPayload;
import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;

import java.util.Locale;

/**
 * A Gringotts goblin, in one of four roles.
 *
 * <p>The four are exactly the goblin professions the mod already defines in {@code ProfessionNode}
 * — Vault Clerk, Metalsmith, Bank Steward, Gringotts Director — so the mod carries one goblin
 * vocabulary rather than inventing a second one for mobs. One entity class covers all of them,
 * following {@code DragonEntity}'s one-class-many-breeds precedent: they differ only in what they
 * are wearing, and a role is not worth an {@code EntityType}.
 *
 * <p>Every role shares one skeleton and therefore one animation file
 * ({@code goblin_teller.animation.json}); only the geometry and texture swap, which is what
 * {@link at.koopro.wizardsandbeasts.client.entity.GoblinVariantGeoModel} does off this entity's
 * synced {@link #DATA_VARIANT}.
 *
 * <p>The teller is still the only way into the vault screen — {@link #mobInteract} opens it,
 * whatever role the goblin happens to be wearing.
 */
public class GoblinTellerEntity extends GeoEntityBase {

    public static final String ACTION_CONTROLLER = "goblin_action";

    private static final String ASSET = "goblin_teller";
    private static final RawAnimation IDLE_ANIM = AnimHelper.loop(ASSET, "idle");
    private static final RawAnimation WALK_ANIM = AnimHelper.loop(ASSET, "walk");

    /**
     * The four Gringotts roles, with the spawn weighting that decides how often each is seen.
     *
     * <p>Weights mirror a bank's staffing rather than an even split: clerks are who you actually
     * meet at a counter, and there is only ever one Director, so seeing one should feel like an
     * event.
     */
    public enum Variant {
        CLERK(55),
        METALSMITH(20),
        BANKER(20),
        DIRECTOR(5);

        private static final Variant[] VALUES = values();
        private static final int TOTAL_WEIGHT;

        static {
            int total = 0;
            for (Variant variant : VALUES) {
                total += variant.weight;
            }
            TOTAL_WEIGHT = total;
        }

        private final int weight;

        Variant(int weight) {
            this.weight = weight;
        }

        /** Save/command name, e.g. {@code /summon … {Variant:"metalsmith"}}. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * Asset subpath under {@code geckolib/models/entity/} and {@code textures/entity/}.
         *
         * <p>Note this is <i>not</i> the animation path: the shared clips live at {@code ASSET},
         * which is also the plain prop-free rig the player heritage form draws.
         */
        public String assetSubpath() {
            return "goblin/" + key();
        }

        public static Variant byKey(String key) {
            for (Variant variant : VALUES) {
                if (variant.key().equals(key)) {
                    return variant;
                }
            }
            return CLERK;
        }

        public static Variant byId(int id) {
            return VALUES[Math.floorMod(id, VALUES.length)];
        }

        static Variant roll(RandomSource random) {
            int pick = random.nextInt(TOTAL_WEIGHT);
            for (Variant variant : VALUES) {
                pick -= variant.weight;
                if (pick < 0) {
                    return variant;
                }
            }
            return CLERK;
        }
    }

    /**
     * Which role this goblin is wearing. Synced because the client picks the geometry and texture
     * from it, and read on the client only through render-state (never live at render time),
     * matching the {@code GenericBeastEntity.DATA_DISGUISED} contract.
     */
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(GoblinTellerEntity.class, EntityDataSerializers.INT);

    public GoblinTellerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, Variant.CLERK.ordinal());
    }

    public Variant variant() {
        return Variant.byId(this.entityData.get(DATA_VARIANT));
    }

    public void setVariant(Variant variant) {
        this.entityData.set(DATA_VARIANT, variant.ordinal());
    }

    /**
     * Rolls the role on spawn.
     *
     * <p>{@code finalizeSpawn} rather than the constructor because it runs when a goblin is
     * genuinely spawned and not when one is loaded back off disk — rolling in the constructor
     * would re-pick a role every time the chunk reloaded, before {@link #readAdditionalSaveData}
     * put the saved one back.
     */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        setVariant(Variant.roll(level.getRandom()));
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Variant", variant().key());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        // Also the path that makes `/summon … {Variant:"director"}` work, with no command of our own.
        input.getString("Variant").ifPresent(key -> setVariant(Variant.byKey(key)));
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 0.3));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            // Papers first. A goblin looks at what it is handed before it opens anything, which is
            // both the licence gate and the moment a forgery gets its 15% chance to be noticed.
            if (!MinistryLicenceGate.admit(serverPlayer, LicenseType.MINISTRY_ACCESS)) {
                return InteractionResult.FAIL;
            }
            // "The goblin bowed them through the silver doors." Fires as the vault screen opens.
            triggerAnim(ACTION_CONTROLLER, "bow");
            // Walking up to the counter is what moves the rate: a fresh roll here, and the same
            // number stands for the whole visit. See DragotQuotes.
            DragotQuotes.refresh(serverPlayer);
            GringottsOpenS2CPayload.sendToPlayer(serverPlayer);
            DragotQuoteS2CPayload.sendToPlayer(serverPlayer);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(AnimHelper.movementController(ASSET, 5, IDLE_ANIM, WALK_ANIM));
        controllers.add(new AnimationController<GoblinTellerEntity>(ACTION_CONTROLLER, 0, state -> PlayState.STOP)
                .triggerableAnim("bow", AnimHelper.playOnce(ASSET, "bow")));
    }
}
