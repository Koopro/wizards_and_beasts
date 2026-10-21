package at.koopro.wizardsandbeasts.creature.companion;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * The kinds of time spent together that deepen a bond, each with a daily cap.
 *
 * <p>The cap is what keeps a bond from being clicked to the top: a day of feeding, a day of travelling and a
 * day of hunting together are each worth something, and none is worth everything. Personality scales the
 * gain ({@link PersonalityTrait#bondMultipliers()}): a playful creature loves company, a greedy one loves
 * treats, a brave one loves a fight.
 */
@NullMarked
public enum Interaction implements StringRepresentable {
    /** Food from the hand (the profile's feeds; the existing cooldown still applies). */
    FEED(1, 0),
    /** Petting, and feeding it while it is hurt. */
    CARE(3, 6),
    /** Ground covered together. */
    EXPLORE(1, 6),
    /** A threat beaten together. */
    COMBAT(2, 6),
    /** An order carried out. */
    TRAINING(1, 5),
    /** Its favourite treat, once a day. */
    GIFT(8, 8),
    /** It found, healed or fetched something for you. */
    HELP(2, 6);

    public static final Codec<Interaction> CODEC = StringRepresentable.fromEnum(Interaction::values);

    private final int baseGain;
    private final int dailyCap;

    Interaction(int baseGain, int dailyCap) {
        this.baseGain = baseGain;
        this.dailyCap = dailyCap;
    }

    public int baseGain() {
        return baseGain;
    }

    /** Most bond this kind can add in one in-game day; 0 = no cap of its own (feeding has its cooldown). */
    public int dailyCap() {
        return dailyCap;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
