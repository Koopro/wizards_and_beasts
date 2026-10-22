package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.item.wand.WandCoreMaterialItem;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.cast.WandStats;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import at.koopro.wizardsandbeasts.wand.recipe.WandmakingRecipe;
import at.koopro.wizardsandbeasts.wand.rules.WandRulesService;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * Wandmaking (Phase 4): the cores that were dead or plain items now go into the bench and come out as wands that
 * carry their character (2026-10-22).
 */
public final class WandmakingTests {

    private static final Identifier HOLLY = Identifier.fromNamespaceAndPath("wizards_and_beasts", "holly");

    private WandmakingTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("wandmaking_rare_and_imported_cores_make_wands",
                "wandmaking: rougarou hair, White River Monster spine and the three imported cores are bench cores "
                        + "with a recipe, and the wand made carries the core",
                WandmakingTests::rareCoresMakeWands);
    }

    private static void rareCoresMakeWands(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var registries = helper.getLevel().registryAccess();
        for (var item : List.of(WandItemRegistry.ROUGAROU_HAIR, WandItemRegistry.WHITE_RIVER_MONSTER_SPINE,
                WandItemRegistry.THUNDERBIRD_TAIL_FEATHER, WandItemRegistry.WAMPUS_CAT_HAIR, WandItemRegistry.TROLL_WHISKER)) {
            ItemStack material = new ItemStack(item.get());
            check(helper, WandCoreMaterialItem.isBenchCore(material), () -> item.getId() + " is not a bench core");
            Identifier core = item.getId();
            Optional<WandmakingRecipe> recipe = WandRulesService.makeable(server, HOLLY, core);
            check(helper, recipe.isPresent(), () -> "no holly recipe for " + core);
            ItemStack wand = recipe.get().createWand(WandFlexibility.PLIANT, 11.0f);
            check(helper, core.equals(WandComponents.getCore(wand)), () -> "the wand made does not carry " + core);
        }

        ItemStack rougarou = WandRulesService.makeable(server, HOLLY, WandItemRegistry.ROUGAROU_HAIR.getId()).orElseThrow()
                .createWand(WandFlexibility.PLIANT, 11.0f);
        ItemStack unicorn = WandRulesService.makeable(server, HOLLY, WandItemRegistry.UNICORN_HAIR.getId()).orElseThrow()
                .createWand(WandFlexibility.PLIANT, 11.0f);
        WandStats darkWand = WandStatsResolver.resolve(rougarou, registries);
        WandStats lightWand = WandStatsResolver.resolve(unicorn, registries);
        float dark = darkWand.categoryDamageBonus().getOrDefault(SpellCategory.DARK_ARTS, 0f);
        float light = lightWand.categoryDamageBonus().getOrDefault(SpellCategory.DARK_ARTS, 0f);
        check(helper, dark > 0.1f && light < 0f, () -> "Dark Arts bonus: rougarou " + dark + ", unicorn " + light);
        check(helper, darkWand.fizzleChance() > lightWand.fizzleChance(),
                () -> "rougarou hair should misfire more than unicorn hair: " + darkWand.fizzleChance() + " vs "
                        + lightWand.fizzleChance());
        helper.succeed();
    }
}
