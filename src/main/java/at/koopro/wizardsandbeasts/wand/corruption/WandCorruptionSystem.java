package at.koopro.wizardsandbeasts.wand.corruption;

import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.item.wand.WandModuleHooks;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries;
import at.koopro.wizardsandbeasts.wand.registry.WandWoodDefinition;
import at.koopro.wizardsandbeasts.wand.resonance.WandResonanceConfigLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Corruption accumulation on the wand. Spell dispatch should call {@link #onSpellCast} once per cast.
 * // Spell system (future): replace {@link #isDarkSpell} / {@link #isLightSpell} heuristics with datapack tags.
 */
public final class WandCorruptionSystem {

    private WandCorruptionSystem() {
    }

    public static void onSpellCast(Player player, ItemStack wand, Identifier spellKey) {
        if (!ModuleManager.isEnabled(Module.WANDS) || !(wand.getItem() instanceof WandItem)) {
            return;
        }
        // Spell system integration: read corruption_increment from spell definition when available.
        float increment = isDarkSpell(spellKey) ? 0.02f : 0.0f;
        if (increment <= 0.0f) {
            return;
        }
        float oldCorruption = WandComponents.getCorruption(wand);
        float newCorruption = Math.min(1.0f, oldCorruption + increment);
        wand.set(WandComponents.WAND_CORRUPTION.get(), newCorruption);
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        if (oldCorruption < 0.5f && newCorruption >= 0.5f) {
            sp.displayClientMessage(prefixPreview(Component.translatable("wandcraft.corruption.threshold_warning")), false);
        }
        if (oldCorruption < 1.0f && newCorruption >= 1.0f) {
            sp.displayClientMessage(prefixPreview(Component.translatable("wandcraft.corruption.fully_corrupted")), false);
        }
    }

    /** Temporary: Dark spells identified by path segment — spell system will use proper tags. */
    public static boolean isDarkSpell(Identifier spellKey) {
        return spellKey.getPath().contains("dark_");
    }

    /** Temporary: Light spells identified by path segment — spell system will use proper tags. */
    public static boolean isLightSpell(Identifier spellKey) {
        return spellKey.getPath().contains("light_");
    }

    public static float getEffectivePowerMultiplier(ItemStack wand, Identifier spellKey, RegistryAccess registryAccess) {
        float integrity = WandComponents.getIntegrity(wand);
        float corruption = WandComponents.getCorruption(wand);
        float mult = integrity;
        float darkBonusThreshold = WandResonanceConfigLoader.getConfig(registryAccess).corruptionDarkBonusThreshold();
        if (corruption > darkBonusThreshold && isDarkSpell(spellKey)) {
            mult *= 1.0f + (corruption - darkBonusThreshold);
        }
        Identifier woodId = WandComponents.getWood(wand);
        if (woodId != null && corruption > darkBonusThreshold && isLightSpell(spellKey)) {
            Optional<Holder.Reference<WandWoodDefinition>> wood =
                    registryAccess.lookupOrThrow(WandDatapackRegistries.WAND_WOOD_REGISTRY).get(woodId);
            if (wood.isPresent() && wood.get().value().affinityTags().contains("healing")) {
                mult *= 1.0f - (corruption - darkBonusThreshold);
            }
        }
        return Math.max(0.1f, Math.min(2.0f, mult));
    }

    private static Component prefixPreview(Component base) {
        if (WandModuleHooks.isWandsPreview()) {
            return Component.literal("[Preview] ").append(base);
        }
        return base;
    }
}
