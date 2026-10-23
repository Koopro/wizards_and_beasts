package at.koopro.wizardsandbeasts.brew;

import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * How well a bottle was brewed: a percentage stored on the bottle ({@link ModDataComponents#BREW_QUALITY}) and
 * applied to every duration the brew gives when drunk. Amplifiers are never touched.
 *
 * <p>User decision (2026-10-22): quality is the <em>brewer's</em>, set when the brew starts and kept by the bottle,
 * so a Felix brewed by a master is worth more than one brewed by a first-year and it stays that way in a chest, in
 * a trade, in somebody else's hand. Before this the drinker's skill scaled every potion, which made who brewed it
 * irrelevant.
 *
 * <p>The band is {@value #MIN}..{@value #MAX}%: an untrained brewer's work is a little thin, {@code potion_potency}
 * adds {@value #PER_POTENCY_LEVEL} points a level (three levels count), and the Potions O.W.L. adds up to
 * {@value #GRADE_O} more for an Outstanding. A bottle without the component -- bought, looted, from an old save --
 * is plain {@value #PLAIN}%.
 */
@NullMarked
public final class BrewQuality {

    public static final int MIN = 85;
    public static final int MAX = 130;
    public static final int PLAIN = 100;
    public static final int PER_POTENCY_LEVEL = 10;
    public static final int MAX_POTENCY_LEVELS = 3;
    public static final int GRADE_O = 15;

    private BrewQuality() {}

    /** Pure: the quality a brewer with this skill level and Potions O.W.L. grade (null: none) puts in a bottle. */
    public static int percent(int potencyLevel, @Nullable OWLGrade potionsGrade) {
        int levels = Math.min(Math.max(potencyLevel, 0), MAX_POTENCY_LEVELS);
        int grade = potionsGrade == null ? 0 : switch (potionsGrade) {
            case O -> GRADE_O;
            case E -> 10;
            case A -> 5;
            case P, D, T -> 0;
        };
        return clamp(MIN + PER_POTENCY_LEVEL * levels + grade);
    }

    /** The quality this brewer puts in a bottle now. */
    public static int of(ServerPlayer brewer) {
        return percent(SkillSystemAPI.getSkillData(brewer).getSkillLevel(BrewPotency.SKILL_ID),
                brewer.getData(ModAttachments.OWL_DATA.get()).grades().get(OWLSubject.POTIONS));
    }

    /** The bottle's quality, {@value #PLAIN} when it carries none. */
    public static int of(ItemStack bottle) {
        Integer stored = bottle.get(ModDataComponents.BREW_QUALITY.get());
        return stored == null ? PLAIN : clamp(stored);
    }

    /** The duration multiplier a drink of this bottle gets. */
    public static float multiplier(ItemStack bottle) {
        return of(bottle) / 100f;
    }

    /** Writes {@code percent} onto the bottle and returns it. */
    public static ItemStack stamp(ItemStack bottle, int percent) {
        bottle.set(ModDataComponents.BREW_QUALITY.get(), clamp(percent));
        return bottle;
    }

    private static int clamp(int percent) {
        return Math.min(MAX, Math.max(MIN, percent));
    }
}
