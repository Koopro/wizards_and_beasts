package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.creature.wildlife.SignatureRules;
import at.koopro.wizardsandbeasts.entity.BeastNavigation;
import at.koopro.wizardsandbeasts.entity.GeoEntityBase;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;

import java.util.EnumSet;
import java.util.List;

/**
 * Cornish Pixie — electric blue, eight inches tall, and very mischievous (Fantastic Beasts; Chamber of Secrets). It
 * delights in tricks and practical jokes and is capable of seizing unwary people by the ears and hoisting them up trees
 * and onto buildings.
 *
 * <ul>
 *   <li><b>Snatching:</b> it goes for whatever a person is holding — a wand in either hand before anything else —
 *       takes it and makes off with it, then lets it drop wherever it happens to be: up a tree, on a roof, in the lake.
 *       </li>
 *   <li><b>Hoisting:</b> when {@link SignatureRules#PIXIE_HOIST_SWARM} or more of them are on one person, they lift
 *       that person into the air and let them down slowly. A prank, not an attack: nothing here does damage.</li>
 *   <li><b>Counterplay:</b> a pixie that is struck lets go of what it carries. Creative players lose nothing.</li>
 * </ul>
 *
 * <p>Deliberately absent: taming, drops of its own, and real harm — canon pixies wreck rooms, not people.
 */
public class CornishPixieEntity extends GeoEntityBase {

    private static final RawAnimation IDLE_ANIM = AnimHelper.loop("cornish_pixie", "idle");
    private static final RawAnimation FLY_ANIM = AnimHelper.loop("cornish_pixie", "fly");
    /** One-shots (hit, death), registered after movement so they override the loop. */
    private static final String ACTION = "cornish_pixie_action";

    /** Ticks between one snatch and the next attempt. */
    static final int SNATCH_COOLDOWN = 600;
    /** How long it keeps what it snatched before losing interest and dropping it. */
    static final int CARRY_TICKS = 160;
    /** Ticks between hoists by the same pixie. */
    static final int HOIST_COOLDOWN = 400;
    /** How close pixies must be to a person to hoist them. */
    static final double HOIST_REACH = 4.0;
    /** How close it must get to a hand to take what is in it. */
    static final double SNATCH_REACH_SQR = 2.5 * 2.5;

    private ItemStack carried = ItemStack.EMPTY;
    private int carryLeft;
    private int snatchCooldown;
    private int hoistCooldown;

