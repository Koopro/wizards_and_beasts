package at.koopro.wizardsandbeasts.skill.vocation;

import at.koopro.wizardsandbeasts.spell.cast.ModifierStack;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Consumers for {@link VocationDefinition#grantedAbilities()} — the descriptive commitment flags that have
 * no {@code SkillNodeEffect} mapping. Each flag is read via {@link VocationHelper#hasGrantedAbility}:
 * <ul>
 *   <li>{@code duelist_spell_power} — +10% spell damage.</li>
 *   <li>{@code duelist_cast_speed} — -10% spell cooldown.</li>
 *   <li>{@code curse_power} — +15% damage on Dark Arts spells.</li>
 *   <li>{@code dark_corruption_accrual} — corruption flows 25% faster (the cost of embracing it).</li>
 *   <li>{@code crop_yield} — +10% harvest double-drop chance (HerbologyAbilityHandler).</li>
 *   <li>{@code beast_capacity} — +10% beast damage resistance (MagizoologyAbilityHandler).</li>
 *   <li>{@code mount_loyalty} — mounts you ride take 25% less damage (MagizoologyAbilityHandler).</li>
 * </ul>
 */
@NullMarked
public final class VocationAbilityHooks {

    private static final float SPELL_POWER_MULT = 1.10f;
    private static final float CAST_SPEED_MULT = 0.90f;
    private static final float CURSE_POWER_MULT = 1.15f;
    private static final float CORRUPTION_ACCRUAL_MULT = 1.25f;
    public static final float CROP_YIELD_BONUS = 0.10f;
    public static final float BEAST_RESISTANCE_BONUS = 0.10f;
    public static final float MOUNT_DAMAGE_MULT = 0.75f;

    private VocationAbilityHooks() {}

    /** Duelist / Dark Arts cast bonuses; called from SpellExecutor alongside the skill modifiers. */
    public static void applyCastModifiers(ModifierStack modifiers, ServerPlayer caster, Spell spell) {
        if (VocationHelper.hasGrantedAbility(caster, "duelist_spell_power")) {
            modifiers.multiplyDamage(SPELL_POWER_MULT, "vocation_duelist_spell_power");
        }
        if (VocationHelper.hasGrantedAbility(caster, "duelist_cast_speed")) {
            modifiers.multiplyCooldown(CAST_SPEED_MULT, "vocation_duelist_cast_speed");
        }
        if (spell.getCategory() == SpellCategory.DARK_ARTS
                && VocationHelper.hasGrantedAbility(caster, "curse_power")) {
            modifiers.multiplyDamage(CURSE_POWER_MULT, "vocation_curse_power");
        }
    }

    /** Scales dark-corruption gains for committed Dark Arts vocations. Apply at every accrual site. */
    public static float scaleCorruptionGain(ServerPlayer player, float gain) {
        return VocationHelper.hasGrantedAbility(player, "dark_corruption_accrual")
                ? gain * CORRUPTION_ACCRUAL_MULT
                : gain;
    }
}
