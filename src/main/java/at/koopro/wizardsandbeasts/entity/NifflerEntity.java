package at.koopro.wizardsandbeasts.entity;

import at.koopro.wizardsandbeasts.entity.ai.NifflerPickupCoinGoal;
import at.koopro.wizardsandbeasts.entity.ai.NifflerStealFromPlayerGoal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import at.koopro.wizardsandbeasts.util.AnimHelper;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.RawAnimation;

public class NifflerEntity extends GeoEntityBase {

    private static final RawAnimation IDLE_ANIM = AnimHelper.loop("niffler", "idle");
    private static final RawAnimation WALK_ANIM = AnimHelper.loop("niffler", "walk");

    private final SimpleContainer stolenItems = new SimpleContainer(27);

    public NifflerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.4);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new NifflerPickupCoinGoal(this, 16.0));
        goalSelector.addGoal(2, new NifflerStealFromPlayerGoal(this, 3.0));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.4));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    public SimpleContainer getStolenItems() {
        return stolenItems;
    }

    public void storeItem(ItemStack stack) {
        for (int i = 0; i < stolenItems.getContainerSize(); i++) {
            ItemStack existing = stolenItems.getItem(i);
            if (existing.isEmpty()) {
                stolenItems.setItem(i, stack.copy());
                return;
            }
            if (ItemStack.isSameItemSameComponents(existing, stack)
                    && existing.getCount() < existing.getMaxStackSize()) {
                int space = existing.getMaxStackSize() - existing.getCount();
                int transfer = Math.min(space, stack.getCount());
                existing.grow(transfer);
                return;
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (int i = 0; i < stolenItems.getContainerSize(); i++) {
            ItemStack stack = stolenItems.getItem(i);
            if (!stack.isEmpty()) {
                spawnAtLocation(level, stack);
            }
        }
        stolenItems.clearContent();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        for (int i = 0; i < stolenItems.getContainerSize(); i++) {
            ItemStack stack = stolenItems.getItem(i);
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        output.store("StolenItems", ItemStack.CODEC.listOf(), items);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        stolenItems.clearContent();
        java.util.List<ItemStack> items = input.read("StolenItems", ItemStack.CODEC.listOf())
                .orElse(java.util.List.of());
        for (int i = 0; i < items.size() && i < stolenItems.getContainerSize(); i++) {
            stolenItems.setItem(i, items.get(i));
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(AnimHelper.movementController("niffler", 5, IDLE_ANIM, WALK_ANIM));
    }

}
