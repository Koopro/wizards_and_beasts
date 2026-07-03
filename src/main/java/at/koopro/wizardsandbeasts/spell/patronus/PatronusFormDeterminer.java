package at.koopro.wizardsandbeasts.spell.patronus;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public final class PatronusFormDeterminer {

    /** Happiness at/above which a heritage's rare Patronus form is used instead of the common one. */
    private static final float RARE_FORM_THRESHOLD = 90.0f;

    /**
     * Rare Patronus forms, earned at {@link #RARE_FORM_THRESHOLD}+ happiness. Vanilla stand-in
     * models, same pipeline as the common forms; heritages absent here fall through to common.
     */
    private static final Map<Heritage, Identifier> RARE_FORMS = new EnumMap<>(Heritage.class);

    static {
        RARE_FORMS.put(Heritage.WIZARDKIND, mc("horse"));     // noble steed — stag stand-in
        RARE_FORMS.put(Heritage.WEREWOLF, mc("polar_bear"));  // the wolf mastered, not denied
        RARE_FORMS.put(Heritage.OBSCURIAL, mc("phantom"));    // the shadow given wings
        RARE_FORMS.put(Heritage.VAMPIRE, mc("cat"));          // a warm-blooded companion
        RARE_FORMS.put(Heritage.VEELA, mc("allay"));          // radiant spirit of the dance
    }

    private PatronusFormDeterminer() {}

    public static @Nullable Identifier determine(@Nullable Heritage heritage, @Nullable HeritageVariant variant, float happiness) {
        if (heritage != null && happiness >= RARE_FORM_THRESHOLD) {
            Identifier rare = RARE_FORMS.get(heritage);
            if (rare != null) {
                return rare;
            }
        }
        if (heritage == null) {
            return wizardkindForm(null);
        }
        return switch (heritage) {
            case WIZARDKIND -> wizardkindForm(variant);
            case WEREWOLF   -> mc("wolf");
            case OBSCURIAL  -> mc("bat");
            case VAMPIRE    -> vampireForm(variant);
            case VEELA      -> mc("parrot");
            default         -> null;
        };
    }

    private static Identifier wizardkindForm(@Nullable HeritageVariant variant) {
        if (variant == HeritageVariant.PURE_BLOOD) {
            return mc("wolf");
        }
        if (variant == HeritageVariant.HALF_BLOOD) {
            return mc("fox");
        }
        if (variant == HeritageVariant.MUGGLE_BORN) {
            return mc("rabbit");
        }
        return mc("goat"); // closest vanilla stand-in for the classic deer until a custom model ships
    }

    private static Identifier vampireForm(@Nullable HeritageVariant variant) {
        if (variant == HeritageVariant.VAMPIRE_BORN) {
            return mc("phantom");
        }
        return mc("bat");
    }

    private static Identifier mc(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }
}
