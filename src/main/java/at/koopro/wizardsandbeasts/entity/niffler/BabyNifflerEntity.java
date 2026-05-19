package at.koopro.wizardsandbeasts.entity.niffler;

import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundEvent;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Baby Niffler variant. 9-slot pouch, half-scale, cannot be pocket-carried,
 * grows into adult after 3 in-game days (72000 ticks) with at least 3 feedings.
 */
public class BabyNifflerEntity extends NifflerEntity {

    private static final RawAnimation IDLE_ANIM = AnimHelper.loop("niffler", "idle");
    private static final RawAnimation WALK_ANIM = AnimHelper.loop("niffler", "walk");

    private int growthTicks;
    private int feedCount;

    public BabyNifflerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 0.5)
                .add(Attributes.FOLLOW_RANGE, 16.0)
                .add(Attributes.SCALE, 0.5);
    }

    @Override
    protected int getPouchCapacity() { return 9; }

    @Override
    public boolean isBaby() { return true; }



    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            growthTicks++;
            if (growthTicks >= 72000 && feedCount >= 3) {
                growIntoAdult();
            }
        }
    }

    @Override
    protected @NonNull InteractionResult mobInteract(@NonNull Player player, @NonNull InteractionHand hand) {
        net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
        // Track feedings for growth
        if (isFeedItemForGrowth(held)) {
            InteractionResult result = super.mobInteract(player, hand);
            if (result.consumesAction()) feedCount++;
            return result;
        }
        return super.mobInteract(player, hand);
    }

    private boolean isFeedItemForGrowth(net.minecraft.world.item.ItemStack stack) {
        return stack.is(net.minecraft.world.item.Items.DIAMOND)
                || stack.is(net.minecraft.world.item.Items.GOLD_INGOT)
                || stack.is(net.minecraft.world.item.Items.GOLD_NUGGET);
    }

    private void growIntoAdult() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        NifflerEntity adult = new NifflerEntity(at.koopro.wizardsandbeasts.registry.ModEntities.NIFFLER.get(), serverLevel);
        adult.copyPosition(this);
        // Transfer bond data without needing a Player reference
        adult.transferBondFrom(this);
        serverLevel.addFreshEntity(adult);
        this.discard();
    }

    // Growth NBT
    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("GrowthTicks", com.mojang.serialization.Codec.INT, growthTicks);
        output.store("FeedCount", com.mojang.serialization.Codec.INT, feedCount);
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        growthTicks = input.read("GrowthTicks", com.mojang.serialization.Codec.INT).orElse(0);
        feedCount = input.read("FeedCount", com.mojang.serialization.Codec.INT).orElse(0);
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return ModSounds.BABY_NIFFLER_AMBIENT.get();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(AnimHelper.movementController("niffler", 5, IDLE_ANIM, WALK_ANIM));
    }
}
