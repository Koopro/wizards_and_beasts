package at.koopro.wizardsandbeasts.admin.command;

import at.koopro.wizardsandbeasts.admin.AdminConfirmations;
import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * How every admin command turns an {@link AdminResult} into chat — shared by {@code /wandb admin config} and
 * {@code /wandb admin spell} so the two speak the same way. A result held for confirmation is parked in
 * {@link AdminConfirmations} and the actor told how to confirm it.
 */
@NullMarked
final class AdminCommandFeedback {

    private AdminCommandFeedback() {}

    /** Reports one result; a held one is parked under {@code action} for {@code /wandb admin config confirm}. */
    static int report(CommandSourceStack source, AdminResult result, AdminConfirmations.Action action) {
        if (result.needsConfirmation()) {
            AdminConfirmations.hold(AdminContext.of(source), List.of(action), Util.getMillis());
            source.sendFailure(confirmationPrompt(result));
            return 0;
        }
        return report(source, result);
    }

    static int report(CommandSourceStack source, AdminResult result) {
        String path = result.settingId().getPath();
        switch (result.status()) {
            case APPLIED -> {
                MutableComponent message = Component.translatable("command.wizards_and_beasts.admin.config.applied",
                        path, result.previousValue(), result.value()).withStyle(ChatFormatting.GREEN);
                if (result.restartRequired()) {
                    message.append(" ").append(Component.translatable("command.wizards_and_beasts.admin.config.restart")
                            .withStyle(ChatFormatting.GOLD));
                } else {
                    at.koopro.wizardsandbeasts.admin.config.AdminSetting<?> setting =
                            at.koopro.wizardsandbeasts.admin.AdminSettings.registry().get(result.settingId());
                    if (setting != null && setting.applyMode() != at.koopro.wizardsandbeasts.admin.config.ApplyMode.RUNTIME) {
                        message.append(" ").append(Component.translatable(setting.applyMode().explanationKey())
                                .withStyle(ChatFormatting.GOLD));
                    }
                }
                source.sendSuccess(() -> message, true);
                return 1;
            }
            case UNCHANGED -> {
                source.sendSuccess(() -> Component.translatable("command.wizards_and_beasts.admin.config.unchanged",
                        path, result.value()).withStyle(ChatFormatting.GRAY), false);
                return 1;
            }
            default -> {
                source.sendFailure(rejection(result));
                return 0;
            }
        }
    }

    /** Reports a batch; held members are parked together for one confirmation. */
    static int reportBatch(CommandSourceStack source, List<AdminResult> results) {
        if (results.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.wizards_and_beasts.admin.config.reset_section.nothing")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        long applied = results.stream().filter(AdminResult::applied).count();
        long refused = results.stream().filter(r -> r.rejected() && !r.needsConfirmation()).count();
        List<AdminConfirmations.Action> held = new ArrayList<>();
        for (AdminResult result : results) {
            if (result.needsConfirmation()) {
                held.add(new AdminConfirmations.Action(result.settingId(), null));
                source.sendFailure(confirmationPrompt(result));
            } else if (result.rejected()) {
                source.sendFailure(rejection(result));
            }
        }
        AdminConfirmations.hold(AdminContext.of(source), held, Util.getMillis());
        source.sendSuccess(() -> Component.translatable("command.wizards_and_beasts.admin.config.reset_section.done",
                applied, refused).withStyle(refused == 0 ? ChatFormatting.GREEN : ChatFormatting.GOLD), true);
        return applied > 0 ? 1 : 0;
    }

    static Component rejection(AdminResult result) {
        MutableComponent message = Component.translatable("command.wizards_and_beasts.admin.config.rejected",
                result.settingId().getPath(),
                result.rejection() == null ? "" : Component.translatable(result.rejection().translationKey()));
        if (result.detailKey() != null) {
            message.append(" ").append(Component.translatable(result.detailKey()));
        }
        return message.withStyle(ChatFormatting.RED);
    }

    private static Component confirmationPrompt(AdminResult result) {
        MutableComponent message = Component.translatable("command.wizards_and_beasts.admin.config.confirm_needed",
                result.settingId().getPath()).withStyle(ChatFormatting.GOLD);
        if (result.detailKey() != null) {
            message.append(" ").append(Component.translatable(result.detailKey()).withStyle(ChatFormatting.YELLOW));
        }
        return message;
    }
}
