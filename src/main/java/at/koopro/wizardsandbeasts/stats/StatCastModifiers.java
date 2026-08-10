package at.koopro.wizardsandbeasts.stats;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.spell.cast.ModifierStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * The cast-pipeline read for {@link PlayerStat}. Three of the five stats change how a spell comes
 * out of the wand:
 *
 * <ul>
 *   <li>{@link PlayerStat#POWER} — damage. Rolled once at the heritage ceremony and capped by the
 *       species band, so this is the "how strong were you born" axis.</li>
 *   <li>{@link PlayerStat#PRECISION} — fewer misfires.</li>
 *   <li>{@link PlayerStat#REFLEXES} — shorter cooldowns.</li>
 * </ul>
 *
 * <p>{@link PlayerStat#WILLPOWER} is not here: it governs resisting magic rather than casting it,
 * and is read at the resist rolls in {@code ImperioServerLogic} and {@code LegilimencyServerLogic}.
 * {@link PlayerStat#KNOWLEDGE} is derived and purely informational.
 *
 * <p>POWER goes through {@code multiplyDamage} rather than {@code multiplyPower} because
 * {@code ModifierStack.finalPower()} has no consumers anywhere in the mod — writing to it would look
 * wired and do nothing.
 *
 * <p>Called from {@code SpellExecutor.executeGeneric} alongside the other modifier sources, and
 * necessarily <em>before</em> the misfire roll so the PRECISION delta is in the total that is diced.
 */
public final class StatCastModifiers {

    private StatCastModifiers() {}

    public static void applyCastModifiers(ModifierStack stack, ServerPlayer caster) {
        if (!ModuleManager.isEnabled(Module.PLAYER_STATS)) return;

        stack.multiplyDamage(
                StatEffects.damageMultiplier(PlayerStatsAPI.getStat(caster, PlayerStat.POWER)),
                "player_stat_power");
        stack.addMisfireChance(
                StatEffects.misfireDelta(PlayerStatsAPI.getStat(caster, PlayerStat.PRECISION)),
                "player_stat_precision");
        stack.multiplyCooldown(
                StatEffects.cooldownMultiplier(PlayerStatsAPI.getStat(caster, PlayerStat.REFLEXES)),
                "player_stat_reflexes");
    }
}
