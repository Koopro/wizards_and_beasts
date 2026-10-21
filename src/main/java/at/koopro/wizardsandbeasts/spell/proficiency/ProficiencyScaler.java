package at.koopro.wizardsandbeasts.spell.proficiency;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Turns a player's per-spell proficiency (stored on {@link PlayerSpellData}, kept honest by
 * {@link SpellPractice}) into the {@link SpellScalingProfile} a cast multiplies its damage, cooldown,
 * duration, control and accuracy by.
 */
public final class ProficiencyScaler {
    private ProficiencyScaler() {}

    /**
     * The proficiency key for a spell id. Spell ids are already {@code namespace:path}; an id that does not
     * parse falls back to this mod's namespace. Every cast path resolves the key here so they cannot drift.
     */
    public static Identifier spellKey(String spellId) {
        Identifier parsed = Identifier.tryParse(spellId);
        return parsed != null ? parsed : Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, spellId);
    }

    public static SpellScalingProfile computeProfile(float proficiency) {
        float p = clamp01(proficiency);

        // Damage: smoothstep S-curve. Floor raised so a freshly-learned spell no longer lands at half
        // power (felt limp); mastered ceiling (1.50x) is unchanged.
        float damageT = p * p * (3.0f - 2.0f * p);
        float damageMult = 0.65f + 0.85f * damageT;

        // Cooldown: power curve with stronger early rewards.
        float cooldownT = (float) Math.pow(p, 0.7f);
        float cooldownMult = 1.40f - (cooldownT * 0.70f);

        // Duration: linear. Floor raised (0.60->0.75) so fresh-spell effect durations aren't clipped short;
        // mastered ceiling (1.40x) unchanged.
        float durationMult = 0.75f + (p * 0.65f);

        // Control: linear floor->ceiling. Floor raised (0.70->0.82); ceiling (1.30x) unchanged.
        float controlMult = 0.82f + (p * 0.48f);

        // Accuracy multiplier (lower spread with mastery).
        float accuracyMult = 1.00f - (p * 0.20f);

        return new SpellScalingProfile(damageMult, cooldownMult, durationMult, controlMult, accuracyMult);
    }

    public static SpellScalingProfile getProfileForPlayer(ServerPlayer player, Identifier spellId) {
        if (!ModuleManager.isEnabled(Module.PROFICIENCY)) {
            return SpellScalingProfile.DEFAULT;
        }
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        if (data == null) {
            return computeProfile(0.0f);
        }
        // A spell practised to a tier never casts below what that tier promises, whatever an old save stored.
        String id = spellId.toString();
        return computeProfile(SpellPractice.effective(data.getSpellProficiency(id), data.getSuccessfulHits(id)));
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
