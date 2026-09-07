package at.koopro.wizardsandbeasts.heritage.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.heritage.nutrition.NutritionPolicy;
import at.koopro.wizardsandbeasts.heritage.nutrition.NutritionPolicyResolver;
import at.koopro.wizardsandbeasts.heritage.vampire.ThirstStage;
import at.koopro.wizardsandbeasts.heritage.vampire.VampireBloodAPI;
import at.koopro.wizardsandbeasts.heritage.vampire.VampireBloodConfig;
import at.koopro.wizardsandbeasts.heritage.vampire.VampireBloodData;
import at.koopro.wizardsandbeasts.util.ChatReport;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wandb player heritage blood …} — reading and moving a blood pool by hand.
 *
 * <p>Sits under {@code heritage} rather than beside it because the pool only exists as a consequence of
 * a heritage; a player with no heritage has nothing here to inspect. The read verb is ungated for the
 * reason {@link HeritageCommands}'s own reads are: a player checking their own thirst is not an
 * administrative act. The three write verbs are, because they move a resource the game is supposed to be
 * charging for.
 *
 * <p>{@code drain} exists specifically so the bottom of the ladder is reachable in one command. Waiting
 * eight minutes for a pool to empty is not a test of what STARVING does; it is a test of patience.
 */
@NullMarked
public final class BloodCommands {

    private BloodCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("blood")
                .then(Commands.literal("get")
                        .executes(ctx -> get(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> get(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("set")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", FloatArgumentType.floatArg(0.0f))
                                        .executes(ctx -> set(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                FloatArgumentType.getFloat(ctx, "amount"))))))
                .then(Commands.literal("fill")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> fill(
                                        ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("drain")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> set(
                                        ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), 0.0f))));
    }

    private static int get(CommandSourceStack source, ServerPlayer target) {
        NutritionPolicy policy = NutritionPolicyResolver.resolve(target);
        ChatReport report = ChatReport.of("Blood — " + target.getName().getString())
                .row("Nutrition", policy.name());

        if (policy != NutritionPolicy.BLOOD) {
            report.note("Not on the blood economy; this pool is inert.").send(source);
            return 1;
        }

        VampireBloodData data = VampireBloodAPI.getData(target);
        ThirstStage stage = data.getThirstStage();
        report.meter("Pool", Math.round(data.getBlood()), Math.round(data.getMaxBlood()))
                .row("Thirst", Component.translatable(stage.translationKey()))
                .row("Since feed", data.getTicksSinceLastFeed() + " ticks")
                .row("Exhaustion", String.format("%.2f", data.getExhaustion()))
                // The mirrored value, so an operator can see at a glance that the hidden vanilla bar and
                // the pool agree. They disagreeing is the failure mode this whole layer exists to prevent.
                .row("Mirrored food", target.getFoodData().getFoodLevel() + "/20")
                .flag("Damaging", stage == ThirstStage.STARVING
                        && data.getBlood() <= VampireBloodConfig.starvationDamageFloor)
                .send(source);
        return 1;
    }

    private static int set(CommandSourceStack source, ServerPlayer target, float amount) {
        if (!refuseIfNotBloodDrinker(source, target)) {
            return 0;
        }
        VampireBloodAPI.setBlood(target, amount);
        float now = VampireBloodAPI.getBlood(target);
        source.sendSuccess(() -> Component.literal(String.format("Set %s's blood to %.1f (%s).",
                        target.getName().getString(), now, VampireBloodAPI.getThirstStage(target).name()))
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int fill(CommandSourceStack source, ServerPlayer target) {
        if (!refuseIfNotBloodDrinker(source, target)) {
            return 0;
        }
        // seed() rather than setBlood(max): filling should also resize the pool to whatever the config
        // now says, which is the only way an operator can apply a raised vampireBloodMaxBlood to a
        // character who was created before they raised it.
        VampireBloodAPI.seed(target);
        source.sendSuccess(() -> Component.literal(String.format("Filled %s's blood to %.1f.",
                        target.getName().getString(), VampireBloodAPI.getBlood(target)))
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /** Fails loudly rather than silently writing a pool nothing will ever read. */
    private static boolean refuseIfNotBloodDrinker(CommandSourceStack source, ServerPlayer target) {
        if (VampireBloodAPI.drinksBlood(target)) {
            return true;
        }
        source.sendFailure(Component.literal(target.getName().getString()
                        + " does not live on blood — their heritage has no blood_hunger lineage.")
                .withStyle(ChatFormatting.RED));
        return false;
    }
}
