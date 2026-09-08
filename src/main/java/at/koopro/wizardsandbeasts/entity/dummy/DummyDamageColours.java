package at.koopro.wizardsandbeasts.entity.dummy;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import org.jspecify.annotations.NullMarked;

import at.koopro.wizardsandbeasts.apparition.splinch.SplinchDamageTypes;
import at.koopro.wizardsandbeasts.azkaban.AzkabanDamageTypes;
import at.koopro.wizardsandbeasts.shadow.ShadowForm;

/**
 * The colour a damage number is drawn in, picked from the damage type on the server.
 *
 * <p>Resolved server-side so the payload carries a finished colour: a colour is three bytes, and a
 * client that mapped types itself would be a second copy of this table to keep in step.
 *
 * <p>Order matters. The mod's own sources are tested before the vanilla families they belong to, so
 * a Dementor's kiss reads as a kiss rather than as generic magic. Only sources this mod actually
 * emits get an entry — a table of colours for damage nothing produces is a table nobody can check.
 */
@NullMarked
public final class DummyDamageColours {

    private static final int GENERIC = 0xFFFFFF;
    private static final int SPELL = 0x9B6BFF;
    private static final int LIGHT_SPELL = 0xFFF3B0;
    private static final int DEMENTOR = 0x3C4A5A;
    private static final int SPLINCH = 0x910038;
    private static final int FIRE = 0xFF7700;
    private static final int COLD = 0x09D2FF;
    private static final int EXPLOSION = 0xFFBB29;
    private static final int LIGHTNING = 0xFFF200;
    private static final int DROWNING = 0x1898E3;
    private static final int WITHER = 0x666666;
    private static final int THORNS = 0x0FA209;
    private static final int HEAL = 0x4CE05A;

    private DummyDamageColours() {}

    /** Colour for healing numbers, which carry no damage source of their own. */
    public static int healing() {
        return HEAL;
    }

    public static int of(DamageSource source) {
        if (ShadowForm.isLightMagic(source)) {
            return LIGHT_SPELL;
        }
        if (source.is(AzkabanDamageTypes.DEMENTOR_KISS) || source.is(AzkabanDamageTypes.DEMENTOR_DISSIPATE)) {
            return DEMENTOR;
        }
        if (source.is(SplinchDamageTypes.SPLINCH)) {
            return SPLINCH;
        }
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
            return SPELL;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return FIRE;
        }
        if (source.is(DamageTypeTags.IS_FREEZING)) {
            return COLD;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return EXPLOSION;
        }
        if (source.is(DamageTypeTags.IS_LIGHTNING)) {
            return LIGHTNING;
        }
        if (source.is(DamageTypeTags.IS_DROWNING)) {
            return DROWNING;
        }
        if (source.is(DamageTypes.WITHER) || source.is(DamageTypes.WITHER_SKULL)) {
            return WITHER;
        }
        if (source.is(DamageTypes.THORNS) || source.is(DamageTypes.CACTUS)) {
            return THORNS;
        }
        return GENERIC;
    }
}
