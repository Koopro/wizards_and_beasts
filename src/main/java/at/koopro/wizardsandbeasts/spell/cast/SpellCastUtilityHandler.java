package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.lib.*;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.ModDimensions;
import at.koopro.wizardsandbeasts.item.trunk.EnchantedTrunkItem;
import at.koopro.wizardsandbeasts.trunk.TrunkAccessMode;
import at.koopro.wizardsandbeasts.trunk.TrunkArchetype;
import at.koopro.wizardsandbeasts.trunk.ExtensionCharmService;
import at.koopro.wizardsandbeasts.trunk.TrunkRecord;
import at.koopro.wizardsandbeasts.trunk.TrunkTier;
import at.koopro.wizardsandbeasts.util.WandHelper;
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
        float missing = caster.getMaxHealth() - caster.getHealth();
        if (missing > 0.0f) {
            caster.heal(Math.min(4.0f, missing));
        }
        caster.removeEffect(MobEffects.POISON);
        caster.removeEffect(MobEffects.WITHER);
        caster.removeEffect(MobEffects.BLINDNESS);
        caster.removeEffect(MobEffects.NAUSEA);
        int regenDuration = missing >= 8.0f ? 120 : 80;
        caster.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regenDuration, 0, false, true, true));
        return true;
    }

    static boolean handleFrigoraSelf(ServerLevel level, ServerPlayer caster, Spell spell) {
        caster.clearFire();
        caster.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 220, 0, false, true, true));
        caster.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 120, 0, false, true, true));
        Vec3 start = caster.getEyePosition();
        Vec3 end = start.add(caster.getLookAngle().scale(4.0));
        BlockHitResult blockHit = level.clip(new ClipContext(
                start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, caster));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            SpellHelper.tryGlaciusBlockInteraction(level, blockHit, spell);
        }
        return true;
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
        if (caseStack.isEmpty()) {
            caster.displayClientMessage(Component.literal("You need an Enchanted Trunk to cast this spell.")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        UUID caseId = ensureCaseId(caseStack);
        TrunkArchetype archetype = caseStack.getOrDefault(ModDataComponents.POCKET_ARCHETYPE.get(), TrunkArchetype.FIELD_CAMP);
        TrunkAccessMode accessMode = caseStack.getOrDefault(ModDataComponents.POCKET_ACCESS_MODE.get(), TrunkAccessMode.SEALED);
        TrunkTier tier = caseStack.getItem() instanceof EnchantedTrunkItem trunkItem ? trunkItem.tier() : TrunkTier.TIER_1;
        TrunkRecord pocket = ExtensionCharmService.getOrCreatePocket(caster, caseId, archetype,
                caseStack.getOrDefault(ModDataComponents.POCKET_TEMPLATE_ID.get(), "blank_shell"),
                accessMode, false, false, tier);
        caseStack.set(ModDataComponents.POCKET_ID.get(), pocket.pocketId());
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
            if (stack.getItem() instanceof EnchantedTrunkItem) {
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
