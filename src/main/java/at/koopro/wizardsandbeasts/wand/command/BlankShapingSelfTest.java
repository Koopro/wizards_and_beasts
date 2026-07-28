package at.koopro.wizardsandbeasts.wand.command;

import at.koopro.wizardsandbeasts.registry.WandItemRegistry;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * TEMPORARY. Drives the wand blank's shaping path headlessly so a dedicated server can say where it
 * fails, without needing a human at a keyboard. Delete once the answer is known.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID)
public final class BlankShapingSelfTest {

    private BlankShapingSelfTest() {}

    /**
     * TEMPORARY. Fires on both sides for a right-click made while holding a blank, so a single click says
     * whether the interaction event even happens, and on which side, before the item is consulted.
     */
    @net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onRightClickBlock(
            net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof at.koopro.wizardsandbeasts.item.wand.WandBlankItem)) {
            return;
        }
        boolean client = event.getLevel().isClientSide();
        event.getEntity().displayClientMessage(Component.literal(
                (client ? "[probe/client] " : "[probe/server] ")
                        + "RightClickBlock on " + event.getLevel().getBlockState(event.getPos()).getBlock()
                        + " canceled=" + event.isCanceled()
                        + " useItem=" + event.getUseItem()
                        + " useBlock=" + event.getUseBlock()), false);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("blanktest").requires(at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions.ADMIN).executes(ctx -> run(ctx.getSource()));
    }

    private static int run(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        reportEnvironment(source);
        BlockPos logPos = BlockPos.containing(source.getPosition()).above(3);
        level.setBlock(logPos, Blocks.OAK_LOG.defaultBlockState(), 3);
        BlockState state = level.getBlockState(logPos);

        say(source, "block at " + logPos.toShortString() + " = " + state.getBlock());
        say(source, "is #minecraft:oak_logs = " + state.is(BlockTags.OAK_LOGS));
        say(source, "WAND_WOOD registered = " + WandComponents.WAND_WOOD.isBound());

        ItemStack blank = new ItemStack(WandItemRegistry.WAND_BLANK.get());
        say(source, "blank item class = " + blank.getItem().getClass().getName());
        say(source, "wood before = " + WandComponents.getWood(blank));

        var player = FakePlayerFactory.getMinecraft(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, blank);
        ItemStack inHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        say(source, "stack in hand is the same object = " + (inHand == blank));

        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(logPos), Direction.UP, logPos, false);
        UseOnContext context = new UseOnContext(level, player, InteractionHand.MAIN_HAND, inHand, hit);

        InteractionResult direct = blank.getItem().useOn(context);
        say(source, "Item.useOn returned " + direct);
        say(source, "wood after direct useOn = " + WandComponents.getWood(inHand));

        // And through the full ItemStack path, which is what the server actually calls.
        ItemStack second = new ItemStack(WandItemRegistry.WAND_BLANK.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, second);
        ItemStack inHand2 = player.getItemInHand(InteractionHand.MAIN_HAND);
        UseOnContext context2 = new UseOnContext(level, player, InteractionHand.MAIN_HAND, inHand2, hit);
        InteractionResult viaStack = inHand2.useOn(context2);
        say(source, "ItemStack.useOn returned " + viaStack);
        Identifier after = WandComponents.getWood(inHand2);
        say(source, "wood after ItemStack.useOn = " + after);
        say(source, "hand stack wood = " + WandComponents.getWood(player.getItemInHand(InteractionHand.MAIN_HAND)));

        // Now the full server interaction path, which the two calls above skip: this is what a real
        // right-click runs, and it posts PlayerInteractEvent.RightClickBlock on the way in.
        ItemStack third = new ItemStack(WandItemRegistry.WAND_BLANK.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, third);
        ItemStack inHand3 = player.getItemInHand(InteractionHand.MAIN_HAND);

        var event = net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(
                player, InteractionHand.MAIN_HAND, logPos, hit);
        say(source, "RightClickBlock canceled = " + event.isCanceled()
                + ", useItem = " + event.getUseItem() + ", useBlock = " + event.getUseBlock());

        InteractionResult viaGameMode =
                player.gameMode.useItemOn(player, level, inHand3, InteractionHand.MAIN_HAND, hit);
        say(source, "gameMode.useItemOn returned " + viaGameMode);
        say(source, "wood after gameMode path = "
                + WandComponents.getWood(player.getItemInHand(InteractionHand.MAIN_HAND)));

        level.setBlock(logPos, Blocks.AIR.defaultBlockState(), 3);
        say(source, after != null ? "RESULT: direct shaping works server-side" : "RESULT: direct shaping FAILED");
        return 1;
    }

    /**
     * The state a real session actually depends on. The mechanism above works on a fake player in an
     * empty world, so anything that breaks it for a real player lives here.
     */
    private static void reportEnvironment(CommandSourceStack source) {
        var wands = at.koopro.wizardsandbeasts.module.Module.WANDS;
        say(source, "WANDS module state = " + at.koopro.wizardsandbeasts.module.ModuleManager.state(wands)
                + " (accessible = " + at.koopro.wizardsandbeasts.module.ModuleManager.isEnabled(wands) + ")");
        var blankItem = WandItemRegistry.WAND_BLANK.get();
        var owner = at.koopro.wizardsandbeasts.module.ModuleContentIndex.moduleOf(blankItem);
        say(source, "wand_blank owned by module " + owner + ", accessible = "
                + at.koopro.wizardsandbeasts.module.ModuleContentIndex.accessible(owner));

        var player = source.getPlayer();
        if (player == null) {
            say(source, "run by console — no held item to inspect");
            return;
        }
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        say(source, "your main hand = " + held.getItem() + " x" + held.getCount());
        say(source, "its wand_wood component = " + WandComponents.getWood(held));
        say(source, "gamemode = " + player.gameMode.getGameModeForPlayer()
                + ", mayBuild = " + player.getAbilities().mayBuild);
    }

    private static void say(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal("[blanktest] " + line).withStyle(ChatFormatting.YELLOW), false);
    }
}
