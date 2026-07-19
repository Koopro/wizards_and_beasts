package at.koopro.wizardsandbeasts.ministry.post;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.NullMarked;

/**
 * How the Ministry reaches a wizard.
 *
 * <p>Deliberately a seam rather than a chat call: the mod has no owl-post system today — the {@code OWLS}
 * module is the O.W.L. <i>examination</i> system, and there is no owl entity or letter item anywhere — so
 * notices currently arrive as a formatted message. When a carrier owl exists it becomes another
 * implementation behind this same call, and no caller changes.
 */
@NullMarked
public final class MinistryPost {

    private MinistryPost() {}

    /** Formal correspondence: summons, sentences, licence decisions. */
    public static void send(ServerPlayer recipient, Component subject, Component body) {
        recipient.sendSystemMessage(Component.literal("✉ ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("ministry.wizards_and_beasts.post.header")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        recipient.sendSystemMessage(subject.copy().withStyle(ChatFormatting.YELLOW));
        recipient.sendSystemMessage(body.copy().withStyle(ChatFormatting.GRAY));
        recipient.level().playSound(null, recipient.blockPosition(),
                SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.7f, 1.0f);
    }

    /** Terse, immediate notice — shown on the action bar rather than as a letter. */
    public static void notify(ServerPlayer recipient, Component message, ChatFormatting style) {
        recipient.displayClientMessage(message.copy().withStyle(style), true);
    }
}
