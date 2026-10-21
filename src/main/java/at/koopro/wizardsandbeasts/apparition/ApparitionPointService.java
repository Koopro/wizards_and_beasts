package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.network.apparition.ApparitionPointsSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Memorising and forgetting Apparition destinations — one door for the {@code /wandb magic apparate} commands and the
 * Travel tab, so the two cannot drift apart.
 *
 * <p>Destination knowledge is a place the wizard has <em>stood</em>: {@link #mark} memorises the player's own
 * position, never a coordinate a client sends. That is the rule that makes Apparition a thing learned by going
 * somewhere, and it is also why no payload carries a position.
 */
@NullMarked
public final class ApparitionPointService {

    private ApparitionPointService() {}

    /** What a mark or forget did, and the line to tell the player. */
    public record Outcome(boolean done, Component message) {
        static Outcome yes(Component message) {
            return new Outcome(true, message.copy().withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        static Outcome no(Component message) {
            return new Outcome(false, message.copy().withStyle(ChatFormatting.RED));
        }
    }

    /** Memorises where {@code player} stands under {@code rawName}, replacing a destination of that name. */
    public static Outcome mark(ServerPlayer player, String rawName) {
        String name = ApparitionPoint.sanitize(rawName);
        if (name.isEmpty()) {
            return Outcome.no(Component.translatable("apparition.wizards_and_beasts.point.bad_name"));
        }
        if (!ApparitionServerLogic.canApparate(player)) {
            return Outcome.no(Component.translatable("apparition.wizards_and_beasts.point.cannot_apparate"));
        }
        PlayerApparitionPoints points = points(player);
        int capacity = ApparitionAnchors.capacity(player);
        int ceiling = ApparitionAnchors.ceiling(player);
        boolean replacing = points.byName(name) != null;
        if (!replacing && points.isFull(capacity, ceiling)) {
            return Outcome.no(Component.translatable("apparition.wizards_and_beasts.point.full",
                    Math.min(capacity, ceiling)));
        }
        ApparitionPoint point = new ApparitionPoint(name, player.level().dimension(), player.position(),
                player.getYRot());
        setPoints(player, points.with(point, capacity, ceiling));
        return Outcome.yes(Component.translatable(replacing
                ? "apparition.wizards_and_beasts.point.remarked" : "apparition.wizards_and_beasts.point.marked", name));
    }

    /** Forgets the destination called {@code rawName}. */
    public static Outcome forget(ServerPlayer player, String rawName) {
        PlayerApparitionPoints points = points(player);
        PlayerApparitionPoints next = points.without(rawName);
        if (next == points) {
            return Outcome.no(Component.translatable("apparition.wizards_and_beasts.point.unknown"));
        }
        setPoints(player, next);
        return Outcome.yes(Component.translatable("apparition.wizards_and_beasts.point.forgot",
                ApparitionPoint.sanitize(rawName)));
    }

    private static PlayerApparitionPoints points(ServerPlayer player) {
        return player.getData(ModAttachments.APPARITION_POINTS.get());
    }

    private static void setPoints(ServerPlayer player, PlayerApparitionPoints next) {
        player.setData(ModAttachments.APPARITION_POINTS.get(), next);
        ApparitionPointsSyncS2CPayload.syncToPlayer(player);
    }
}
