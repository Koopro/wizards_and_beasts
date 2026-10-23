package at.koopro.wizardsandbeasts.loot;

import at.koopro.wizardsandbeasts.brew.BrewingRecipes;
import at.koopro.wizardsandbeasts.brew.knowledge.RecipeSources;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Adds a loose potion recipe page to a chest: one recipe drawn from {@code brew_recipe_sources/recipe_pages.json}
 * (everything but the basics), with {@code chance}. The brewing counterpart of {@link SpellbookLootModifier}: the
 * world is where recipes nobody sells are found, and the pool is data, so a pack decides what a library can hold.
 */
@NullMarked
public class RecipePageLootModifier extends LootModifier {

    public static final MapCodec<RecipePageLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).and(
                    Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(m -> m.chance)
            ).apply(instance, RecipePageLootModifier::new));

    private final float chance;

    public RecipePageLootModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (chance < 1.0f && context.getRandom().nextFloat() >= chance) {
            return generatedLoot;
        }
        List<String> pool = RecipeSources.recipes(RecipeSources.PAGES).stream()
                .filter(id -> BrewingRecipes.byId(id) != null).toList();
        if (pool.isEmpty()) {
            return generatedLoot;
        }
        ItemStack page = new ItemStack(MiscItemRegistry.POTION_RECIPE_PAGE.get());
        page.set(ModDataComponents.BREW_RECIPES.get(), List.of(pool.get(context.getRandom().nextInt(pool.size()))));
        generatedLoot.add(page);
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
