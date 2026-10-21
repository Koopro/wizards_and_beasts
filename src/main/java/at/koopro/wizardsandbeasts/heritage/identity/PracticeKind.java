package at.koopro.wizardsandbeasts.heritage.identity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * Something a people does with its own magic, counted towards its professions ({@link HeritagePractice}).
 *
 * <p>Wizardkind earn profession points by mastering spells. A people that holds no wand masters no spell, so before
 * this they stopped at the three points a new character is given. Practice is their road instead: the house-elf's
 * household charms and summons answered, the goblin's appraisals, the centaur's nights under the stars. Each kind
 * belongs to one people; practice another people's art and it counts for nothing.
 *
 * @param weight progress one use is worth ({@link HeritagePracticeRules})
 */
@NullMarked
public enum PracticeKind {
    ELF_SNAP(Heritage.HOUSE_ELF, 1),
    ELF_KITCHEN(Heritage.HOUSE_ELF, 2),
    ELF_SUMMONS(Heritage.HOUSE_ELF, 3),
    ELF_APPARITION(Heritage.HOUSE_ELF, 1),
    GOBLIN_APPRAISAL(Heritage.GOBLIN, 2),
    CENTAUR_STARS(Heritage.CENTAUR, 2);

    private final Heritage heritage;
    private final int weight;

    PracticeKind(Heritage heritage, int weight) {
        this.heritage = heritage;
        this.weight = weight;
    }

    public Heritage heritage() {
        return heritage;
    }

    public int weight() {
        return weight;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "heritage." + WizardsAndBeastsMod.MODID + ".practice." + id();
    }
}
