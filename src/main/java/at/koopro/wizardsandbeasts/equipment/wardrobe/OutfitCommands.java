package at.koopro.wizardsandbeasts.equipment.wardrobe;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wandb item outfit <livery> [rank]}: stitches the robe in the executing player's main hand
 * in a livery at a rank (WARDROBE_RENAISSANCE Phase C). Admin-only through the parent
 * {@code item} node. Every check lives in {@link Wardrobe#stamp}; this only reports.
 */
@NullMarked
public final class OutfitCommands {

    private static final String KEY = "command." + WizardsAndBeastsMod.MODID + ".outfit.";

    private OutfitCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("outfit")
                .then(Commands.argument("livery", IdentifierArgument.id())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                ctx.getSource().registryAccess().lookupOrThrow(Wardrobe.LIVERY_REGISTRY).keySet(),
                                builder))
                        .executes(ctx -> stamp(ctx.getSource(), IdentifierArgument.getId(ctx, "livery"), 0))
                        .then(Commands.argument("rank", IntegerArgumentType.integer(0, Outfit.MAX_RANK))
                                .executes(ctx -> stamp(ctx.getSource(),
                                        IdentifierArgument.getId(ctx, "livery"),
                                        IntegerArgumentType.getInteger(ctx, "rank")))));
    }

    private static int stamp(CommandSourceStack source, Identifier livery, int rank) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        Wardrobe.Result result = Wardrobe.stamp(held, livery, rank, source.registryAccess());
        Component item = held.getHoverName();
        switch (result) {
            case STAMPED -> {
                source.sendSuccess(() -> Component.translatable(KEY + "stamped", item, livery.toString(), rank), true);
                return 1;
            }
            case NOT_A_CUT -> source.sendFailure(Component.translatable(KEY + "not_a_cut", item));
            case UNKNOWN_LIVERY -> source.sendFailure(Component.translatable(KEY + "unknown_livery", livery.toString()));
            case LIVERY_REFUSES_CUT -> source.sendFailure(
                    Component.translatable(KEY + "livery_refuses_cut", livery.toString(), item));
            case BAD_RANK -> source.sendFailure(Component.translatable(KEY + "bad_rank"));
        }
        return 0;
    }
}