    public CornishPixieEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 10, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 4.0)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return BeastNavigation.flying(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new MakeOffGoal());
        goalSelector.addGoal(2, new PesterGoal());
        goalSelector.addGoal(3, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    /** What it is making off with, or empty. */
    public ItemStack carried() {
        return carried;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel level) || !isAlive()) {
            return;
        }
        if (snatchCooldown > 0) snatchCooldown--;
        if (hoistCooldown > 0) hoistCooldown--;
        if (!carried.isEmpty() && --carryLeft <= 0) {
            letGo();
        }
        if (hoistCooldown == 0 && tickCount % 20 == 0 && ModuleManager.isEnabled(Module.CREATURES)) {
            tryHoist(level);
        }
    }

    /** Whether this person can lose what they hold to a pixie. */
    static boolean canLoseItems(Player player) {
        return !player.isCreative() && !player.isSpectator() && player.isAlive();
    }

    /** The hand a pixie goes for: a wand in either hand first, otherwise the main hand. */
    static InteractionHand handToRob(Player player) {
        if (player.getMainHandItem().getItem() instanceof WandItem) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().getItem() instanceof WandItem) {
            return InteractionHand.OFF_HAND;
        }
        return InteractionHand.MAIN_HAND;
    }

    /**
     * Takes what {@code player} holds ({@link #handToRob}) and makes off with it.
     *
     * @return whether it snatched anything
     */
    public boolean snatchFrom(Player player) {
        if (level().isClientSide() || snatchCooldown > 0) {
            return false;
        }
        InteractionHand hand = handToRob(player);
        ItemStack held = player.getItemInHand(hand);
        if (!SignatureRules.pixieMaySnatch(!held.isEmpty(), canLoseItems(player), !carried.isEmpty())) {
            return false;
        }
        carried = held.copy();
        player.setItemInHand(hand, ItemStack.EMPTY);
        carryLeft = CARRY_TICKS;
        snatchCooldown = SNATCH_COOLDOWN;
        playSound(ModSounds.CORNISH_PIXIE_JABBER.get(), 1.0f, 1.0f);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "entity.wizards_and_beasts.cornish_pixie.snatched", carried.getHoverName()), true);
            BestiaryDiscoveryHandler.witnessedSignature(serverPlayer, this);
        }
        return true;
    }

    /** Drops whatever it carries, where it is. */
    public void letGo() {
        if (carried.isEmpty() || !(level() instanceof ServerLevel level)) {
            return;
        }
        ItemEntity dropped = new ItemEntity(level, getX(), getY(), getZ(), carried);
        dropped.setDefaultPickUpDelay();
        level.addFreshEntity(dropped);
        carried = ItemStack.EMPTY;
        carryLeft = 0;
    }

    /**
     * Enough pixies on one person hoist them: a lift into the air, then a slow way down. Every pixie in on it waits
     * before it does it again.
     *
     * @return whether it hoisted someone
     */
    public boolean tryHoist(ServerLevel level) {
        if (hoistCooldown > 0) {
            return false;
        }
        for (Player player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(HOIST_REACH),
                p -> canLoseItems(p) && !p.hasEffect(MobEffects.LEVITATION))) {
            List<CornishPixieEntity> swarm = level.getEntitiesOfClass(CornishPixieEntity.class,
                    player.getBoundingBox().inflate(HOIST_REACH), CornishPixieEntity::isAlive);
            if (!SignatureRules.pixiesHoist(swarm.size())) {
                continue;
            }
            player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 1));
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0));
            for (CornishPixieEntity pixie : swarm) {
                pixie.hoistCooldown = HOIST_COOLDOWN;
            }
            playSound(ModSounds.CORNISH_PIXIE_JABBER.get(), 1.2f, 1.3f);
            BestiaryDiscoveryHandler.signatureSeenByNearby(this, 16.0);
            return true;
        }
        return false;
    }

    /** Flies at someone holding something and snatches it. */
    private final class PesterGoal extends Goal {
        private @Nullable Player target;

        PesterGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (snatchCooldown > 0 || !carried.isEmpty() || !ModuleManager.isEnabled(Module.CREATURES)) {
                return false;
            }
            Player nearest = level().getNearestPlayer(CornishPixieEntity.this, 10.0);
            if (nearest == null || !canLoseItems(nearest) || nearest.getItemInHand(handToRob(nearest)).isEmpty()) {
                return false;
            }
            target = nearest;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target.isAlive() && carried.isEmpty() && snatchCooldown == 0
                    && distanceToSqr(target) < 256.0;
        }

        @Override
        public void tick() {
            if (target == null) {
                return;
            }
            getLookControl().setLookAt(target);
            getNavigation().moveTo(target.getX(), target.getEyeY(), target.getZ(), 1.4);
            if (distanceToSqr(target.getX(), target.getEyeY(), target.getZ()) < SNATCH_REACH_SQR) {
                snatchFrom(target);
            }
        }

        @Override
        public void stop() {
            target = null;
            getNavigation().stop();
        }
    }

    /** With something in hand, it heads away and up — into the trees, onto the roof. */
    private final class MakeOffGoal extends Goal {
        MakeOffGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !carried.isEmpty() && getNavigation().isDone();
        }

        @Override
        public void start() {
            Vec3 away = DefaultRandomPos.getPos(CornishPixieEntity.this, 12, 6);
            if (away != null) {
                getNavigation().moveTo(away.x, away.y + 4, away.z, 1.5);
            }
        }
    }

    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            letGo(); // struck, it drops what it took
            if (isAlive()) triggerAnim(ACTION, "hit");
        }
        return hurt;
    }

    @Override
    public void die(@NonNull DamageSource cause) {
        if (!level().isClientSide()) {
            letGo();
            triggerAnim(ACTION, "death");
        }
        super.die(cause);
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (!carried.isEmpty()) {
            output.store("Carried", ItemStack.CODEC, carried);
            output.putInt("CarryLeft", carryLeft);
        }
        output.putInt("SnatchCooldown", snatchCooldown);
        output.putInt("HoistCooldown", hoistCooldown);
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        carried = input.read("Carried", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        carryLeft = input.getIntOr("CarryLeft", 0);
        snatchCooldown = input.getIntOr("SnatchCooldown", 0);
        hoistCooldown = input.getIntOr("HoistCooldown", 0);
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return ModSounds.CORNISH_PIXIE_JABBER.get();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(AnimHelper.movementController("cornish_pixie", 4, IDLE_ANIM, FLY_ANIM));
        controllers.add(new AnimationController<CornishPixieEntity>(ACTION, 0, test -> PlayState.STOP)
                .triggerableAnim("hit", AnimHelper.playOnce("cornish_pixie", "hit"))
                .triggerableAnim("death", AnimHelper.playOnce("cornish_pixie", "death")));
    }
}
