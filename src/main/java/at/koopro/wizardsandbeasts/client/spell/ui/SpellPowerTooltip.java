package at.koopro.wizardsandbeasts.client.spell.ui;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.client.skill.state.ClientSkillBonusCache;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.spell.cast.SpellPower;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.proficiency.ProficiencyScaler;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * What a spell will actually hit for, as lines a screen can draw.
 *
 * <h2>Why the client is allowed to claim this</h2>
 * <p>The number is computed by {@link SpellPower} — the same pure class the server composes a real
 * cast with — from data the server has already synced: per-spell proficiency
 * ({@code ClientSpellDataState}) and the skill web's category multipliers
 * ({@code ClientSkillBonusCache}). Same function, same inputs, same answer. A tooltip that
 * recomputed the formula in its own way would drift the first time either side was retuned.
 *
 * <h2>What it deliberately does not claim</h2>
 * <p>The situational channel — wand corruption, allegiance, dark corruption, vocation, Niffler
 * happiness, player stats — is resolved server-side at cast time and the client has no honest way to
 * know it. Rather than guess, the panel says so on its last line. A scoped claim the player can
 * trust beats a total that is quietly wrong.
 *
 * <p>The per-spell half of the skill web is likewise absent: {@code PlayerSkillBonusData}, which is
 * what gets synced, carries only per-category maps. The lines therefore under-report rather than
 * over-report, which is the right direction for a number a player plans around.
 */
@NullMarked
public final class SpellPowerTooltip {

    private SpellPowerTooltip() {}

    /**
     * Zero to five lines describing the caster's effective damage multiplier for {@code spell}.
     *
     * <p>Empty when the tooltip is switched off in config, or when the spell deals no damage and the
     * whole notion does not apply to it.
     */
    public static List<Component> lines(Spell spell) {
        if (!Config.showSpellPowerInTooltip || spell.getBaseDamage() <= 0) {
            return List.of();
        }

        float proficiency = proficiencyMultiplier(spell);
        float skill = ClientSkillBonusCache.get().damageMultipliers()
                .getOrDefault(spell.getCategory(), 1.0f);
        SpellPower.Breakdown power = SpellPower.damage(1.0f, proficiency, skill);

        List<Component> out = new ArrayList<>(5);
        out.add(Component.translatable("tooltip.wizards_and_beasts.spell_power.header"));
        out.add(Component.translatable("tooltip.wizards_and_beasts.spell_power.total", signed(power.total())));
        if (Math.abs(proficiency - 1.0f) > 1.0e-3f) {
            out.add(Component.translatable("tooltip.wizards_and_beasts.spell_power.proficiency", signed(proficiency)));
        }
        if (Math.abs(skill - 1.0f) > 1.0e-3f) {
            out.add(Component.translatable("tooltip.wizards_and_beasts.spell_power.skill", signed(skill)));
        }
        if (power.clamped()) {
            out.add(Component.translatable("tooltip.wizards_and_beasts.spell_power.capped"));
        }
        out.add(Component.translatable("tooltip.wizards_and_beasts.spell_power.excludes"));
        return out;
    }

    /**
     * The proficiency curve for this spell, or a flat {@code 1.0} when the module is off.
     *
     * <p>Mirrors {@code ProficiencyScaler.getProfileForPlayer}, which cannot be reused directly
     * because it takes a {@code ServerPlayer}. It reads the same synced float and calls the same
     * {@code computeProfile}, so the curve itself is shared rather than restated.
     */
    private static float proficiencyMultiplier(Spell spell) {
        if (!at.koopro.wizardsandbeasts.module.ModuleManager.isEnabled(
                at.koopro.wizardsandbeasts.module.Module.PROFICIENCY)) {
            return 1.0f;
        }
        float proficiency = ClientSpellDataState.get().getSpellProficiency(spell.getId());
        return ProficiencyScaler.computeProfile(proficiency).damageMult();
    }

    /** {@code 1.25f} renders as {@code +25%}, {@code 0.65f} as {@code -35%}. */
    private static String signed(float multiplier) {
        int percent = Math.round((multiplier - 1.0f) * 100.0f);
        return (percent >= 0 ? "+" : "") + percent + "%";
    }
}
