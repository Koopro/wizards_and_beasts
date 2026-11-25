package at.koopro.wizardsandbeasts.house;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** The four Hogwarts houses, with the colours the scarves and banners wear. */
@NullMarked
public enum House {
    GRYFFINDOR(0xAE0001, 0xEEBA30, ChatFormatting.RED, "house_lion"),
    HUFFLEPUFF(0xECB939, 0x372E29, ChatFormatting.YELLOW, "house_badger"),
    RAVENCLAW(0x222F5B, 0x946B2D, ChatFormatting.BLUE, "house_eagle"),
    SLYTHERIN(0x1A472A, 0xAAAAAA, ChatFormatting.DARK_GREEN, "house_serpent");

    private final int primary;
    private final int secondary;
    private final ChatFormatting chat;
    private final String livery;

    House(int primary, int secondary, ChatFormatting chat, String livery) {
        this.primary = primary;
        this.secondary = secondary;
        this.chat = chat;
        this.livery = livery;
    }

    /**
     * The wardrobe livery in this house's colours ({@code data/.../livery/house_lion.json}). The liveries are named
     * for the house animal, as the tailor's stitching recipes are.
     */
    public Identifier livery() {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, livery);
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
