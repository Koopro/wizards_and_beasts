package at.koopro.wizardsandbeasts.broom.command;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.broom.BroomDefinitionRegistry;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import at.koopro.wizardsandbeasts.registry.BroomItemRegistry;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class BroomCommands {
    private BroomCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("broom")
                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                .then(Commands.literal("give")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("variant_id", StringArgumentType.string())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(getIds(), builder))
                                        .executes(BroomCommands::give))))
                .then(Commands.literal("info")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(BroomCommands::info)))
                .then(Commands.literal("reload")
                        .executes(BroomCommands::reload))
                .then(Commands.literal("stats")
                        .then(Commands.argument("variant_id", StringArgumentType.string())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(getIds(), builder))
                                .executes(BroomCommands::stats)))
                .then(Commands.literal("setdurability")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(BroomCommands::setDurability)))));
    }

    private static int give(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        Identifier id = Identifier.parse(StringArgumentType.getString(context, "variant_id"));
        ItemStack stack = new ItemStack(BroomItemRegistry.BROOM_ITEM.get());
        stack.set(ModDataComponents.BROOM_DEFINITION.get(), id);
        player.getInventory().add(stack);
        context.getSource().sendSuccess(() -> Component.literal("Gave broom variant: " + id), true);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        if (!(player.getVehicle() instanceof BroomEntity broom)) {
            context.getSource().sendFailure(Component.literal("Player is not riding a broom."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Riding broom definition: " + broom.getDefinitionId())
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        var server = context.getSource().getServer();
        server.reloadResources(server.getPackRepository().getSelectedIds()).thenRun(() ->
                context.getSource().sendSuccess(() -> Component.literal("Broom definitions reloaded."), true));
        return 1;
    }

    private static int stats(CommandContext<CommandSourceStack> context) {
        Identifier id = Identifier.parse(StringArgumentType.getString(context, "variant_id"));
        BroomDefinition def = BroomDefinitionRegistry.get(id);
        if (def == null) {
            context.getSource().sendFailure(Component.literal("Unknown broom definition: " + id));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(String.format("[%s] tier=%s speed=%.2f accel=%.3f boost=x%.2f (%dt / %dt cd) gravity=%.3f handling=%.2f stability=%.2f durability=%d",
                def.displayName().getString(), def.tier().name(), def.maxSpeed(), def.acceleration(),
                def.boostMultiplier(), def.boostDurationTicks(), def.boostCooldownTicks(),
                def.weakGravity(), def.handlingRating(), def.stabilityRating(), def.durability())), false);
        return 1;
    }

    private static int setDurability(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        int value = IntegerArgumentType.getInteger(context, "value");
        if (!(player.getVehicle() instanceof BroomEntity broom)) {
            context.getSource().sendFailure(Component.literal("Player is not riding a broom."));
            return 0;
        }
        broom.setCurrentDurability(value);
        context.getSource().sendSuccess(() -> Component.literal("Set broom durability to " + value), true);
        return 1;
    }

    private static List<String> getIds() {
        return BroomDefinitionRegistry.getAll().stream().map(def -> def.id().toString()).sorted().toList();
    }
}
