package at.koopro.wizardsandbeasts.shadow;

import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellFamilies;
import at.koopro.wizardsandbeasts.spell.core.SpellFamily;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Turning a spell's <em>family</em> into a damage source.
 *
 * <p>Spell damage went out as plain {@code magic} everywhere, which meant nothing downstream could
 * tell a Lumos-flavoured blast from a Confringo one — and a mechanic like Shadow Form's light
 * vulnerability has nowhere to attach. This is the one seam that fixes that: the family is already
 * known at every damage site, it just was not being carried through.
 *
 * <p><b>Only light is special-cased, on purpose.</b> Giving each of the seven families its own damage
 * type would be seven registrations and seven tags in service of one mechanic. The day a fire
 * vulnerability wants the same treatment, it adds a branch here and nowhere else.
 *
 * <p>Data-driven spells have a second, older route to the same place: a {@code damage} effect
 * component may name any {@code damageType} in its JSON, so a pack can mark a spell light-based
 * without it being in the LIGHT family at all.
 */
@NullMarked
public final class SpellDamageSources {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SpellDamageSources() {}

    /**
     * The damage source {@code spell} should hurt with.
     *
     * <p>Falls back to plain magic for every family but light, and also whenever the light-magic
     * damage type is missing from the loaded datapacks — a spell that fails to hurt anybody because
     * a registry lookup threw would be a far worse bug than one that hurts them generically.
     */
    public static DamageSource forSpell(ServerLevel level, @Nullable Entity caster, @Nullable Spell spell) {
        DamageSource magic = level.damageSources().magic();
        if (spell == null || SpellFamilies.of(spell) != SpellFamily.LIGHT) {
            return magic;
        }
        try {
            Holder<DamageType> holder = level.registryAccess()
                    .lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(ShadowForm.LIGHT_MAGIC);
            return new DamageSource(holder, caster);
        } catch (RuntimeException ex) {
            LOGGER.warn("[WizardsAndBeasts] light_magic damage type missing; falling back to magic", ex);
            return magic;
        }
    }
}
