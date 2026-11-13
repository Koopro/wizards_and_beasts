package at.koopro.wizardsandbeasts.client.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.beam.WandTipTracker;
import at.koopro.wizardsandbeasts.client.pose.ClientCastAnimationState;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * While a wizard holds a cast in its wind-up, the spell gathers at the wand tip: a mote of the spell's own trail
 * particle every other tick, two once the hold passes a second. Only Protego had any sign of a held cast before
 * (its charge tint); every other spell held looked exactly like a wand at rest.
 *
 * <p>Read entirely from the cast state the server already sends ({@code SpellCastAnimationS2CPayload}, hold phase 0),
 * so other players see it too and nothing is predicted. Beams skip the wind-up (they channel from the first tick), so
 * they never show it. The motes thin with the vanilla particle setting like every tinted spell particle.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class CastChargeMotes {

    /** Hold ticks after which the gathering doubles. */
    private static final int STRONG_AFTER = 20;

    private CastChargeMotes() {}

    private record PendingFlash(int casterId, int argb, long atTick) {}

    private static final java.util.List<PendingFlash> PENDING = new java.util.ArrayList<>();

    /** Queues a release flash at a caster's wand tip {@code delay} ticks from now. */
    static void flashLater(int casterId, int argb, int delay) {
        PENDING.add(new PendingFlash(casterId, argb, ClientCastAnimationState.clientTick() + delay));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        long now = ClientCastAnimationState.clientTick();
        RandomSource random = mc.level.random;
        if (!PENDING.isEmpty()) {
            var due = PENDING.iterator();
            while (due.hasNext()) {
                PendingFlash flash = due.next();
                if (flash.atTick() > now && flash.atTick() - now < 40) {
                    continue;
                }
                due.remove();
                if (flash.atTick() <= now
                        && mc.level.getEntity(flash.casterId()) instanceof net.minecraft.world.entity.LivingEntity caster) {
                    SpellVfxClient.releaseFlashNow(caster, flash.argb());
                }
            }
        }
        for (Player player : mc.level.players()) {
            ClientCastAnimationState.ActiveCast cast = ClientCastAnimationState.get(player.getId(), now);
            if (cast == null || cast.holdPhase() != 0 || cast.spellId().isEmpty()) {
                continue;
            }
            long held = now - cast.startTick();
            if (held % 2 != 0) {
                continue;
            }
            Spell spell = Spells.byId(cast.spellId());
            if (spell == null) {
                continue;
            }
            var options = ModParticles.tinted(spell.vfx().trail().particleFamily(), spell.getColor());
            Vec3 tip = WandTipTracker.resolve(player, 1.0f);
            int count = held >= STRONG_AFTER ? 2 : 1;
            for (int i = 0; i < count; i++) {
                mc.level.addParticle(options,
                        tip.x + (random.nextDouble() - 0.5) * 0.12,
                        tip.y + (random.nextDouble() - 0.5) * 0.12,
                        tip.z + (random.nextDouble() - 0.5) * 0.12,
                        0.0, 0.0, 0.0);
            }
        }
    }
}
