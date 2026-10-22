package at.koopro.wizardsandbeasts.artefact.gryffindor;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jspecify.annotations.NullMarked;

/** Routes the game's events to {@link GryffindorService}; decides nothing itself. */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class GryffindorEvents {

    private GryffindorEvents() {}

    /** After the damage has landed: who struck a foe, and whether a Hat's wearer is now in mortal danger. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof ServerPlayer striker) {
            GryffindorService.struck(striker, victim);
        }
        if (victim instanceof ServerPlayer wearer && source.getEntity() != null && wearer.isAlive()) {
            GryffindorService.hurtBy(wearer, source.getEntity());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof ServerPlayer killer) {
            ItemStack weapon = source.getWeaponItem() != null ? source.getWeaponItem() : killer.getMainHandItem();
            GryffindorService.killed(killer, event.getEntity(), weapon);
        }
    }

    /**
     * A dropped stack leaving the world for good -- burnt, blown up, fallen out of the world, despawned -- still holds
     * its item. A stack picked up or poured into a hopper leaves empty, and one in an unloading chunk is not destroyed.
     */
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof ItemEntity item && event.getLevel() instanceof ServerLevel level
                && entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy()
                && !item.getItem().isEmpty()) {
            GryffindorService.destroyed(level, item.getItem());
        }
    }
}
