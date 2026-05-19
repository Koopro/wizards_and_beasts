package at.koopro.wizardsandbeasts.spell.patronus;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import org.jspecify.annotations.Nullable;

/**
 * Maps heritage to a vanilla entity model id string for Patronus rendering.
 */
public final class PatronusFormDeterminer {

    private PatronusFormDeterminer() {}

    public static String determine(@Nullable Heritage heritage, @Nullable HeritageVariant variant, float happiness) {
        if (heritage == null) {
            return raritySuffix("minecraft:cow", happiness);
        }
        String base = switch (heritage) {
            case WIZARDKIND -> wizardkindForm(variant);
            case WEREWOLF -> "minecraft:wolf";
            case OBSCURIAL -> "minecraft:bat";
            case VAMPIRE -> vampireForm(variant);
            case VEELA -> "minecraft:parrot";
            case CENTAUR -> "minecraft:horse";
            case MERPEOPLE -> "minecraft:salmon";
            case GIANT -> giantForm(variant);
            case GOBLIN -> "minecraft:silverfish";
            case HOUSE_ELF -> "minecraft:cat";
            default -> "minecraft:cow"; // TODO(scope): replace with custom Patronus deer model
        };
        return raritySuffix(base, happiness);
    }

    private static String wizardkindForm(@Nullable HeritageVariant variant) {
        if (variant == HeritageVariant.PURE_BLOOD) {
            return "minecraft:wolf";
        }
        if (variant == HeritageVariant.HALF_BLOOD) {
            return "minecraft:fox";
        }
        if (variant == HeritageVariant.MUGGLE_BORN) {
            return "minecraft:rabbit";
        }
        return "minecraft:cow";
    }

    private static String vampireForm(@Nullable HeritageVariant variant) {
        if (variant == HeritageVariant.VAMPIRE_BORN) {
            return "minecraft:phantom";
        }
        return "minecraft:bat";
    }

    private static String giantForm(@Nullable HeritageVariant variant) {
        if (variant == HeritageVariant.GIANT_FULL) {
            return "minecraft:polar_bear";
        }
        return "minecraft:cow";
    }

    private static String raritySuffix(String id, float happiness) {
        if (happiness >= 90.0f) {
            return id + "_rare";
        }
        return id;
    }
}
