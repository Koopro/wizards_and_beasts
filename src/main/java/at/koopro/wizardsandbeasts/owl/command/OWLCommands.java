package at.koopro.wizardsandbeasts.owl.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.heritage.data.PlayerProfessionData;
import at.koopro.wizardsandbeasts.network.owl.OWLDataSyncPayload;
import at.koopro.wizardsandbeasts.network.owl.ProfessionSyncPayload;
import at.koopro.wizardsandbeasts.owl.OWLExaminationHandler;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

public final class OWLCommands {

    private OWLCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("owls")
                .then(Commands.literal("results")
                        .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> printResults(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("reset")
                        .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> resetExam(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("setprofession")
                        .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("profession", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(Profession.values()).map(Enum::name), b))
                                        .executes(ctx -> setProfession(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "profession"))))))
                .then(Commands.literal("setgrade")
                        .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("subject", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(OWLSubject.values()).map(Enum::name), b))
                                        .then(Commands.argument("grade", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                        Arrays.stream(OWLGrade.values()).map(Enum::name), b))
                                                .executes(ctx -> setGrade(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "subject"),
                                                        StringArgumentType.getString(ctx, "grade")))))));
    }

    private static int printResults(CommandSourceStack source, ServerPlayer player) {
        PlayerOWLData data = player.getData(ModAttachments.OWL_DATA.get());
        PlayerProfessionData profData = player.getData(ModAttachments.PROFESSION_DATA.get());

        if (!data.examTaken()) {
            source.sendSuccess(() -> Component.literal(player.getName().getString() + " has not taken their OWLs.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("OWL results for ")
                .withStyle(ChatFormatting.GOLD)
                .append(player.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(":").withStyle(ChatFormatting.GOLD)), false);

        for (OWLSubject subject : OWLSubject.values()) {
            OWLGrade grade = data.grades().getOrDefault(subject, OWLGrade.T);
            ChatFormatting gradeColor = grade.passing ? ChatFormatting.YELLOW : ChatFormatting.GRAY;
            source.sendSuccess(() -> Component.literal("  ")
                    .append(Component.translatable(subject.translationKey()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(grade.name()).withStyle(gradeColor)), false);
        }

        if (profData.profession() != null) {
            Profession prof = profData.profession();
            source.sendSuccess(() -> Component.literal("Profession: ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.translatable(prof.translationKey()).withStyle(ChatFormatting.AQUA)), false);
        } else {
            source.sendSuccess(() -> Component.literal("No profession chosen yet.")
                    .withStyle(ChatFormatting.GRAY), false);
        }

        return 1;
    }

    private static int resetExam(CommandSourceStack source, ServerPlayer player) {
        OWLExaminationHandler.resetExam(player);
        source.sendSuccess(() -> Component.literal("Reset OWL data for ")
                .withStyle(ChatFormatting.GREEN)
                .append(player.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE)), true);
        return 1;
    }

    private static int setProfession(CommandSourceStack source, ServerPlayer player, String profName) {
        Profession profession;
        try {
            profession = Profession.valueOf(profName.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Unknown profession: " + profName).withStyle(ChatFormatting.RED));
            return 0;
        }
        player.setData(ModAttachments.PROFESSION_DATA.get(), new PlayerProfessionData(profession));
        PacketDistributor.sendToPlayer(player, new ProfessionSyncPayload(profession));
        source.sendSuccess(() -> Component.literal("Set profession of ")
                .withStyle(ChatFormatting.GREEN)
                .append(player.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" to ").withStyle(ChatFormatting.GREEN))
                .append(Component.translatable(profession.translationKey()).withStyle(ChatFormatting.AQUA)), true);
        return 1;
    }

    private static int setGrade(CommandSourceStack source, ServerPlayer player, String subjectName, String gradeName) {
        OWLSubject subject;
        try {
            subject = OWLSubject.valueOf(subjectName.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Unknown subject: " + subjectName).withStyle(ChatFormatting.RED));
            return 0;
        }
        OWLGrade grade;
        try {
            grade = OWLGrade.valueOf(gradeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Unknown grade: " + gradeName).withStyle(ChatFormatting.RED));
            return 0;
        }

        PlayerOWLData existing = player.getData(ModAttachments.OWL_DATA.get());
        Map<OWLSubject, OWLGrade> newGrades = new EnumMap<>(existing.grades());
        newGrades.put(subject, grade);
        PlayerOWLData updated = new PlayerOWLData(java.util.Collections.unmodifiableMap(newGrades), existing.examTaken());
        player.setData(ModAttachments.OWL_DATA.get(), updated);
        PacketDistributor.sendToPlayer(player, new OWLDataSyncPayload(newGrades, existing.examTaken()));

        source.sendSuccess(() -> Component.literal("Set ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.translatable(subject.translationKey()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" to ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(grade.name()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" for ").withStyle(ChatFormatting.GREEN))
                .append(player.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE)), true);
        return 1;
    }
}
