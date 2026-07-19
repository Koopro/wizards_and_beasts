package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.ability.AnimagusForms;
import at.koopro.wizardsandbeasts.ability.AnimagusTransformService;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /wandb animagus} — survival actions (info / form / transform / register)
 * plus operator-only testing shortcuts (unlock / reset).
 */
public final class AnimagusCommands {

    private AnimagusCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("animagus")
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("transform")
                        .executes(ctx -> transform(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("register")
                        .executes(ctx -> registerMinistry(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("form")
                        .then(Commands.argument("beast", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        AnimagusForms.BEAST_KEYS, builder))
                                .executes(ctx -> chooseForm(
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "beast")))))
                .then(Commands.literal("unlock")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .executes(ctx -> opUnlock(ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> opUnlock(EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("reset")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .executes(ctx -> opReset(ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> opReset(EntityArgument.getPlayer(ctx, "player")))));
    }

    private static int info(ServerPlayer player) {
        boolean unlocked = PlayerAbilityHelper.isAnimagusUnlocked(player);
        boolean registered = PlayerAbilityHelper.isAnimagusRegistered(player);
        boolean transformed = PlayerAbilityHelper.isCurrentlyTransformed(player);
        String formId = PlayerAbilityHelper.getAnimagusFormId(player);
        boolean hasSkill = AnimagusTransformService.hasAnimagusSkill(player);

        send(player, "— Animagus —", ChatFormatting.GOLD);
        send(player, "Discipline learned (skill): " + yesNo(hasSkill), ChatFormatting.GRAY);
        send(player, "Transformation complete: " + yesNo(unlocked), ChatFormatting.GRAY);
        send(player, "Beast form: " + (formId != null ? formId : "(none)"), ChatFormatting.GRAY);
        send(player, "Ministry-registered: " + yesNo(registered), ChatFormatting.GRAY);
        send(player, "Currently transformed: " + yesNo(transformed), ChatFormatting.GRAY);
        if (hasSkill && !unlocked) {
            send(player, "Complete the ritual: right-click a mandrake during a thunderstorm.", ChatFormatting.DARK_GREEN);
        }
        return 1;
    }

    private static int transform(ServerPlayer player) {
        if (!AnimagusTransformService.canTransform(player)
                && !PlayerAbilityHelper.isCurrentlyTransformed(player)) {
            send(player, "You are not yet an Animagus, or have not chosen a beast form.", ChatFormatting.RED);
            return 0;
        }
        AnimagusTransformService.toggleTransform(player);
        return 1;
    }

    private static int chooseForm(ServerPlayer player, String beast) {
        if (!PlayerAbilityHelper.isAnimagusUnlocked(player)) {
            send(player, "You must complete the Animagus transformation before choosing a form.", ChatFormatting.RED);
            return 0;
        }
        String resolved = AnimagusTransformService.setForm(player, beast);
        if (resolved == null) {
            return 0;
        }
        send(player, "Your Animagus form is now: " + resolved, ChatFormatting.GOLD);
        return 1;
    }

    private static int registerMinistry(ServerPlayer player) {
        if (!PlayerAbilityHelper.isAnimagusUnlocked(player)) {
            send(player, "Only a completed Animagus may register with the Ministry.", ChatFormatting.RED);
            return 0;
        }
        if (PlayerAbilityHelper.isAnimagusRegistered(player)) {
            send(player, "You are already a registered Animagus.", ChatFormatting.GRAY);
            return 1;
        }
        PlayerAbilityHelper.setAnimagusRegistered(player, true);
        send(player, "You are now a registered Animagus with the Ministry of Magic.", ChatFormatting.GREEN);
        return 1;
    }

    private static int opUnlock(ServerPlayer player) {
        PlayerAbilityHelper.setAnimagusUnlocked(player, true);
        if (PlayerAbilityHelper.getAnimagusFormId(player) == null) {
            PlayerAbilityHelper.setAnimagusFormId(player, AnimagusForms.defaultFormId());
        }
        send(player, "[op] Animagus unlocked for " + player.getName().getString() + ".", ChatFormatting.YELLOW);
        return 1;
    }

    private static int opReset(ServerPlayer player) {
        AnimagusTransformService.forceRevert(player);
        PlayerAbilityHelper.setCurrentlyTransformed(player, false);
        PlayerAbilityHelper.setAnimagusUnlocked(player, false);
        PlayerAbilityHelper.setAnimagusRegistered(player, false);
        PlayerAbilityHelper.setAnimagusFormId(player, null);
        send(player, "[op] Animagus state reset for " + player.getName().getString() + ".", ChatFormatting.YELLOW);
        return 1;
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static void send(ServerPlayer player, String msg, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(msg).withStyle(color));
    }
}
