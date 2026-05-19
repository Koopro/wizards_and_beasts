package at.koopro.wizardsandbeasts.item.broom;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.broom.BroomDefinitionRegistry;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
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
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "cleansweep_seven");

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

            AABB searchBox = player.getBoundingBox().inflate(3.0);
            List<BroomEntity> nearbyBrooms = level.getEntitiesOfClass(BroomEntity.class, searchBox,
                    broom -> broom.getControllingPassenger() == null);
            if (!nearbyBrooms.isEmpty()) {
                BroomEntity nearest = nearbyBrooms.stream()
                        .min(Comparator.comparingDouble(player::distanceToSqr))
                        .orElse(null);
                if (nearest != null) {
                    player.startRiding(nearest);
                    return InteractionResult.SUCCESS;
                }
            }

            BroomEntity broom = new BroomEntity(ModEntities.BROOM.get(), level);
            broom.setPos(player.getX(), player.getY(), player.getZ());
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
        for (Component lore : definition.loreLines()) {
            tooltipAdder.accept(lore.copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
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
