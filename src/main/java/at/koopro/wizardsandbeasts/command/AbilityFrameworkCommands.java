package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.ability.AbilityResolver;
import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import at.koopro.wizardsandbeasts.ability.def.AbilityDefinitionRegistry;
import at.koopro.wizardsandbeasts.ability.grant.AbilityGrants;
import at.koopro.wizardsandbeasts.ability.grant.AbilityKey;
import at.koopro.wizardsandbeasts.ability.grant.DebugAbilityGrantSource;
import at.koopro.wizardsandbeasts.ability.select.AbilitySelectionState;
import at.koopro.wizardsandbeasts.network.ability.AbilitySelectionSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.skill.AbilityGrantsSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wandb ability list|grant|revoke} — debug surface for the ability framework (permission level 2).
 * {@code grant}/{@code revoke} drive the {@link DebugAbilityGrantSource} override; {@code list} dumps the
 * player's resolved grant/type/module/cooldown state. Registered under {@code /wandb} (the mod's command
 * root; the prompt's {@code /wizardsandbeasts} is a deprecated alias of the same root).
 */
@NullMarked
public final class AbilityFrameworkCommands {

    private AbilityFrameworkCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("ability")
                .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> list(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("grant")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("id", IdentifierArgument.id())
                                        .executes(ctx -> grant(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IdentifierArgument.getId(ctx, "id"))))))
                .then(Commands.literal("revoke")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("id", IdentifierArgument.id())
                                        .executes(ctx -> revoke(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IdentifierArgument.getId(ctx, "id"))))));
    }

    private static int list(CommandSourceStack source, ServerPlayer target) {
        AbilityGrants grants = AbilityResolver.grants(target);
        AbilitySelectionState state = target.getData(ModAttachments.ABILITY_SELECTION.get());
        long gameTime = target.level().getGameTime();

        source.sendSuccess(() -> Component.literal("Abilities for ")
                .withStyle(ChatFormatting.GOLD)
                .append(target.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE)), false);

        int shown = 0;
        for (AbilityDefinition def : AbilityDefinitionRegistry.getAll()) {
            boolean granted = grants.has(AbilityKey.of(def.id().toString()));
            if (!granted) {
                continue;
            }
            shown++;
            boolean moduleOk = AbilityResolver.moduleAllows(def);
            long cd = state.cooldownRemaining(def.id(), gameTime);
            String sources = grants.sourcesOf(AbilityKey.of(def.id().toString())).toString();
            Component line = Component.literal("- ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(def.id().toString()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" [" + def.type().getSerializedName() + "]")
                            .withStyle(moduleOk ? ChatFormatting.GREEN : ChatFormatting.RED))
                    .append(Component.literal(" src=" + sources).withStyle(ChatFormatting.DARK_GRAY))
                    .append(cd > 0
                            ? Component.literal(" cd=" + cd + "t").withStyle(ChatFormatting.YELLOW)
                            : Component.empty());
            source.sendSuccess(() -> line, false);
        }
        if (shown == 0) {
            source.sendSuccess(() -> Component.literal("(no granted abilities)").withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return shown;
    }

    private static int grant(CommandSourceStack source, ServerPlayer target, Identifier id) {
        boolean added = DebugAbilityGrantSource.INSTANCE.grant(target, id.toString());
        resync(target);
        source.sendSuccess(() -> Component.literal((added ? "Granted " : "Already granted ") + id + " to ")
                .withStyle(ChatFormatting.GRAY)
                .append(target.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE)), true);
        if (!AbilityDefinitionRegistry.contains(id)) {
            source.sendSuccess(() -> Component.literal("  note: no definition registered for " + id)
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return added ? 1 : 0;
    }

    private static int revoke(CommandSourceStack source, ServerPlayer target, Identifier id) {
        boolean removed = DebugAbilityGrantSource.INSTANCE.revoke(target, id.toString());
        resync(target);
        source.sendSuccess(() -> Component.literal((removed ? "Revoked " : "Was not granted ") + id + " from ")
                .withStyle(ChatFormatting.GRAY)
                .append(target.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE)), true);
        return removed ? 1 : 0;
    }

    private static void resync(ServerPlayer target) {
        AbilityGrantsSyncS2CPayload.syncToPlayer(target);
        AbilitySelectionSyncS2CPayload.syncToPlayer(target);
    }
}
