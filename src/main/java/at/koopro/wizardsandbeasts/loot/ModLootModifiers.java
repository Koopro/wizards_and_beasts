package at.koopro.wizardsandbeasts.loot;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Global loot modifier serializers. Modifier instances live under a namespace's {@code loot_modifiers}
 * directory — plural, as {@code LootModifierManager} hard-codes that folder name — and each one must also
 * be listed in {@code data/neoforge/loot_modifiers/global_loot_modifiers.json} or it is never loaded.
 */
public final class ModLootModifiers {

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> GLOBAL_LOOT_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, WizardsAndBeastsMod.MODID);

    public static final Supplier<MapCodec<AddItemLootModifier>> ADD_ITEM =
            GLOBAL_LOOT_MODIFIER_SERIALIZERS.register("add_item", () -> AddItemLootModifier.CODEC);

    private ModLootModifiers() {}
}
