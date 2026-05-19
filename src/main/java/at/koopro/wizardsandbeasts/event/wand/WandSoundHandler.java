package at.koopro.wizardsandbeasts.event.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

/**
 * Server-side ambience when equipping/swinging a wand.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class WandSoundHandler {

    private WandSoundHandler() {}

    @SubscribeEvent
    public static void onLivingEquipmentChanged(LivingEquipmentChangeEvent event) {
        if (event.getSlot() != EquipmentSlot.MAINHAND) {
            return;
        }
        LivingEntity living = event.getEntity();
        if (!(living instanceof Player)) {
            return;
        }
        if (!(living.level() instanceof ServerLevel)) {
            return;
        }
        ServerLevel level = (ServerLevel) living.level();
        ServerPlayer player = (ServerPlayer) living;
        ItemStack from = event.getFrom();
        ItemStack to = event.getTo();
        boolean wandTo = !to.isEmpty() && to.getItem() instanceof WandItem;
        boolean wandFrom = !from.isEmpty() && from.getItem() instanceof WandItem;
        if (wandTo && !wandFrom) {
            level.playSound(null, player.blockPosition(), ModSounds.WAND_EQUIP.get(), SoundSource.PLAYERS,
                    0.82f, 1.03f + level.random.nextFloat() * 0.08f);
        } else if (wandFrom && !wandTo) {
            level.playSound(null, player.blockPosition(), ModSounds.WAND_UNEQUIP.get(), SoundSource.PLAYERS,
                    0.72f, 0.95f + level.random.nextFloat() * 0.1f);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel)) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (!(player.getMainHandItem().getItem() instanceof WandItem)) {
            return;
        }
        level.playSound(null, event.getTarget().blockPosition(), ModSounds.WAND_SWING.get(), SoundSource.PLAYERS,
                0.58f, 1.0f + level.random.nextFloat() * 0.15f);
    }
}
