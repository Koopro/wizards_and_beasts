package at.koopro.wizardsandbeasts.spell.patronus;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public final class PatronusFormDeterminer {

    // TODO(patronus-rare): populate with canonical rare Patronus forms per heritage once designs are settled
    private static final Map<Heritage, Identifier> RARE_FORMS = new EnumMap<>(Heritage.class);

    private PatronusFormDeterminer() {}

    public static @Nullable Identifier determine(@Nullable Heritage heritage, @Nullable HeritageVariant variant, float happiness) {
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
        return mc("cow"); // TODO(scope): replace with custom Patronus deer model
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
