package at.koopro.wizardsandbeasts.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

/**
 * Chat message formatting helpers. Replaces scattered {@code \u00A7} color code usage.
 */
public final class ChatHelper {

    private ChatHelper() {}

    // ── Color code constants ────────────────────────────────────────────

    private static final String RED = "\u00A7c";
    private static final String GREEN = "\u00A7a";
    private static final String YELLOW = "\u00A7e";
    private static final String GOLD = "\u00A76";
    private static final String GRAY = "\u00A77";
    private static final String DARK_GRAY = "\u00A78";
    private static final String AQUA = "\u00A7b";
    private static final String WHITE = "\u00A7f";

    // ── Component builders ──────────────────────────────────────────────

    public static MutableComponent error(String text) {
        return Component.literal(RED + text);
    }

    public static MutableComponent success(String text) {
        return Component.literal(GREEN + text);
    }

    public static MutableComponent warning(String text) {
        return Component.literal(YELLOW + text);
    }

    public static MutableComponent gold(String text) {
        return Component.literal(GOLD + text);
    }

    public static MutableComponent info(String text) {
        return Component.literal(GRAY + text);
    }

    public static MutableComponent dim(String text) {
        return Component.literal(DARK_GRAY + text);
    }

    public static MutableComponent aqua(String text) {
        return Component.literal(AQUA + text);
    }

    public static MutableComponent white(String text) {
        return Component.literal(WHITE + text);
    }

    /**
     * Multi-colored text: combines multiple color segments.
     * Usage: {@code ChatHelper.colored("§cError: §7something went wrong")}
     */
    public static MutableComponent colored(String text) {
        return Component.literal(text);
    }

    // ── Player message shortcuts ────────────────────────────────────────

    /**
     * Sends a chat message to the player.
     */
    public static void send(Player player, Component message) {
        player.displayClientMessage(message, false);
    }

    /**
     * Sends a colored chat message to the player.
     */
    public static void send(Player player, String text) {
        player.displayClientMessage(Component.literal(text), false);
    }

    /**
     * Sends an action bar message (above hotbar).
     */
    public static void sendActionBar(Player player, Component message) {
        player.displayClientMessage(message, true);
    }

    /**
     * Sends a colored action bar message.
     */
    public static void sendActionBar(Player player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }

    /**
     * Sends an error message to the player's chat.
     */
    public static void sendError(Player player, String text) {
        send(player, error(text));
    }

    /**
     * Sends a success message to the player's chat.
     */
    public static void sendSuccess(Player player, String text) {
        send(player, success(text));
    }
}
