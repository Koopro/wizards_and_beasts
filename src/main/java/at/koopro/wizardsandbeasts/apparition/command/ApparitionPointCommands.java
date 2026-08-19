package at.koopro.wizardsandbeasts.apparition.command;

import at.koopro.wizardsandbeasts.apparition.ApparitionAnchors;
import at.koopro.wizardsandbeasts.apparition.ApparitionPoint;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.apparition.ApparitionServerLogic;
import at.koopro.wizardsandbeasts.apparition.licence.ApparitionLicence;
import at.koopro.wizardsandbeasts.apparition.PlayerApparitionPoints;
import at.koopro.wizardsandbeasts.apparition.sidealong.SideAlongService;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.util.ChatReport;
import at.koopro.wizardsandbeasts.network.apparition.ApparitionPointsSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wandb magic apparate …} — the player-facing half of Apparition: the places they have memorised,
 * a standing side-along offer, and the Ministry test that earns them a licence.
 *
 * <p>Unlike the ward commands next door this is all ordinary gameplay, so it is <b>not</b>
 * permission-gated; every subcommand only ever touches the caller's own record.
 */
@NullMarked
public final class ApparitionPointCommands {

    private ApparitionPointCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("apparate")
                .then(Commands.literal("mark")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> mark(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("forget")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> forget(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("accept")
                        .executes(ctx -> accept(ctx.getSource())))
                .then(Commands.literal("licence")
                        .executes(ctx -> licence(ctx.getSource())))
                .then(Commands.literal("test")
                        .executes(ctx -> takeTest(ctx.getSource())));
    }

    /**
     * Where the player stands on the way to a licence. Not decoration: the practice requirement is
     * invisible otherwise — proficiency is server-side and unsynced — so without this a wizard has no
     * way to know whether they are one jump from the test or fifty.
     */
    private static int licence(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        boolean held = PlayerAbilityHelper.isApparitionLicensed(player);
        ChatReport report = ChatReport.of(Component.translatable("apparition.wizards_and_beasts.licence.title"))
                .subtitle(Component.translatable(held
                        ? "apparition.wizards_and_beasts.licence.held"
                        : "apparition.wizards_and_beasts.licence.not_held"));

        if (held) {
            report.send(source);
            return 1;
        }

        // The two things standing between the player and a licence, in the order the gate applies them.
        report.flag(Component.translatable("apparition.wizards_and_beasts.licence.row.training").getString(),
                        ApparitionServerLogic.hasTraining(player))
                .bar(Component.translatable("apparition.wizards_and_beasts.licence.row.practice").getString(),
                        ApparitionLicence.progress(player));

        ApparitionLicence.Eligibility verdict = ApparitionLicence.evaluate(player);
        report.divider();
        if (verdict.eligible()) {
            report.action(Component.translatable("apparition.wizards_and_beasts.licence.action").getString(),
                    "/wandb magic apparate test",
                    Component.translatable("apparition.wizards_and_beasts.licence.action.hover"));
        } else {
            report.note(verdict.reason());
        }
        report.send(source);
        return verdict.eligible() ? 1 : 0;
    }

    /**
     * Sits the Ministry Apparition Test. A pass is a discrete, once-per-character event, so it is a
     * toast rather than the action bar; a refusal carries the reason that says what is still missing.
     */
    private static int takeTest(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        ApparitionLicence.Eligibility verdict = ApparitionLicence.takeTest(player);
        if (!verdict.eligible()) {
            PlayerFeedback.refuse(player,
                    Component.translatable("apparition.wizards_and_beasts.test.refused"),
                    verdict.reason());
            return 0;
        }
        PlayerFeedback.unlocked(player,
                Component.translatable("apparition.wizards_and_beasts.test.passed"),
                Component.translatable("apparition.wizards_and_beasts.test.passed.body"));
        return 1;
    }

    /**
     * Takes up a standing side-along offer. A chat affordance for what
     * {@code ApparitionSideAlongAcceptC2SPayload} will do from a prompt UI once one exists.
     */
    private static int accept(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        return SideAlongService.accept(player) ? 1 : 0;
    }

    private static int mark(CommandSourceStack source, String rawName) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        String name = ApparitionPoint.sanitize(rawName);
        if (name.isEmpty()) {
            feedback(source, "That is not a usable name.", ChatFormatting.RED);
            return 0;
        }
        if (!ApparitionServerLogic.canApparate(player)) {
            feedback(source, "You cannot Apparate, so there is no point memorising destinations.", ChatFormatting.RED);
            return 0;
        }

        PlayerApparitionPoints points = points(player);
        int capacity = ApparitionAnchors.capacity(player);
        boolean replacing = points.byName(name) != null;
        if (!replacing && points.isFull(capacity)) {
            feedback(source, "You can only hold " + capacity
                    + " destinations in mind. Forget one, or re-use the name of the one you are replacing.",
                    ChatFormatting.RED);
            return 0;
        }

        ApparitionPoint point = new ApparitionPoint(name, player.level().dimension(), player.position(), player.getYRot());
        setPoints(player, points.with(point, capacity));
        feedback(source, (replacing ? "Re-memorised " : "Memorised ") + name + ".", ChatFormatting.LIGHT_PURPLE);
        return 1;
    }

    private static int forget(CommandSourceStack source, String rawName) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        PlayerApparitionPoints points = points(player);
        PlayerApparitionPoints next = points.without(rawName);
        if (next == points) {
            feedback(source, "You have no destination by that name.", ChatFormatting.RED);
            return 0;
        }
        setPoints(player, next);
        feedback(source, "Forgot " + ApparitionPoint.sanitize(rawName) + ".", ChatFormatting.GRAY);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        PlayerApparitionPoints points = points(player);
        if (points.points().isEmpty()) {
            feedback(source, "You have memorised no destinations.", ChatFormatting.GRAY);
            return 0;
        }
        feedback(source, "Memorised destinations:", ChatFormatting.LIGHT_PURPLE);
        for (ApparitionPoint point : points.points()) {
            boolean sameWorld = player.level().dimension().equals(point.dimension());
            String detail = sameWorld
                    ? Mth.floor(player.position().distanceTo(point.position())) + "m away"
                    : "another world";
            feedback(source, " · " + point.name() + " (" + detail + ")",
                    sameWorld ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY);
        }
        return points.points().size();
    }

    private static PlayerApparitionPoints points(ServerPlayer player) {
        return player.getData(ModAttachments.APPARITION_POINTS.get());
    }

    private static void setPoints(ServerPlayer player, PlayerApparitionPoints next) {
        player.setData(ModAttachments.APPARITION_POINTS.get(), next);
        ApparitionPointsSyncS2CPayload.syncToPlayer(player);
    }

    private static void feedback(CommandSourceStack source, String message, ChatFormatting color) {
        source.sendSuccess(() -> Component.literal(message).withStyle(color), false);
    }
}
