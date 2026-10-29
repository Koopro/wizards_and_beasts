package at.koopro.wizardsandbeasts.journey;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellPractice;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * The path's words, built the same way on both sides. Keys are named through {@link Component#keybind}, so a hint
 * shows the key the player actually bound, not the default.
 */
@NullMarked
public final class PathText {

    private PathText() {}

    public static Component title(PathStep step) {
        return Component.translatable(step.titleKey());
    }

    public static Component hint(PathStep step) {
        return switch (step) {
            case FIRST_LIGHT -> Component.translatable(step.hintKey(), key("spell_wheel"),
                    Component.keybind("key.use"));
            case OWN_SPELL -> Component.translatable(step.hintKey(), key("skill_menu"));
            case PRACTICE -> Component.translatable(step.hintKey(), Proficiency.PROFICIENT.getCastsRequired(),
                    SpellPractice.DAILY_PRACTICE);
            case FIELD_STUDY -> Component.translatable(step.hintKey(), key("character_sheet"));
            default -> Component.translatable(step.hintKey());
        };
    }

    /** "Paid: the Ministry Handbook, the bench … in your recipe book." Only the parts actually paid are named. */
    public static Component paid(List<PathReward> parts) {
        MutableComponent list = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                list.append(Component.translatable("journey.wizards_and_beasts.reward.separator"));
            }
            list.append(Component.translatable(parts.get(i).translationKey()));
        }
        return Component.translatable("journey.wizards_and_beasts.reward.paid", list);
    }

    private static Component key(String name) {
        return Component.keybind("key." + WizardsAndBeastsMod.MODID + "." + name);
    }
}
