package at.koopro.wizardsandbeasts.creature.companion;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * What an owner can ask of a companion.
 *
 * <p>Two kinds. A <b>mode</b> persists until another mode replaces it — follow, stay, rest, defend, patrol.
 * An <b>order</b> is done once and the creature falls back to its mode — return, search, assist, attack.
 * Which commands a species understands is data ({@link CompanionProfile#commands()}); each also needs the
 * creature to have reached {@link #requires()}, and a trait may refuse it ({@link PersonalityTrait#refuses()}).
 */
@NullMarked
public enum CompanionCommand implements StringRepresentable {
    FOLLOW(true, CompanionStage.TRUSTING),
    STAY(true, CompanionStage.TRUSTING),
    REST(true, CompanionStage.TRUSTING),
    DEFEND(true, CompanionStage.TRAINED),
    PATROL(true, CompanionStage.TRAINED),
    RETURN(false, CompanionStage.BONDED),
    SEARCH(false, CompanionStage.BONDED),
    ASSIST(false, CompanionStage.BONDED),
    ATTACK(false, CompanionStage.TRAINED);

    public static final Codec<CompanionCommand> CODEC = StringRepresentable.fromEnum(CompanionCommand::values);

    private final boolean mode;
    private final CompanionStage requires;

    CompanionCommand(boolean mode, CompanionStage requires) {
        this.mode = mode;
        this.requires = requires;
    }

    /** A standing mode rather than a one-off order. */
    public boolean isMode() {
        return mode;
    }

    /** The least stage at which a creature will obey it. */
    public CompanionStage requires() {
        return requires;
    }

    /** Needs the creature to be able to fight ({@code ATTACK_DAMAGE} and the species' say-so). */
    public boolean isCombat() {
        return this == DEFEND || this == ATTACK || this == PATROL;
    }

    /** A mode in which it keeps to its owner's side: following, or following to guard. */
    public boolean keepsToOwner() {
        return this == FOLLOW || this == DEFEND;
    }

    public String key() {
        return "companion.wizards_and_beasts.command." + getSerializedName();
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
