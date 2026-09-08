package at.koopro.wizardsandbeasts.wand.cast;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.wand.stat.WandCore;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.wand.stat.WandLength;
import at.koopro.wizardsandbeasts.wand.stat.WandWood;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.cast.ModifierStack;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.registry.WandCastModifiers;
import at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries;
import at.koopro.wizardsandbeasts.wand.registry.WandCoreDefinition;
import at.koopro.wizardsandbeasts.wand.registry.WandWoodDefinition;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a wand's four soul attributes (wood, core, length, flexibility) into
 * a single {@link WandStats}. Lore-faithful starter table — every modifier is
 * tunable in one place and intentionally moderate so that no single component
 * dominates the math (the pillar that does the heavy lifting is the core).
 *
 * <p>Wood and core are both datapack-driven — {@link WandWoodDefinition#castModifiers()} and
 * {@link WandCoreDefinition#castModifiers()} — and are resolved by exactly the same path. Length and
 * flexibility remain per-enum tables here: neither has a datapack registry to be moved into, and both
 * are closed sets that a datapack cannot extend, so there is nothing to migrate.
 */
public final class WandStatsResolver {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Ids already reported as undefined. {@link #resolve} runs once per cast and once per beam
     * tick, so an unguarded warning would flood the log for as long as the wand is held.
     */
    private static final Set<Identifier> WARNED_MISSING_WOODS = ConcurrentHashMap.newKeySet();

    /** Same guard as {@link #WARNED_MISSING_WOODS}, for cores. */
    private static final Set<Identifier> WARNED_MISSING_CORES = ConcurrentHashMap.newKeySet();

    private WandStatsResolver() {}

    /**
     * Returns the combined {@link WandStats} for a wand stack. If the stack is
     * not a wand or has missing attributes, the missing pieces fall back to
     * neutral contributions so the result is always usable in math.
     *
     * @param registries needed to read the wood and core definitions. When {@code null} neither
     *                   contributes — callers without a registry get length and flexibility, not a
     *                   crash. That is a real case: a tooltip context carries no registries on a
     *                   client that has not finished joining a world.
     */
    public static WandStats resolve(@Nullable ItemStack wandStack, HolderLookup.@Nullable Provider registries) {
        if (wandStack == null || wandStack.isEmpty() || !(wandStack.getItem() instanceof WandItem)) {
            return WandStats.NEUTRAL;
        }

        WandStats.Builder b = WandStats.builder();
        applyCore(b, resolveCoreId(wandStack), registries);
        applyWood(b, resolveWoodId(wandStack), registries);
        applyLength(b, resolveLength(wandStack));
        applyFlexibility(b, resolveFlexibility(wandStack));
        return b.build();
    }

    public static void applyToStack(ModifierStack stack, WandStats wandStats, Spell spell) {
        stack.multiplyDamage(wandStats.damageFor(spell), "wand");
        stack.multiplyCooldown(wandStats.cooldownFor(spell), "wand");
        if (wandStats.fizzleChance() > 0.0f) {
            stack.addMisfireChance(wandStats.fizzleChance(), "wand");
        }
    }

    // ── Cores: the dominant flavor knob ──────────────────────────────────

    /**
     * A core's contribution, read straight off its definition.
     *
     * <p>Identical in shape to {@link #applyWood}, which it was not until every core carried an
     * authored {@code cast_modifiers} block. Until then this fell through to a ten-case
     * {@code WandCore} switch for the three that did not — {@code troll_whisker}, {@code rougarou_hair}
     * and {@code white_river_monster_spine} — which cost more than the duplication: it forced a neutral
     * result to mean <em>"not authored"</em> rather than "authored as neutral", so a core deliberately
     * tuned to contribute nothing was indistinguishable from one nobody had got to yet, and a datapack
     * could not override a built-in core downwards at all. All ten are authored now, and both the
     * switch and that ambiguity are gone.
     */
    private static void applyCore(WandStats.Builder b, @Nullable Identifier coreId,
                                  HolderLookup.@Nullable Provider registries) {
        if (coreId == null || registries == null) return;
        WandCastModifiers mods = castModifiersForCore(coreId, registries);
        if (mods.isNeutral()) return;
        applyModifiers(b, mods);
    }

    /**
     * A core with no definition contributes nothing rather than throwing, exactly as
     * {@link #castModifiersFor} does for wood. Warned once per id for the same reason: this runs once
     * per cast and once per beam tick.
     */
    private static WandCastModifiers castModifiersForCore(Identifier coreId, HolderLookup.Provider registries) {
        Optional<Holder.Reference<WandCoreDefinition>> holder =
                registries.lookup(WandDatapackRegistries.WAND_CORE_REGISTRY)
                        .flatMap(lookup -> lookup.get(
                                ResourceKey.create(WandDatapackRegistries.WAND_CORE_REGISTRY, coreId)));
        if (holder.isEmpty()) {
            if (WARNED_MISSING_CORES.add(coreId)) {
                LOGGER.warn("Wand core '{}' has no definition; it contributes nothing to casts.", coreId);
            }
            return WandCastModifiers.NEUTRAL;
        }
        return holder.get().value().castModifiers();
    }

    /** Shared by wood and core: fold one {@link WandCastModifiers} into the builder. */
    private static void applyModifiers(WandStats.Builder b, WandCastModifiers mods) {
        b.mulDamage(mods.damage())
                .mulCooldown(mods.cooldown())
                .mulRange(mods.range())
                .addFizzle(mods.fizzle());
        mods.categoryDamageBonus().forEach(b::addCategoryDamageBonus);
    }

    // ── Woods: datapack-driven ───────────────────────────────────────────

    private static void applyWood(WandStats.Builder b, @Nullable Identifier woodId,
                                  HolderLookup.@Nullable Provider registries) {
        if (woodId == null || registries == null) return;
        WandCastModifiers mods = castModifiersFor(woodId, registries);
        if (mods.isNeutral()) return;
        applyModifiers(b, mods);
    }

    /**
     * A wood with no definition contributes nothing rather than throwing: an incomplete datapack
     * should not break casting. Uses the nullable {@code lookup} rather than {@code lookupOrThrow}
     * for the same reason — this runs on the cast path.
     */
    private static WandCastModifiers castModifiersFor(Identifier woodId, HolderLookup.Provider registries) {
        Optional<Holder.Reference<WandWoodDefinition>> holder =
                registries.lookup(WandDatapackRegistries.WAND_WOOD_REGISTRY)
                        .flatMap(lookup -> lookup.get(
                                ResourceKey.create(WandDatapackRegistries.WAND_WOOD_REGISTRY, woodId)));
        if (holder.isEmpty()) {
            if (WARNED_MISSING_WOODS.add(woodId)) {
                LOGGER.warn("Wand wood '{}' has no definition; it contributes nothing to casts.", woodId);
            }
            return WandCastModifiers.NEUTRAL;
        }
        return holder.get().value().castModifiers();
    }

    // ── Length: range / damage / cooldown trade ──────────────────────────

    private static void applyLength(WandStats.Builder b, @Nullable WandLength length) {
        if (length == null) return;
        switch (length) {
            case SHORT -> b.mulRange(0.90f).mulDamage(0.95f).mulCooldown(0.90f);
            case MEDIUM -> b.mulRange(0.97f);
            case STANDARD -> {
                // neutral
            }
            case LONG -> b.mulRange(1.10f).mulDamage(1.05f).mulCooldown(1.05f);
        }
    }

    // ── Flexibility: precision (fizzle / cooldown) trade ─────────────────

    private static void applyFlexibility(WandStats.Builder b, @Nullable WandFlexibility flex) {
        if (flex == null) return;
        switch (flex) {
            case UNYIELDING -> b.addFizzle(-0.03f).mulDamage(1.04f);
            case RIGID -> b.addFizzle(-0.02f).mulDamage(1.03f);
            case PLIANT -> b.addFizzle(-0.01f);
            case SUPPLE -> b.mulCooldown(0.95f);
            case SPRINGY -> b.addFizzle(0.02f).mulCooldown(0.92f);
        }
    }

    /**
     * The core's id, mirroring {@link #resolveWoodId}: the legacy enum component still wins, converted
     * to an {@code Identifier} so the datapack lookup has something to key on.
     *
     * <p>Converted through {@link WandCore#getDefinitionPath()} rather than
     * {@code getSerializedName()}. The two differ for exactly one core — the enum persists Thestral as
     * {@code thestral_tail} while its definition is {@code thestral_tail_hair} — and taking the
     * serialized form here made that lookup miss, which cost every legacy Thestral wand its core
     * contribution. The enum fallback used to hide it; nothing does now.
     */
    @Nullable
    private static Identifier resolveCoreId(ItemStack wandStack) {
        WandCore legacy = wandStack.get(ModDataComponents.WAND_CORE.get());
        if (legacy != null) {
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, legacy.getDefinitionPath());
        }
        return WandComponents.getCore(wandStack);
    }

    /**
     * The legacy enum component still wins over the modern {@code Identifier} one, exactly as it did
     * when wood was resolved to an enum. Stacks written before that migration carry only the legacy
     * component, and dropping this branch would silently make them neutral.
     */
    @Nullable
    private static Identifier resolveWoodId(ItemStack wandStack) {
        WandWood legacy = wandStack.get(ModDataComponents.WAND_WOOD.get());
        if (legacy != null) {
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, legacy.getSerializedName());
        }
        return WandComponents.getWood(wandStack);
    }

    private static WandLength resolveLength(ItemStack wandStack) {
        WandLength legacy = wandStack.get(ModDataComponents.WAND_LENGTH.get());
        if (legacy != null) {
            return legacy;
        }
        Float inches = WandComponents.getLength(wandStack);
        if (inches == null) {
            return null;
        }
        if (inches <= 10.0f) return WandLength.SHORT;
        if (inches <= 12.0f) return WandLength.MEDIUM;
        if (inches >= 14.0f) return WandLength.LONG;
        return WandLength.STANDARD;
    }

    private static WandFlexibility resolveFlexibility(ItemStack wandStack) {
        WandFlexibility legacy = wandStack.get(ModDataComponents.WAND_FLEXIBILITY.get());
        if (legacy != null) {
            return legacy;
        }
        return WandComponents.getFlexibility(wandStack);
    }
}
