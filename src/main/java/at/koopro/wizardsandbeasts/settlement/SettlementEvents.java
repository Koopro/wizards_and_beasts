package at.koopro.wizardsandbeasts.settlement;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.settlement.post.PostRules;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jspecify.annotations.NullMarked;

/**
 * The two things only an entity can report to its post. Works on the attachment, so anyone keeping a post is covered,
 * the goblin teller as much as the shared NPC.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class SettlementEvents {

    private SettlementEvents() {}

    /**
     * A blow that would kill someone keeping a post: they Apparate away instead. Cancelling the death means no drops
     * and no experience, so there is nothing to farm, and they are recorded away until dawn, identity kept.
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !entity.hasData(ModAttachments.SETTLEMENT_POST.get())) {
            return;
        }
        PostRef ref = entity.getData(ModAttachments.SETTLEMENT_POST.get());
        SettlementData data = SettlementData.get(level);
        SettlementData.Post post = data.post(ref);
        if (post == null || post.npc().filter(entity.getUUID()::equals).isEmpty()) {
            return;
        }
        event.setCanceled(true);
        data.setPost(ref, post.away(PostRules.backAt(level.getGameTime(), level.getDayTime(), level.getDayTimePerTick()),
                PostRules.dawn(level.getDayTime())));
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), ModSounds.APPARITION_CRACK_WIZARD.get(),
                SoundSource.NEUTRAL, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 1.0, entity.getZ(), 16, 0.3, 0.5, 0.3, 0.02);
        entity.discard();
    }

    /**
     * Only the person the record names may stand at a post. Any other copy, whichever way round the chunks loaded, is
     * stale: one replaced after being lost, or one back from an old region file.
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(event.getLevel() instanceof ServerLevel level) || !entity.hasData(ModAttachments.SETTLEMENT_POST.get())) {
            return;
        }
        SettlementData.Post post = SettlementData.get(level).post(entity.getData(ModAttachments.SETTLEMENT_POST.get()));
        if (post == null || post.status() != PostRules.Status.PRESENT
                || post.npc().filter(entity.getUUID()::equals).isEmpty()) {
            event.setCanceled(true);
        }
    }
}
