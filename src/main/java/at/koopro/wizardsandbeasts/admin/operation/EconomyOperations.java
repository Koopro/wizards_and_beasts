package at.koopro.wizardsandbeasts.admin.operation;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.location.Locations;
import at.koopro.wizardsandbeasts.market.MarketBook;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Economy operations. {@code economy_market_reset <location|all>} (destructive): forgets recent trade at one market
 * or every market, restoring list prices for everyone — moved here from the command so the Markets report's button
 * and {@code /wandb admin economy market reset} run the same audited operation.
 */
@NullMarked
public final class EconomyOperations {

    public static final String MARKET_RESET = "economy_market_reset";

    private EconomyOperations() {}

    static void registerAll() {
        AdminOperations.register(new AdminOperation() {
            @Override
            public String id() {
                return MARKET_RESET;
            }

            @Override
            public AdminCategory section() {
                return AdminCategory.ECONOMY;
            }

            @Override
            public boolean destructive() {
                return true;
            }

            @Override
            public @Nullable String validate(Request request) {
                if (request.subject().equals("all")) {
                    return null;
                }
                Identifier id = Identifier.tryParse(WizardsAndBeastsMod.MODID + ":" + request.subject());
                var location = id == null ? null : Locations.get(id);
                return location == null || location.market().isEmpty() ? "no market at " + request.subject() : null;
            }

            @Override
            public Outcome execute(Request request) {
                String prefix = request.subject().equals("all") ? "" : WizardsAndBeastsMod.MODID + ":" + request.subject() + "|";
                int cleared = MarketBook.get(request.server()).reset(prefix);
                return Outcome.ok("market pressure cleared on " + cleared + " good(s) at " + request.subject());
            }
        });
    }
}
