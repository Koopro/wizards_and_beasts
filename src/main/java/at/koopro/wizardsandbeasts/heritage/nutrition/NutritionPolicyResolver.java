package at.koopro.wizardsandbeasts.heritage.nutrition;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Works out which {@link NutritionPolicy} a body is under.
 *
 * <p>Keyed on the <b>variant tag</b>, not on the heritage enum. {@code blood_hunger} was already declared
 * on all three vampire lineages and read by nothing; making it the source of truth means a datapack
 * lineage, a future half-vampire, or a cursed wizard who acquires the tag all get the blood economy
 * without this class learning their names. {@code Heritage.VAMPIRE} is never mentioned here on purpose —
 * the day something other than a vampire hungers for blood, that should be a tag edit and not a patch.
 *
 * <p>Every overload funnels into {@link #resolve(Heritage, HeritageVariant)}, which is pure and side-free
 * so both sides of the network can use it: the server reads the attachment, the client reads
 * {@code ClientHeritageDataState}, and neither can drift from the other because the decision is the same
 * three lines.
 */
@NullMarked
public final class NutritionPolicyResolver {

    /**
     * The variant tag that means "this body drinks blood".
     *
     * <p>Shared with {@code VampireHeritageHandler.TAG_SUNLIGHT_WEAKNESS}'s sibling constants by
     * convention rather than by a common enum: the tag vocabulary lives in {@link HeritageVariant} as
     * plain strings, and inventing a registry for four of them would cost more than it saved.
     */
    public static final String TAG_BLOOD_HUNGER = "blood_hunger";

    private NutritionPolicyResolver() {}

    /**
     * The one real decision. Both arguments may be null — a player who has not passed the heritage gate
     * yet is an ordinary hungry human, which is also what they look like on screen.
     */
    public static NutritionPolicy resolve(@Nullable Heritage heritage, @Nullable HeritageVariant variant) {
        if (variant != null && variant.hasTag(TAG_BLOOD_HUNGER)) {
            return NutritionPolicy.BLOOD;
        }
        return NutritionPolicy.VANILLA;
    }

    /** Resolves from a heritage block, whichever side holds it. */
    public static NutritionPolicy resolve(PlayerHeritageData data) {
        return resolve(data.getSelectedHeritage(), data.getSelectedHeritageVariant());
    }

    /** Server-side convenience. Reads the attachment, which only the server is authoritative for. */
    public static NutritionPolicy resolve(ServerPlayer player) {
        return resolve(player.getData(ModAttachments.HERITAGE_DATA.get()));
    }

    /** Shorthand for the commonest question asked of this class. */
    public static boolean drinksBlood(ServerPlayer player) {
        return resolve(player) == NutritionPolicy.BLOOD;
    }
}
