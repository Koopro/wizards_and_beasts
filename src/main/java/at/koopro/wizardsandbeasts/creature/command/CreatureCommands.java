package at.koopro.wizardsandbeasts.creature.command;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.creature.CreatureDefinition;
import at.koopro.wizardsandbeasts.creature.CreatureDefinitionRegistry;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModCreatures;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;

/**
 * {@code /wandb creature …} (gamemaster). Summons generic creatures by id and lists the build status.
 * Access is gated by {@link Module#CREATURES}; registration is always present.
 */
public final class CreatureCommands {

    private CreatureCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("creature")
                .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                .then(Commands.literal("summon")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        ModCreatures.MANIFEST.stream().map(ModCreatures.Spec::id), b))
                                .executes(ctx -> summon(ctx.getSource(), StringArgumentType.getString(ctx, "id"), 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 32))
                                        .executes(ctx -> summon(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())));
    }

    private static int summon(CommandSourceStack src, String id, int count)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!ModuleManager.isEnabled(Module.CREATURES)) {
            src.sendFailure(Component.literal("The CREATURES module is disabled."));
            return 0;
        }
        var holder = ModCreatures.ENTITIES.get(id);
        if (holder == null) {
            src.sendFailure(Component.literal("Unknown creature id: " + id));
            return 0;
        }
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();
        EntityType<?> type = holder.get();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Entity entity = type.create(level, EntitySpawnReason.COMMAND);
            if (entity == null) {
                continue;
            }
            entity.setPos(player.getX(), player.getY(), player.getZ());
            if (level.addFreshEntity(entity)) {
                spawned++;
            }
        }
        int finalSpawned = spawned;
        src.sendSuccess(() -> Component.literal("Summoned " + finalSpawned + "x " + id)
                .withStyle(ChatFormatting.GREEN), true);
        return spawned;
    }

    private static int list(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("Generic creatures: " + ModCreatures.MANIFEST.size()
                + " registered, " + CreatureDefinitionRegistry.size() + " definitions loaded")
                .withStyle(ChatFormatting.GOLD), false);
        for (ModCreatures.Spec spec : ModCreatures.MANIFEST) {
            CreatureDefinition def = CreatureDefinitionRegistry.get(
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, spec.id()));
            boolean ok = def != null;
            src.sendSuccess(() -> Component.literal((ok ? "  [ok] " : "  [--] ") + spec.id()
                    + " [" + spec.loco() + "]")
                    .withStyle(ok ? ChatFormatting.GRAY : ChatFormatting.RED), false);
        }
        return 1;
    }
}
