package at.koopro.wizardsandbeasts.house;

import net.minecraft.ChatFormatting;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** The four Hogwarts houses, with the colours the scarves and banners wear. */
@NullMarked
public enum House {
    GRYFFINDOR(0xAE0001, 0xEEBA30, ChatFormatting.RED),
    HUFFLEPUFF(0xECB939, 0x372E29, ChatFormatting.YELLOW),
    RAVENCLAW(0x222F5B, 0x946B2D, ChatFormatting.BLUE),
    SLYTHERIN(0x1A472A, 0xAAAAAA, ChatFormatting.DARK_GREEN);

    private final int primary;
    private final int secondary;
    private final ChatFormatting chat;

    House(int primary, int secondary, ChatFormatting chat) {
        this.primary = primary;
        this.secondary = secondary;
        this.chat = chat;
    }

    public int primary() {
        return primary;
    }

    public int secondary() {
        return secondary;
    }

    public ChatFormatting chat() {
        return chat;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String nameKey() {
        return "house.wizards_and_beasts." + id();
    }

    public static @Nullable House byId(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (House house : values()) {
            if (house.id().equals(id)) {
                return house;
            }
        }
        return null;
    }
}
