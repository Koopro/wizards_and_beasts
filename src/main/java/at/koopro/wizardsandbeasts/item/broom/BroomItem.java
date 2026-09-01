package at.koopro.wizardsandbeasts.item.broom;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.broom.BroomDefinitionRegistry;
import at.koopro.wizardsandbeasts.broom.BroomDefinitionRegistry;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import at.koopro.wizardsandbeasts.ministry.licence.BroomLicence;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import org.jspecify.annotations.Nullable;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.function.Consumer;

import java.util.Comparator;
import java.util.List;

public class BroomItem extends Item {
    private static final Identifier FALLBACK_ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "broom");

    private final Identifier defaultDefinitionId;

    public BroomItem(Properties properties) {
        this(properties, FALLBACK_ID);
    }

    public BroomItem(Properties properties, Identifier defaultDefinitionId) {
        super(properties);
        this.defaultDefinitionId = defaultDefinitionId;
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        stack.set(ModDataComponents.BROOM_DEFINITION.get(), defaultDefinitionId);
        return stack;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.BROOM_FLIGHT)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.literal("Broom flight is not yet available.")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            if (player.getVehicle() instanceof BroomEntity) {
                return InteractionResult.SUCCESS;
            }
            // Papers, before anything is spawned or mounted. Checked off the stack's own definition so
            // the refusal happens without a BroomEntity ever existing to clean up.
            Identifier riddenId = stack.getOrDefault(ModDataComponents.BROOM_DEFINITION.get(), FALLBACK_ID);
            if (BroomLicence.refuse(player, BroomDefinitionRegistry.getOrFallback(riddenId))) {
                return InteractionResult.FAIL;
            }
            // A spent broom is not a slow broom, it is a broken one. The entity used to be spawned
            // with Math.max(1, remaining), which quietly handed a fully damaged broom one point of
            // durability and let it be flown -- and then broken again on the first knock.
            if (isSpent(stack)) {
                PlayerFeedback.actionBar(player,
                        Component.translatable("broom.wizards_and_beasts.spent")
                                .withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }

            AABB searchBox = player.getBoundingBox().inflate(3.0);
            List<BroomEntity> nearbyBrooms = level.getEntitiesOfClass(BroomEntity.class, searchBox,
                    broom -> broom.getControllingPassenger() == null);
            if (!nearbyBrooms.isEmpty()) {
                BroomEntity nearest = nearbyBrooms.stream()
                        .min(Comparator.comparingDouble(player::distanceToSqr))
                        .orElse(null);
                if (nearest != null) {
                    // A broom at rest lies on the floor, and the seat is below its origin, so
                    // mounting one where it lies would put the rider's feet underground and let
                    // the collision push them back out. Lift it to meet them instead — which is
                    // also what picking a broom up off the ground looks like.
                    nearest.setPos(nearest.getX(),
                            nearest.getY() + nearest.resolveDefinition().seat().mountLift(),
                            nearest.getZ());
                    player.startRiding(nearest);
                    return InteractionResult.SUCCESS;
                }
            }

            BroomEntity broom = new BroomEntity(ModEntities.BROOM.get(), level);
            // Spawned high enough that the seat lands exactly where the player is standing:
            // startRiding then places them at broomY + SEAT_OFFSET_Y, which is their own feet.
            // Without the lift, mounting would drop the player half a block into the ground.
            broom.setPos(player.getX(),
                    player.getY() + BroomDefinitionRegistry.getOrFallback(
                            stack.getOrDefault(ModDataComponents.BROOM_DEFINITION.get(), FALLBACK_ID))
                            .seat().mountLift(),
                    player.getZ());
            broom.setYRot(player.getYRot());
            broom.setBroomStack(stack.copy());
            Identifier defId = stack.getOrDefault(ModDataComponents.BROOM_DEFINITION.get(), FALLBACK_ID);
            broom.setDefinitionId(defId);
            broom.setCurrentDurability(Math.max(1, stack.getMaxDamage() - stack.getDamageValue()));
            level.addFreshEntity(broom);
            player.startRiding(broom);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Drops the polish window once it has passed.
     *
     * <p>Here rather than on {@code BroomPolishItem} because the broom is what carries the component:
     * a broom in a chest with no polish anywhere near it still has to stop claiming to be slick. It
     * also makes "the component is present" mean "polished" everywhere else, which is what lets the
     * tooltip say so without a clock to check an absolute tick against.
     */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity,
                              @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        Long until = stack.get(ModDataComponents.POLISHED_UNTIL_TICK.get());
        if (until != null && level.getGameTime() >= until) {
            stack.remove(ModDataComponents.POLISHED_UNTIL_TICK.get());
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipAdder, TooltipFlag tooltipFlag) {
        if (!ModuleManager.isEnabled(Module.BROOM_FLIGHT)) {
            tooltipAdder.accept(Component.literal("Broom flight is not yet available.").withStyle(ChatFormatting.RED));
            return;
        }
        BroomDefinition definition = resolveDefinition(stack);
        tooltipAdder.accept(definition.tier().displayName().copy().withStyle(ChatFormatting.GOLD));
        tooltipAdder.accept(Component.literal(String.format("Top speed: %.2f", definition.maxSpeed())).withStyle(ChatFormatting.GRAY));
        tooltipAdder.accept(Component.literal(String.format("Boost: x%.2f", definition.boostMultiplier())).withStyle(ChatFormatting.GRAY));
        tooltipAdder.accept(Component.literal("Durability: " + definition.durability()).withStyle(ChatFormatting.GRAY));
        // Present only while the window is open: BroomItem.inventoryTick strips the component the
        // moment it lapses, so this cannot claim a polish that has already worn off.
        if (stack.has(ModDataComponents.POLISHED_UNTIL_TICK.get())) {
            tooltipAdder.accept(Component.translatable("broom.wizards_and_beasts.polish.tooltip")
                    .withStyle(ChatFormatting.AQUA));
        }
        for (Component lore : definition.loreLines()) {
            tooltipAdder.accept(lore.copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    /** True when this broom has no durability left and must be serviced before it will fly. */
    public static boolean isSpent(ItemStack stack) {
        return stack.getMaxDamage() > 0 && stack.getDamageValue() >= stack.getMaxDamage();
    }

    private static BroomDefinition resolveDefinition(ItemStack stack) {
        Identifier id = stack.getOrDefault(ModDataComponents.BROOM_DEFINITION.get(), FALLBACK_ID);
        BroomDefinition definition = BroomDefinitionRegistry.get(id);
        if (definition != null) {
            return definition;
        }
        return BroomDefinitionRegistry.getFallback();
    }
}
