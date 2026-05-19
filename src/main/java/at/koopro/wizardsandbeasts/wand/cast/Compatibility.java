package at.koopro.wizardsandbeasts.wand.cast;

import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.wand.stat.WandCore;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.wand.stat.WandLength;
import at.koopro.wizardsandbeasts.wand.stat.WandWood;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import static at.koopro.wizardsandbeasts.registry.ModDataComponents.WAND_CORE;
import static at.koopro.wizardsandbeasts.registry.ModDataComponents.WAND_FLEXIBILITY;
import static at.koopro.wizardsandbeasts.registry.ModDataComponents.WAND_LENGTH;
import static at.koopro.wizardsandbeasts.registry.ModDataComponents.WAND_WOOD;

public final class Compatibility {
    private static final float BIND_THRESHOLD = 0.5f;
    private static final float BOND_CONTRIBUTION = 0.3f;

    private Compatibility() {}

    public record Score(float initialCompat, float totalCompat) {}

    public static Score score(ItemStack wandStack, PlayerHeritageData typeData, WandAllegiance allegiance) {
        HeritageVariant subtype = typeData.getSelectedHeritageVariant();
        WandCore core = resolveCore(wandStack);
        WandWood wood = resolveWood(wandStack);
        WandFlexibility flex = resolveFlexibility(wandStack);
        WandLength length = resolveLength(wandStack);

        if (subtype == null || core == null || wood == null || flex == null || length == null) {
            return new Score(0.5f, 0.5f);
        }

        float subtypeAffinity = subtypeAffinity(core, wood, subtype);
        float flexibility = flexibilityMatch(flex, subtype);
        float lengthMatch = lengthMatch(length, subtype);
        float fate = fate(core, wood, flex, length, subtype);

        float initial = clamp(subtypeAffinity * 0.6f + flexibility * 0.2f + lengthMatch * 0.1f + fate);
        float total = clamp(initial + allegiance.bondStrength() * BOND_CONTRIBUTION);
        return new Score(initial, total);
    }

    public static boolean canBind(float initialCompat) {
        return initialCompat >= BIND_THRESHOLD;
    }

    private static float subtypeAffinity(WandCore core, WandWood wood, HeritageVariant subtype) {
        float score = 0.5f;
        if (subtype.hasTag("obscurus_form")) {
            if (core == WandCore.THESTRAL_TAIL || core == WandCore.DRAGON_HEARTSTRING) score += 0.2f;
            if (wood == WandWood.YEW || wood == WandWood.ELDER) score += 0.1f;
        }
        boolean veelaCharm = subtype.getParentHeritage() == Heritage.VEELA
                && (subtype.hasTag("enhanced_bond") || subtype.hasTag("transformation"));
        if (veelaCharm) {
            if (core == WandCore.VEELA_HAIR || core == WandCore.UNICORN_HAIR) score += 0.2f;
            if (wood == WandWood.HOLLY) score += 0.05f;
        }
        if (subtype.hasTag("nature_speech")) {
            if (wood == WandWood.ROWAN) score += 0.2f;
            if (core == WandCore.UNICORN_HAIR) score += 0.1f;
        }
        if (subtype.hasTag("dark_resistance")) {
            if (core == WandCore.PHOENIX_FEATHER) score += 0.1f;
            if (wood == WandWood.ELDER) score -= 0.05f;
        }
        return clamp(score);
    }

    private static float flexibilityMatch(WandFlexibility flexibility, HeritageVariant subtype) {
        float score = 0.5f;
        boolean rigidLean = subtype.hasTag("moon_sensitive")
                || "warrior".equals(subtype.getId())
                || (subtype.hasTag("obscurus_form") && subtype.hasTag("transformation"));
        if (rigidLean) {
            if (flexibility == WandFlexibility.UNYIELDING || flexibility == WandFlexibility.RIGID) score += 0.2f;
        } else {
            if (flexibility == WandFlexibility.SUPPLE || flexibility == WandFlexibility.SPRINGY) score += 0.2f;
        }
        return clamp(score);
    }

    private static float lengthMatch(WandLength length, HeritageVariant subtype) {
        float score = 0.5f;
        if ("full_giant".equals(subtype.getId()) || "war".equals(subtype.getId())) {
            if (length == WandLength.LONG) score += 0.2f;
        } else if (subtype.hasTag("enhanced_bond") || subtype.hasTag("divination_sight")
                || subtype.hasTag("rune_affinity")) {
            if (length == WandLength.MEDIUM || length == WandLength.STANDARD) score += 0.15f;
        } else if (length == WandLength.STANDARD) {
            score += 0.1f;
        }
        return clamp(score);
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float fate(WandCore core, WandWood wood, WandFlexibility flex, WandLength length, HeritageVariant subtype) {
        int hash = 17;
        hash = 31 * hash + core.ordinal();
        hash = 31 * hash + wood.ordinal();
        hash = 31 * hash + flex.ordinal();
        hash = 31 * hash + length.ordinal();
        hash = 31 * hash + subtype.ordinal();
        int normalized = Math.floorMod(hash, 1000);
        return (normalized / 1000.0f) * 0.1f;
    }

    private static WandCore resolveCore(ItemStack wandStack) {
        WandCore legacy = wandStack.get(WAND_CORE.get());
        if (legacy != null) {
            return legacy;
        }
        Identifier id = WandComponents.getCore(wandStack);
        return id == null ? null : WandCore.byName(id.getPath());
    }

    private static WandWood resolveWood(ItemStack wandStack) {
        WandWood legacy = wandStack.get(WAND_WOOD.get());
        if (legacy != null) {
            return legacy;
        }
        Identifier id = WandComponents.getWood(wandStack);
        return id == null ? null : WandWood.byName(id.getPath());
    }

    private static WandFlexibility resolveFlexibility(ItemStack wandStack) {
        WandFlexibility legacy = wandStack.get(WAND_FLEXIBILITY.get());
        if (legacy != null) {
            return legacy;
        }
        return WandComponents.getFlexibility(wandStack);
    }

    private static WandLength resolveLength(ItemStack wandStack) {
        WandLength legacy = wandStack.get(WAND_LENGTH.get());
        if (legacy != null) {
            return legacy;
        }
        Float inches = WandComponents.getLength(wandStack);
        if (inches == null) {
            return null;
        }
        if (inches <= 10.0f) return WandLength.SHORT;
        if (inches <= 12.0f) return WandLength.MEDIUM;
        if (inches >= 14.0f) return WandLength.LONG;
        return WandLength.STANDARD;
    }
}
