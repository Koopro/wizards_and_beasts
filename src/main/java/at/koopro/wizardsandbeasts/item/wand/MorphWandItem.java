package at.koopro.wizardsandbeasts.item.wand;

import at.koopro.wizardsandbeasts.entity.form.FormMannequinEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * Debug tool for the form/morph system.
 * <p>
 * Right-click air: opens the Morph Debug GUI.
 * Right-click block: spawns a Form Mannequin at the clicked position.
 * Requires OP permission (level 2).
 */
public class MorphWandItem extends Item {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    public MorphWandItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.PASS;
        if (!player.createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            player.displayClientMessage(
                    Component.literal("\u00A7cRequires operator permission"), true);
            return InteractionResult.FAIL;
        }

        // Spawn a mannequin at the clicked position
        Vec3 pos = Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace()));
        FormMannequinEntity mannequin = new FormMannequinEntity(
                (ServerLevel) level, pos.x, pos.y, pos.z);
        level.addFreshEntity(mannequin);

        player.displayClientMessage(
                Component.literal("\u00A7aForm Mannequin spawned"), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) return InteractionResult.SUCCESS;
        openClientDebugScreenSafe();
        return InteractionResult.SUCCESS;
    }

    private static void openClientDebugScreenSafe() {
        try {
            Class<?> hooks = Class.forName("at.koopro.wizardsandbeasts.client.wand.MorphWandClientHooks");
            hooks.getMethod("openDebugScreen").invoke(null);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[WizardsAndBeasts] Failed to open morph debug screen via client hook", e);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("\u00A75Morphologist's Wand"));
        tooltipAdder.accept(Component.literal("\u00A77Right-click air: Debug GUI"));
        tooltipAdder.accept(Component.literal("\u00A77Right-click block: Spawn mannequin"));
    }
}
