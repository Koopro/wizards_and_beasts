package at.koopro.wizardsandbeasts.ministry.law;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

/**
 * How badly the Ministry wants a word, derived from current notoriety. Bands rather than a raw number so
 * the enforcement side has something stable to react to — Aurors are dispatched on entering a band, not on
 * every point of heat.
 */
@NullMarked
public enum WantedLevel {

    /** No open interest. */
    CLEAR(0.0f, ChatFormatting.GRAY),
    /** On file. Paperwork offences and cooled-off heat land here. */
    OF_INTEREST(15.0f, ChatFormatting.YELLOW),
    /** Actively sought. Aurors start being dispatched. */
    WANTED(40.0f, ChatFormatting.GOLD),
    /** Dangerous. More Aurors, sent more often. */
    DANGEROUS(70.0f, ChatFormatting.RED),
    /** Undesirable No. 1. */
    UNDESIRABLE(90.0f, ChatFormatting.DARK_RED);

    private final float threshold;
    private final ChatFormatting color;

    WantedLevel(float threshold, ChatFormatting color) {
        this.threshold = threshold;
        this.color = color;
    }

    public float threshold() {
        return threshold;
    }

    public ChatFormatting color() {
        return color;
    }

    /** True once the Ministry will actually send someone. */
    public boolean dispatchesAurors() {
        return ordinal() >= WANTED.ordinal();
    }

    /** How many Aurors a dispatch sends at this level — escalation is in numbers, not in individual power. */
    public int aurorsPerDispatch() {
        return switch (this) {
            case CLEAR, OF_INTEREST -> 0;
            case WANTED -> 1;
            case DANGEROUS -> 2;
            case UNDESIRABLE -> 3;
        };
    }

    public Component displayName() {
        return Component.translatable("ministry.wizards_and_beasts.wanted." + name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(color);
    }

    /** The band {@code notoriety} falls in. */
    public static WantedLevel forNotoriety(float notoriety) {
        WantedLevel result = CLEAR;
        for (WantedLevel level : values()) {
            if (notoriety >= level.threshold) {
                result = level;
            }
        }
        return result;
    }
}
