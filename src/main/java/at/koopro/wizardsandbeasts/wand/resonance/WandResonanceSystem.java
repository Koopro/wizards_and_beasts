package at.koopro.wizardsandbeasts.wand.resonance;

import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.item.wand.WandModuleHooks;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.wand.WandAttachments;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.registry.WandCoreDefinition;
import at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries;
import at.koopro.wizardsandbeasts.wand.registry.WandWoodDefinition;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.particles.ParticleTypes;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure logic for wand–wizard resonance. Invoked from {@link at.koopro.wizardsandbeasts.item.wand.WandItem}
 * and Ollivander trial GUI — no automatic event subscriptions.
 */
public final class WandResonanceSystem {

    private WandResonanceSystem() {
    }

    public record ResonanceResult(boolean matched, float score, Identifier woodKey, Identifier coreKey) {
    }

    public static float computeResonance(Player player, ItemStack wandStack, RegistryAccess registryAccess) {
        Identifier woodKey = WandComponents.getWood(wandStack);
        Identifier coreKey = WandComponents.getCore(wandStack);
        WandFlexibility flex = WandComponents.getFlexibility(wandStack);
        if (woodKey == null || coreKey == null || flex == null) {
            return 0.0f;
        }
        String cacheKey = woodKey + "|" + coreKey + "|" + flex.getSerializedName();
        Map<String, Float> cache = player.getData(WandAttachments.WAND_RESONANCE_CACHE.get());
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        WandResonanceConfig cfg = WandResonanceConfigLoader.getConfig(registryAccess);
        Optional<Holder.Reference<WandWoodDefinition>> woodHolder =
                registryAccess.lookupOrThrow(WandDatapackRegistries.WAND_WOOD_REGISTRY).get(woodKey);
        Optional<Holder.Reference<WandCoreDefinition>> coreHolder =
                registryAccess.lookupOrThrow(WandDatapackRegistries.WAND_CORE_REGISTRY).get(coreKey);
        if (woodHolder.isEmpty() || coreHolder.isEmpty()) {
            return 0.0f;
        }
        WandWoodDefinition wood = woodHolder.get().value();
        WandCoreDefinition core = coreHolder.get().value();

        float woodScore = scoreWoodPersonality(player, wood);
        float coreScore = scoreCoreTemperament(player, core);
        Float lengthInches = WandComponents.getLength(wandStack);
        float len = lengthInches == null ? 11.0f : lengthInches;
        float lengthScore = scoreLengthAffinity(player, len);
        float flexScore = scoreFlexibilityMatch(player, flex);

        float sum = cfg.woodWeight() * woodScore
                + cfg.coreWeight() * coreScore
                + cfg.lengthWeight() * lengthScore
                + cfg.flexibilityWeight() * flexScore;
        float result = Math.max(0.0f, Math.min(1.0f, sum));
        cache.put(cacheKey, result);
        player.setData(WandAttachments.WAND_RESONANCE_CACHE.get(), cache);
        return result;
    }

    private static float scoreWoodPersonality(Player player, WandWoodDefinition wood) {
        PlayerHeritageData typeData = player.getData(ModAttachments.HERITAGE_DATA.get());
        HeritageVariant sub = typeData.getSelectedHeritageVariant();
        if (sub == null) {
            return 0.0f;
        }
        for (String tag : wood.personalityAffinity()) {
            if (tag.equals(sub.getId())) {
                return 1.0f;
            }
            if (sub.getTags().contains(tag)) {
                return 1.0f;
            }
        }
        return 0.0f;
    }

    /** Gaussian preference: ideal raw-power affinity derived from player reach and level. */
    private static float scoreCoreTemperament(Player player, WandCoreDefinition core) {
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        float ideal = (float) Math.min(1.0, Math.max(0.0, (reach - 2.5) / 3.0 + player.experienceLevel * 0.01f));
        float delta = core.rawPower() - ideal;
        return (float) Math.exp(-0.5 * (delta * 5.0) * (delta * 5.0));
    }

    private static float scoreLengthAffinity(Player player, float wandLengthInches) {
        double reach = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        float combatLean = (float) Math.min(1.0, Math.max(0.0, (reach - 2.5) / 2.5));
        float idealLength = 8.0f + combatLean * 6.0f;
        return 1.0f - Math.min(1.0f, Math.abs(wandLengthInches - idealLength) / 8.0f);
    }

