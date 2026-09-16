package at.koopro.wizardsandbeasts.spell.protego;

import at.koopro.wizardsandbeasts.network.spell.ProtegoChargeS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.SpellIds;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The wand-hold side of the Shield Charm: it turns "how long have they been holding" into a tier,
 * and says so while they hold it.
 *
 * <p>This is the adapter between the pure ladder in {@link ProtegoRules} and a live player — it is
 * the only place that reads a caster's proficiency and Dark Arts depth, so the tier the charge-up
 * announces and the tier the release raises cannot drift apart.
 */
public final class ProtegoCharge {

    /** Ticks between charge snapshots sent to the caster. Two is smooth and costs ten bytes a tick. */
    private static final int CHARGE_REPORT_INTERVAL = 2;

    private ProtegoCharge() {}

    /** What this caster would get if they let go now. */
    public static ProtegoRules.TierResolution resolve(ServerPlayer caster, int heldTicks) {
        return ProtegoRules.resolveTier(heldTicks, proficiencyOf(caster), darkArtsDepth(caster));
    }

    /**
     * Charge feedback, once per tick of a wand hold. Called from {@code WandItem.onUseTick}.
     *
     * <p>Deliberately silent unless the caster could actually cast Protego right now — feedback for
     * a charge that will be refused at the release is worse than none.
     */
    public static void tick(ServerPlayer caster, int heldTicks) {
        if (heldTicks <= 0 || !(caster.level() instanceof ServerLevel level) || !isChargingProtego(caster)) {
            return;
        }
        Proficiency proficiency = proficiencyOf(caster);
        int depth = darkArtsDepth(caster);
        ProtegoRules.TierResolution now = ProtegoRules.resolveTier(heldTicks, proficiency, depth);
        ProtegoRules.TierResolution previous = ProtegoRules.resolveTier(heldTicks - 1, proficiency, depth);
        boolean planting = ProtegoRules.canPlant(now.tier(), caster.isShiftKeyDown());

        if (now.tier() != previous.tier()) {
            ProtegoFeedback.chargeStep(level, caster, now.tier(), planting);
        } else if (now.reachedByHold() != previous.reachedByHold() && now.capped()) {
            // They held long enough for the next shape and hit a wall instead. Said once, exactly
            // when the hold crosses the threshold, so it reads as an answer rather than nagging.
            ProtegoFeedback.chargeCapped(level, caster, now.limit());
        }
        float progress = ProtegoRules.chargeProgress(heldTicks, proficiency, depth);
        ProtegoFeedback.chargeAmbient(level, caster, now.tier(), heldTicks, planting, progress);

        // The caster's own screen and wand, on a two-tick beat. Only they get this: it is feedback
        // about a decision in progress, and the client ends it by letting the state go stale.
        if (heldTicks % CHARGE_REPORT_INTERVAL == 0 || now.tier() != previous.tier()) {
            ProtegoChargeS2CPayload.sendTo(caster, now.tier().index(), progress, planting, now.capped());
        }
    }

    /** Is this hold a Protego hold that the cast gate would let through? */
    public static boolean isChargingProtego(ServerPlayer caster) {
        PlayerSpellData data = caster.getData(ModAttachments.SPELL_DATA.get());
        String active = data.getActiveSpellId();
        String protego = Spells.PROTEGO.getId();
        if (active == null || !SpellIds.matches(active, protego)) {
            return false;
        }
        return data.knowsSpell(protego) && !data.isOnCooldown(protego, caster.level().getGameTime());
    }

    private static Proficiency proficiencyOf(ServerPlayer caster) {
        return Spells.PROTEGO.getProficiency(caster);
    }

    /**
     * How far into the Dark Arts this caster has gone. Horribilis is the ward built specifically
     * against Dark magic, and the lore reason it is rare is that warding a curse means understanding
     * it — so the gate is study, not level.
     */
    private static int darkArtsDepth(ServerPlayer caster) {
        return SkillSystemAPI.countUnlockedSkillsInTree(caster, SkillTreeId.DARK_ARTS);
    }
}
