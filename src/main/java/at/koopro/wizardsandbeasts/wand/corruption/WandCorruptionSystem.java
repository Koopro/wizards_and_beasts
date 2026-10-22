package at.koopro.wizardsandbeasts.wand.corruption;

import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.item.wand.WandModuleHooks;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
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
 */
public final class WandCorruptionSystem {

    private WandCorruptionSystem() {
    }

    public static void onSpellCast(Player player, ItemStack wand, SpellCategory category) {
        if (!ModuleManager.isEnabled(Module.WANDS) || !(wand.getItem() instanceof WandItem)) {
            return;
        }
        if (category != SpellCategory.DARK_ARTS) {
            return;
        }
        // The core's dark affinity sets how fast the Dark Arts take hold of the wand (WandStatsResolver.corruptionRate).
        float increment = BASE_DARK_CORRUPTION * coreCorruptionRate(wand, player.level().registryAccess());
        float oldCorruption = WandComponents.getCorruption(wand);
        float newCorruption = Math.min(1.0f, oldCorruption + increment);
        wand.set(WandComponents.WAND_CORRUPTION.get(), newCorruption);
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        if (oldCorruption < 0.5f && newCorruption >= 0.5f) {
            PlayerFeedback.toast(sp, NoticeKind.WARN,
                    prefixPreview(Component.translatable("wandcraft.corruption.threshold_warning.title")),
                    Component.translatable("wandcraft.corruption.threshold_warning"));
        }
        if (oldCorruption < 1.0f && newCorruption >= 1.0f) {
            PlayerFeedback.toast(sp, NoticeKind.WARN,
                    prefixPreview(Component.translatable("wandcraft.corruption.fully_corrupted.title")),
                    Component.translatable("wandcraft.corruption.fully_corrupted"));
        }
    }

    /** Corruption one Dark Arts cast adds to a wand whose core has a neutral (0.5) dark affinity. */
    public static final float BASE_DARK_CORRUPTION = 0.02f;

    private static float coreCorruptionRate(ItemStack wand, RegistryAccess registries) {
        Identifier core = at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver.resolveCoreId(wand);
        return core == null ? 1.0f : at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver.coreDefinition(core, registries)
                .map(d -> at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver.corruptionRate(d.darkAffinity()))
                .orElse(1.0f);
    }

    public static float getEffectivePowerMultiplier(ItemStack wand, SpellCategory category, RegistryAccess registryAccess) {
        float integrity = WandComponents.getIntegrity(wand);
        float corruption = WandComponents.getCorruption(wand);
        float mult = integrity;
        float darkBonusThreshold = WandResonanceConfigLoader.getConfig(registryAccess).corruptionDarkBonusThreshold();
        if (corruption > darkBonusThreshold && category == SpellCategory.DARK_ARTS) {
            mult *= 1.0f + (corruption - darkBonusThreshold);
        }
        Identifier woodId = at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver.resolveWoodId(wand);
        if (woodId != null && corruption > darkBonusThreshold && category == SpellCategory.DEFENSE) {
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
