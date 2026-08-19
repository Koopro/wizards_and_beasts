package at.koopro.wizardsandbeasts.wand.command;

import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.wand.stat.WandCore;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.wand.stat.WandLength;
import at.koopro.wizardsandbeasts.wand.stat.WandWood;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * {@code /wandb item wand give <wood> <core>} — hands the invoking player a wand.
 *
 * <p>Lived in {@code SpellCommands} until the command tree was regrouped, which put a
 * <em>wand</em> spawner in the <em>spell</em> file purely because both hung off the old
 * {@code /wandb wand} node. Length and flexibility are rolled rather than asked for: the pair
 * that matters for testing is wood and core, and four required arguments made the common case
 * tedious.
 */
public final class WandGiveCommands {

    private WandGiveCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("give")
                .then(Commands.argument("wood", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(WandWood.values()).map(WandWood::getSerializedName), builder))
                        .then(Commands.argument("core", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(WandCore.values()).map(WandCore::getSerializedName), builder))
                                .executes(ctx -> giveWand(
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "wood"),
                                        StringArgumentType.getString(ctx, "core")))));
    }

    private static int giveWand(ServerPlayer player, String woodName, String coreName) {
        WandWood wood = WandWood.byName(woodName);
        WandCore core = WandCore.byName(coreName);

        if (wood == null) {
            player.displayClientMessage(Component.literal("Unknown wood: " + woodName).withStyle(ChatFormatting.RED), false);
            return 0;
        }
        if (core == null) {
            player.displayClientMessage(Component.literal("Unknown core: " + coreName).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        WandLength[] lengths = WandLength.values();
        WandFlexibility[] flexes = WandFlexibility.values();
        WandLength length = lengths[player.getRandom().nextInt(lengths.length)];
        WandFlexibility flex = flexes[player.getRandom().nextInt(flexes.length)];

        ItemStack wand = WandItem.createWand(wood, core, length, flex);
        player.getInventory().add(wand);

        player.displayClientMessage(Component.literal(
                "Given " + wood.getDisplayName() + " wand with " + core.getDisplayName() + " core ("
                        + length.getDisplayName() + ", " + flex.getDisplayName() + ")").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
