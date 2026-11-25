package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.registry.WandItemRegistry;
import at.koopro.wizardsandbeasts.spell.cast.ModifierStack;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.wand.WandCastLines;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.cast.WandCastContribution;
import at.koopro.wizardsandbeasts.wand.cast.WandStats;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import at.koopro.wizardsandbeasts.wand.rules.WandRulesService;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * B1 (systems audit 2026-11-25): what the wand tooltip says a wand adds to a spell is what a cast applies.
 *
 * <p>Both sides read {@link WandStatsResolver#resolve} and {@link WandCastContribution}; this drives a real
 * wand through the cast's own {@link WandCastContribution#applyTo} onto a fresh {@link ModifierStack} and reads
 * the factors back from the stack's provenance, then checks each against the percentage the tooltip prints.
 */
public final class WandTooltipParityTests {

    private static final Identifier ELDER = Identifier.fromNamespaceAndPath("wizards_and_beasts", "elder");
    private static final Identifier YEW = Identifier.fromNamespaceAndPath("wizards_and_beasts", "yew");

    private WandTooltipParityTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("wand_tooltip_matches_the_cast", "wand: tooltip figures (damage, stability, cooldown, misfire) "
                + "are the factors a cast applies", WandTooltipParityTests::tooltipMatchesTheCast);
    }

    private static void tooltipMatchesTheCast(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var registries = helper.getLevel().registryAccess();
        Spell stupefy = Spells.byId("wizards_and_beasts:stupefy");
        check(helper, stupefy != null, () -> "stupefy is not registered");

        int checked = 0;
        for (Identifier wood : List.of(ELDER, YEW)) {
            for (var core : List.of(WandItemRegistry.PHOENIX_FEATHER, WandItemRegistry.UNICORN_HAIR)) {
                var recipe = WandRulesService.makeable(server, wood, core.getId());
                if (recipe.isEmpty()) {
                    continue;
                }
                checked++;
                ItemStack wand = recipe.get().createWand(WandFlexibility.PLIANT, 11.0f);
                // A cracked wand, so the stability row has something to say.
                wand.set(WandComponents.WAND_INTEGRITY.get(), 0.75f);

                WandStats stats = WandStatsResolver.resolve(wand, registries);
                float stability = WandCastContribution.stability(wand, registries);
                ModifierStack cast = new ModifierStack();
                WandCastContribution.applyTo(cast, wand, stats, stupefy, registries);

                List<WandCastLines.Line> lines = WandCastLines.lines(stats, stability);
                String label = wood.getPath() + "/" + core.getId().getPath();

                float condition = factor(cast, WandCastContribution.CONDITION_SOURCE + ":damage*x");
                float wandDamage = factor(cast, "wand:damage*x");
                float cooldown = factor(cast, "wand:cooldown*x");
                float misfire = added(cast, "wand:misfire+");
                float categoryBonus = stats.categoryDamageBonus().getOrDefault(stupefy.getCategory(), 0f);

                expect(helper, label, lines, "wandcraft.tooltip.cast.stability", percent(condition - 1f));
                // The cast folds the category bonus into one damage factor; the tooltip prints the two apart.
                expect(helper, label, lines, "wandcraft.tooltip.cast.damage",
                        percent(wandDamage / (1f + categoryBonus) - 1f));
                expect(helper, label, lines, "wandcraft.tooltip.cast.cooldown", percent(cooldown - 1f));
                expect(helper, label, lines, "wandcraft.tooltip.cast.misfire", percent(misfire));
                check(helper, Math.abs(condition - 0.75f) < 1e-4,
                        () -> label + ": a 75% wand cast at x" + condition + " condition");
            }
        }
        int pairs = checked;
        check(helper, pairs >= 2, () -> "only " + pairs + " wood/core pairs had a recipe; the parity check ran on too few");
        helper.succeed();
    }

    /** The percentage the tooltip prints for {@code key}, or 0 when it omits the row. */
    private static void expect(GameTestHelper helper, String label, List<WandCastLines.Line> lines, String key,
                               int castPercent) {
        int shown = 0;
        for (WandCastLines.Line line : lines) {
            if (line.text().getContents() instanceof TranslatableContents t && t.getKey().equals(key)) {
                shown = Integer.parseInt(String.valueOf(t.getArgs()[0]).replace("%", "").replace("+", ""));
            }
        }
        int finalShown = shown;
        check(helper, finalShown == castPercent,
                () -> label + ": tooltip " + key + " says " + finalShown + "%, the cast applies " + castPercent + "%");
    }

    private static int percent(float delta) {
        return Math.round(delta * 100f);
    }

    private static float factor(ModifierStack stack, String prefix) {
        float f = 1f;
        for (String entry : stack.provenance()) {
            if (entry.startsWith(prefix)) {
                f *= Float.parseFloat(entry.substring(prefix.length()));
            }
        }
        return f;
    }

    private static float added(ModifierStack stack, String prefix) {
        float sum = 0f;
        for (String entry : stack.provenance()) {
            if (entry.startsWith(prefix)) {
                sum += Float.parseFloat(entry.substring(prefix.length()));
            }
        }
        return sum;
    }
}
