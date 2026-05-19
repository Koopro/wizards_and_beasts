package at.koopro.wizardsandbeasts.wand.allegiance;

import at.koopro.wizardsandbeasts.wand.elder.ElderWandEventHandler;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.wand.WandAttachments;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.registry.WandCoreDefinition;
import at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

import java.util.Optional;
import java.util.UUID;

public final class WandDisarmAllegianceSystem {
    private static final int DISARM_WINDOW_TICKS = 20;

    private WandDisarmAllegianceSystem() {
    }

    public static void onLivingEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!ModuleManager.isEnabled(Module.WANDS)) {
            return;
        }
        if (event.getSlot() != EquipmentSlot.MAINHAND) {
            return;
        }
        ItemStack oldStack = event.getFrom();
        ItemStack newStack = event.getTo();
        if (!newStack.isEmpty() || oldStack.isEmpty() || !(oldStack.getItem() instanceof WandItem)) {
            return;
        }
        if (WandComponents.getMaster(oldStack).isEmpty()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        ServerLevel level = (ServerLevel) victim.level();
        Optional<RecentDisarmRegistry.Entry> disarm = RecentDisarmRegistry.consumeIfRecent(level, victim.getUUID(), DISARM_WINDOW_TICKS);
        if (disarm.isEmpty()) {
            return;
        }
        ServerPlayer attacker = level.getServer().getPlayerList().getPlayer(disarm.get().attacker());
        if (attacker == null) {
            return;
        }
        ItemStack wand = resolveDisarmedWandStack(victim, oldStack);
        processAllegianceTransfer(attacker, victim, wand);
    }

    private static ItemStack resolveDisarmedWandStack(ServerPlayer victim, ItemStack fromSlot) {
        AABB box = victim.getBoundingBox().inflate(6.0);
        for (ItemEntity entity : victim.level().getEntitiesOfClass(ItemEntity.class, box)) {
            ItemStack s = entity.getItem();
            if (s.getItem() instanceof WandItem && stacksMatchForTransfer(s, fromSlot)) {
                return s;
            }
        }
        return fromSlot;
    }

    private static boolean stacksMatchForTransfer(ItemStack a, ItemStack b) {
        if (!(a.getItem() instanceof WandItem) || !(b.getItem() instanceof WandItem)) {
            return false;
        }
        return java.util.Objects.equals(WandComponents.getWood(a), WandComponents.getWood(b))
                && java.util.Objects.equals(WandComponents.getCore(a), WandComponents.getCore(b))
                && WandComponents.getMaster(a).equals(WandComponents.getMaster(b));
    }

    public static void processAllegianceTransfer(ServerPlayer attacker, ServerPlayer victim, ItemStack wand) {
        Identifier coreId = WandComponents.getCore(wand);
        if (coreId == null) {
            return;
        }
        var coreOpt = attacker.registryAccess().lookupOrThrow(WandDatapackRegistries.WAND_CORE_REGISTRY).get(coreId);
        if (coreOpt.isEmpty()) {
            return;
        }
        WandCoreDefinition coreDef = coreOpt.get().value();
        if (attacker.getRandom().nextFloat() <= coreDef.allegianceTransferResistance()) {
            attacker.displayClientMessage(Component.translatable("wandcraft.allegiance.resisted"), false);
            return;
        }
        float allegiance = Math.max(0.0f, WandComponents.getAllegianceScore(wand) - 0.3f);
        wand.set(WandComponents.WAND_ALLEGIANCE_SCORE.get(), allegiance);
        wand.set(WandComponents.WAND_MASTER.get(), Optional.of(attacker.getUUID()));
        if (ModDataComponents.isElderWand(wand)) {
            ElderWandEventHandler.onElderWandMasterChanged((net.minecraft.server.level.ServerLevel) attacker.level(), attacker.getUUID());
        }
        WandFlexibility flex = WandComponents.getFlexibility(wand);
        Identifier wood = WandComponents.getWood(wand);
        if (wood != null && coreId != null && flex != null) {
            attacker.setData(WandAttachments.BONDED_WAND.get(), Optional.of(new WandAttachments.BondedWandRecord(
                    wood, coreId, flex, allegiance)));
        }
        victim.getData(WandAttachments.BONDED_WAND.get()).ifPresent(rec -> {
            if (rec.woodKey().equals(wood) && rec.coreKey().equals(coreId)) {
                victim.setData(WandAttachments.BONDED_WAND.get(), Optional.empty());
            }
        });
        attacker.displayClientMessage(Component.translatable("wandcraft.allegiance.transferred"), false);
        victim.displayClientMessage(Component.translatable("wandcraft.allegiance.lost"), false);
    }

    public static void onItemPickupPre(ItemEntityPickupEvent.Pre event) {
        if (!ModuleManager.isEnabled(Module.WANDS)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack stack = event.getItemEntity().getItem();
        if (!(stack.getItem() instanceof WandItem)) {
            return;
        }
        Optional<UUID> master = WandComponents.getMaster(stack);
        if (master.isEmpty() || master.get().equals(player.getUUID())) {
            return;
        }
        player.displayClientMessage(Component.translatable("wandcraft.allegiance.stolen_warning"), false);
    }
}