    private static float scoreFlexibilityMatch(Player player, WandFlexibility wandFlex) {
        PlayerHeritageData td = player.getData(ModAttachments.HERITAGE_DATA.get());
        HeritageVariant sub = td.getSelectedHeritageVariant();
        float adaptability = Math.min(1.0f, player.experienceLevel / 30.0f);
        if (sub != null && sub.hasTag("obscurus_form") && sub.hasTag("transformation")) {
            adaptability = Math.min(1.0f, adaptability + 0.25f);
        }
        int idealOrdinal = Math.round(adaptability * (WandFlexibility.values().length - 1));
        int distance = Math.abs(wandFlex.ordinal() - idealOrdinal);
        return 1.0f - distance / (float) (WandFlexibility.values().length - 1);
    }

    public static ResonanceResult applyResonance(Player player, ItemStack wandStack, float score, RegistryAccess registryAccess) {
        Identifier woodKey = WandComponents.getWood(wandStack);
        Identifier coreKey = WandComponents.getCore(wandStack);
        WandFlexibility flex = WandComponents.getFlexibility(wandStack);
        if (woodKey == null || coreKey == null) {
            return new ResonanceResult(false, score, woodKey, coreKey);
        }

        WandResonanceConfig cfg = WandResonanceConfigLoader.getConfig(registryAccess);
        float refuseFloor = cfg.refuseThreshold();
        Optional<Holder.Reference<WandWoodDefinition>> woodOpt =
                registryAccess.lookupOrThrow(WandDatapackRegistries.WAND_WOOD_REGISTRY).get(woodKey);
        if (woodOpt.isPresent()) {
            refuseFloor = Math.max(refuseFloor, woodOpt.get().value().refuseThreshold());
        }

        if (score >= cfg.matchThreshold()) {
            wandStack.set(WandComponents.WAND_MASTER.get(), Optional.of(player.getUUID()));
            wandStack.set(WandComponents.WAND_ALLEGIANCE_SCORE.get(), 1.0f);
            if (flex != null) {
                float allegiance = WandComponents.getAllegianceScore(wandStack);
                player.setData(WandAttachments.BONDED_WAND.get(), Optional.of(new WandAttachments.BondedWandRecord(
                        woodKey, coreKey, flex, allegiance)));
            }
            if (player instanceof ServerPlayer sp) {
                sp.level().playSound(null, sp.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
                sp.level().sendParticles(ParticleTypes.ENCHANT, sp.getX(), sp.getEyeY(), sp.getZ(), 24, 0.35, 0.25, 0.35, 0.02);
                Component woodName = woodOpt.map(h -> h.value().displayName()).orElse(Component.literal(woodKey.toString()));
                Component coreName = registryAccess.lookupOrThrow(WandDatapackRegistries.WAND_CORE_REGISTRY)
                        .get(coreKey).map(h -> h.value().displayName()).orElse(Component.literal(coreKey.toString()));
                sp.displayClientMessage(prefixPreview(Component.translatable("wandcraft.resonance.matched", woodName, coreName)), false);
            }
            return new ResonanceResult(true, score, woodKey, coreKey);
        }

        if (score >= refuseFloor) {
            if (player instanceof ServerPlayer sp) {
                sp.level().playSound(null, sp.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.2f, 1.2f);
                sp.displayClientMessage(prefixPreview(Component.translatable("wandcraft.resonance.mismatch")), false);
            }
            return new ResonanceResult(false, score, woodKey, coreKey);
        }

        if (player instanceof ServerPlayer sp) {
            sp.hurt(sp.damageSources().magic(), 4.0f);
            sp.level().playSound(null, sp.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.15f, 1.5f);
            sp.level().sendParticles(ParticleTypes.LARGE_SMOKE, sp.getX(), sp.getEyeY(), sp.getZ(), 12, 0.2, 0.2, 0.2, 0.01);
            sp.displayClientMessage(prefixPreview(Component.translatable("wandcraft.resonance.refused")), false);
        }
        return new ResonanceResult(false, score, woodKey, coreKey);
    }

    private static Component prefixPreview(Component base) {
        if (WandModuleHooks.isWandsPreview()) {
            return Component.literal("[Preview] ").append(base);
        }
        return base;
    }
}
