package at.koopro.wizardsandbeasts.spell.protego;

import at.koopro.wizardsandbeasts.brew.silver.SilveredWeapons;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.SpellFamilies;
import at.koopro.wizardsandbeasts.spell.core.SpellFamily;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * What counts as Dark magic for the Shield Charm — the one question Protego Horribilis exists to
 * answer differently from every lesser ward.
 *
 * <h2>Why this is wider than the spell family</h2>
 * Horribilis' clause used to read "family {@code DARK} spell projectile", and in the game as it
 * stands that is almost unreachable: of the spells {@code SpellFamilies} calls Dark, Avada Kedavra
 * is unblockable by canon, Crucio and Imperio are beams, Nox is a light switch, and the Obscurus
 * pair are self-cast. There was no Dark <em>bolt</em> for the top tier to swallow, so its defining
 * behaviour almost never fired.
 *
 * <p>Two widenings, both chosen so the rule still reads as "Dark magic" to a player:
 * <ul>
 *   <li>a spell counts if its family is Dark <em>or</em> its category is the Dark Arts — a curse is
 *       a curse whether or not anybody set a particle family on it;</li>
 *   <li>a blow counts if it came from something in the mod's hand-authored
 *       {@code wizards_and_beasts:dark_creatures} tag — dementors, the obscurus, the undead. That
 *       is the reachable half: warding a graveyard with Horribilis now means something.</li>
 * </ul>
 */
public final class ProtegoDarkThreats {

    private ProtegoDarkThreats() {}

    /** Whether this spell is the sort Horribilis was built against. */
    public static boolean isDarkSpell(@Nullable Spell spell) {
        if (spell == null) {
            return false;
        }
        return SpellFamilies.of(spell) == SpellFamily.DARK || spell.getCategory() == SpellCategory.DARK_ARTS;
    }

    /**
     * Whether this blow is Dark in origin — a dementor's chill, an inferius' hands, a wither skull.
     *
     * <p>Reads the attacker, not the damage type: "dark" is a judgement about the fiction, and the
     * tag that already encodes that judgement for silvered weapons is the same list a shield charm
     * should be reacting to.
     */
    public static boolean isDarkDamage(DamageSource source) {
        return isDarkEntity(source.getEntity()) || isDarkEntity(source.getDirectEntity());
    }

    private static boolean isDarkEntity(@Nullable Entity entity) {
        return entity != null && entity.getType().is(SilveredWeapons.DARK_CREATURES);
    }
}
