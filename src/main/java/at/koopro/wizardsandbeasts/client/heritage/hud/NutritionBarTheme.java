package at.koopro.wizardsandbeasts.client.heritage.hud;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.nutrition.NutritionPolicy;
import at.koopro.wizardsandbeasts.heritage.vampire.ThirstStage;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Which sprites fill the hunger bar's slot.
 *
 * <p>Exists so the slot is a <em>slot</em> rather than a vampire feature. {@link BloodBarRenderer} knows
 * how to run vanilla's three-sprite icon loop in the place the drumsticks were; this decides which three
 * sprites it runs with. A second heritage that replaces nutrition — a construct with a charge meter, a
 * merperson with a moisture meter — is a case added to {@link #of} and an art file, and no change at all
 * to the renderer.
 *
 * <p><b>Two sets, not four.</b> There is one variant per thirst band's worth of art only where vanilla
 * has one: plain, and a "something is wrong with you" set, mirroring {@code food_full}/
 * {@code food_full_hunger}. Giving each of the four {@link ThirstStage}s its own drop would tie every
 * threshold change in {@code VampireBloodConfig} to an art change, and the bands are already legible
 * from how much of the bar is left.
 *
 * @param empty the socket, drawn for all ten icons
 * @param half  the odd-value icon
 * @param full  a filled icon, drawn over the socket
 */
@NullMarked
public record NutritionBarTheme(Identifier empty, Identifier half, Identifier full) {

    /** Fresh, arterial. Every band but the last. */
    private static final NutritionBarTheme BLOOD_FRESH = sprites("blood_empty", "blood_half", "blood_full");

    /** Dried and dark. {@link ThirstStage#STARVING} only — vanilla's {@code _hunger} variant, in kind. */
    private static final NutritionBarTheme BLOOD_WITHERED =
            sprites("blood_empty_withered", "blood_half_withered", "blood_full_withered");

    /**
     * The sprite set for a policy and band, or {@code null} when this policy does not own the slot.
     *
     * <p>{@link NutritionPolicy#NONE} returns null on purpose: a heritage that is simply indifferent to
     * food should get an empty corner, not a bar reading "not applicable" forever.
     */
    @Nullable
    public static NutritionBarTheme of(NutritionPolicy policy, ThirstStage stage) {
        return switch (policy) {
            case BLOOD -> stage == ThirstStage.STARVING ? BLOOD_WITHERED : BLOOD_FRESH;
            case VANILLA, NONE -> null;
        };
    }

    /**
     * Sprite ids, not texture paths.
     *
     * <p>These resolve on the {@code minecraft:gui} atlas, which vanilla builds with a
     * {@code minecraft:directory} source over {@code textures/gui/sprites} — and that lister walks every
     * namespace, so a file this mod ships under {@code assets/wizards_and_beasts/textures/gui/sprites/hud}
     * is addressable as {@code wizards_and_beasts:hud/…} with no atlas file of our own. Same mechanism
     * vanilla's own HUD icons use, which is the point: the bar is drawn by the same call vanilla draws
     * drumsticks with.
     */
    private static NutritionBarTheme sprites(String empty, String half, String full) {
        return new NutritionBarTheme(sprite(empty), sprite(half), sprite(full));
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "hud/" + name);
    }
}
