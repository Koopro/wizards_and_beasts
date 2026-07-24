package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.lib.*;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.ModDimensions;
import at.koopro.wizardsandbeasts.block.trunk.TrunkBlock;
import at.koopro.wizardsandbeasts.trunk.TrunkAccessMode;
import at.koopro.wizardsandbeasts.trunk.ExtensionCharmService;
import at.koopro.wizardsandbeasts.trunk.TrunkRecord;
import at.koopro.wizardsandbeasts.util.WandHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

final class SpellCastUtilityHandler {

    private SpellCastUtilityHandler() {
    }

    static boolean handleEpiskeySelf(ServerPlayer caster) {
        // Residual tail (Step 4): only the HP-conditional Regeneration stays in Java; the flat heal and
        // the poison/wither/blindness/nausea cleanse moved to JSON `heal` + `dispel` components. This runs
        // before the effect runner in the SELF arm, so missing-HP (the 120-vs-80 gate) is read pre-heal.
        float missing = caster.getMaxHealth() - caster.getHealth();
        int regenDuration = missing >= 8.0f ? 120 : 80;
        caster.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regenDuration, 0, false, true, true));
        return true;
    }

    static boolean handleFrigoraSelf(ServerLevel level, ServerPlayer caster, Spell spell) {
        // Residual tail (Step 4): only the Glacius-style block interaction stays in Java; clearFire +
        // fire_resistance + water_breathing moved to JSON `clear_fire` + `apply_effect` components.
        Vec3 start = caster.getEyePosition();
        Vec3 end = start.add(caster.getLookAngle().scale(4.0));
        BlockHitResult blockHit = level.clip(new ClipContext(
                start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, caster));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            SpellHelper.tryGlaciusBlockInteraction(level, blockHit, spell);
        }
        return true;
    }

    static boolean handleReparoSelf(ServerLevel level, ServerPlayer caster, Spell spell) {
        // Residual tail (Step 4): reparo's full-repair + wand-aware selection + ElderWand guard live in
        // handleRepair, which the `repair` component can't reproduce; kept here. Block-repair fallback is
        // preserved but its range is no longer wand-scaled (was 5.0 * wand.rangeFor) — logged delta.
        ItemStack wandStack = WandHelper.getWandStack(caster);
        boolean repaired = handleRepair(caster, wandStack);
        if (!repaired) {
            Vec3 start = caster.getEyePosition();
            Vec3 end = start.add(caster.getLookAngle().scale(5.0));
            BlockHitResult blockHit = level.clip(new ClipContext(
                    start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, caster));
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                // Area rebuild: mend every repairable block in a small radius around the aim point, so one
                // cast patches a cracked wall instead of a single block.
                BlockPos center = blockHit.getBlockPos();
                int radius = 3;
                for (BlockPos p : BlockPos.betweenClosed(
                        center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
                    if (SpellHelper.tryReparoRepairBlock(level, p.immutable(), spell)) {
                        repaired = true;
                    }
                }
            }
        }
        if (repaired) {
            SpellHelper.spawnBurst(level, spell, caster.getEyePosition(), 12, 0.2);
        }
        return repaired;
    }

    static boolean handleRepair(ServerPlayer caster, ItemStack wandStack) {
        ItemStack toRepair = wandStack == caster.getMainHandItem()
                ? caster.getOffhandItem()
                : caster.getMainHandItem();
        if (!WandHelper.isWand(wandStack) && WandHelper.isWand(caster.getMainHandItem())) {
            toRepair = caster.getOffhandItem();
        }

        if (ModDataComponents.isElderWand(toRepair)) {
            caster.displayClientMessage(
                    Component.literal("This wand refuses Reparo.").withStyle(ChatFormatting.DARK_RED), true);
            return false;
        }

        if (toRepair.isDamageableItem() && toRepair.isDamaged()) {
            int before = toRepair.getDamageValue();
            toRepair.setDamageValue(0);
            return before > 0;
        }
        return false;
    }

    static boolean handlePocketIngress(ServerPlayer caster) {
        ItemStack caseStack = findPocketCase(caster);
        if (caseStack.isEmpty()
                || !(caseStack.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof TrunkBlock trunk)) {
            caster.displayClientMessage(Component.literal("You need a trunk to cast this spell.")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // Enter the same lock-1 compartment the placed block descends into, so the spell and the
        // block share one space per trunk.
        UUID base = ensureCaseId(caseStack);
        UUID compartmentId = TrunkBlock.lockCaseId(base, 1);
        boolean muggleWorthy = caseStack.getOrDefault(ModDataComponents.POCKET_MUGGLE_WORTHY.get(), false);
        TrunkRecord pocket = ExtensionCharmService.getOrCreatePocket(caster, compartmentId, trunk.archetype(),
                "trunk_lock_1", TrunkAccessMode.SEALED, muggleWorthy, false, trunk.radiusTier());
        caseStack.set(ModDataComponents.POCKET_ID.get(), pocket.pocketId());
        // Muggle-Worthy blocks non-owner entry; the owner always gets in.
        if (pocket.muggleWorthy() && !pocket.owner().equals(caster.getUUID())) {
            caster.displayClientMessage(
                    Component.literal("The case appears to contain only travel supplies."), true);
            return false;
        }
        ExtensionCharmService.enterPocket(caster, pocket);
        return true;
    }

    static boolean handlePocketEgress(ServerPlayer caster) {
        if (!(caster.level() instanceof ServerLevel level) || !level.dimension().equals(ModDimensions.EXTENSION_REALM)) {
            return false;
        }
        ExtensionCharmService.exitPocket(caster);
        return true;
    }

    private static ItemStack findPocketCase(ServerPlayer player) {
        int size = player.getInventory().getContainerSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof TrunkBlock) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static UUID ensureCaseId(ItemStack stack) {
        UUID existing = stack.get(ModDataComponents.POCKET_CASE_ID.get());
        if (existing != null) {
            return existing;
        }
        UUID created = UUID.randomUUID();
        stack.set(ModDataComponents.POCKET_CASE_ID.get(), created);
        return created;
    }
}
