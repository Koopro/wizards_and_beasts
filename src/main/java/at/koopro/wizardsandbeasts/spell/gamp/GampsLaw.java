package at.koopro.wizardsandbeasts.spell.gamp;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.spell.cast.CastContext;
import at.koopro.wizardsandbeasts.spell.def.SpellDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class GampsLaw {
    private static final TagKey<Item> CANNOT_CONJURE_FOOD = ItemTags.create(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "cannot_conjure/food"));
    private static final TagKey<Item> CANNOT_CONJURE_CURRENCY = ItemTags.create(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "cannot_conjure/currency"));

    private GampsLaw() {}

    public record Violation(GampDomain domain, EnforcementType enforcement, Component loreMessage) {
        public boolean isHardReject() {
            return enforcement == EnforcementType.HARD_REJECT;
        }
    }

    public static @Nullable Violation validate(CastContext ctx) {
        SpellDefinition definition = ctx.definition();
        if (definition == null) {
            return null;
        }

        for (GampDomain domain : definition.gampDomains()) {
            if (isDeclaredViolation(domain, definition, ctx)) {
                return new Violation(domain, domain.enforcement(), domain.loreMessage());
            }
        }

        ItemStack produced = definition.previewProducedItem(ctx);
        if (produced != null && !produced.isEmpty()) {
            if (produced.is(CANNOT_CONJURE_FOOD)) {
                return new Violation(
                        GampDomain.FOOD_CONJURATION,
                        EnforcementType.SOFT_PENALTY,
                        GampDomain.FOOD_CONJURATION.loreMessage());
            }
            if (produced.is(CANNOT_CONJURE_CURRENCY)) {
                return new Violation(
                        GampDomain.CURRENCY_CONJURATION,
                        EnforcementType.SOFT_PENALTY,
                        GampDomain.CURRENCY_CONJURATION.loreMessage());
            }
        }

        return null;
    }

    private static boolean isDeclaredViolation(GampDomain domain, SpellDefinition definition, CastContext ctx) {
        return switch (domain) {
            case FOOD_CONJURATION -> definition.wouldConjureFood(ctx);
            case CURRENCY_CONJURATION -> definition.wouldConjureCurrency(ctx);
            case LIFE_CREATION -> definition.wouldCreateLife(ctx);
            case LOVE_CONJURATION -> definition.wouldConjureLove(ctx);
            case INFORMATION_GENESIS -> definition.wouldGenesisInformation(ctx);
        };
    }
}
