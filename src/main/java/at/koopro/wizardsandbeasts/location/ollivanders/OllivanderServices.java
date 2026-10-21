package at.koopro.wizardsandbeasts.location.ollivanders;

import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.location.LocationService;
import at.koopro.wizardsandbeasts.location.LocationServices;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.WandLoreNames;
import at.koopro.wizardsandbeasts.wand.allegiance.WandBondHistory;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.UUID;

/**
 * Ollivanders' counter services beyond the trial and the tending a plain visit already gives.
 *
 * <ul>
 *   <li><b>wand_evaluation</b> — the wandmaker reads the wand in hand: its make, its condition, how much work it has
 *       done, and its history of allegiance — whether it was won from another wizard, and whether someone has been
 *       beating its master lately (the one thing a wizard most needs to know about a wand and the tooltip never
 *       says).</li>
 * </ul>
 */
@NullMarked
public final class OllivanderServices {

    private OllivanderServices() {}

    public static void register(LocationServices.Sink sink) {
        sink.register("wand_evaluation", new Evaluation());
        sink.register("broom_servicing", new BroomServicing());
        sink.register("wand_commission", new WandCommission());
    }

    static final class Evaluation implements LocationService {

        @Override
        public Quote quote(ServerPlayer player, Use use) {
            if (!(player.getMainHandItem().getItem() instanceof WandItem)) {
                return Quote.no(Component.translatable("location.wizards_and_beasts.ollivanders.hold_wand"));
            }
            return Quote.yes(Component.translatable("location.wizards_and_beasts.price",
                    CurrencyHelper.formatFromKnuts(Use.billed() ? use.fee() : 0)));
        }

        @Override
        public boolean perform(ServerPlayer player, Use use) {
            ItemStack wand = player.getMainHandItem();
            if (!(wand.getItem() instanceof WandItem) || !use.charge(player, use.fee())) {
                return false;
            }
            UUID self = player.getUUID();
            String key = "location.wizards_and_beasts.ollivanders.evaluation.";
            player.sendSystemMessage(Component.translatable(key + "header").withStyle(ChatFormatting.GOLD));
            Float length = WandComponents.getLength(wand);
            WandFlexibility flexibility = WandComponents.getFlexibility(wand);
            player.sendSystemMessage(Component.translatable(key + "make",
                    WandLoreNames.wood(player.registryAccess(), WandComponents.getWood(wand)),
                    WandLoreNames.core(player.registryAccess(), WandComponents.getCore(wand)),
                    length == null ? "?" : String.format(Locale.ROOT, "%.1f", length),
                    flexibility == null ? Component.literal("?")
                            : Component.translatable("wandcraft.flexibility." + flexibility.getSerializedName())));
            player.sendSystemMessage(Component.translatable(key + "condition",
                    Math.round(WandComponents.getIntegrity(wand) * 100), WandComponents.getCastCount(wand)));
            WandBondHistory history = WandComponents.getBondHistory(wand);
            if (history.firstMaster() != null) {
                player.sendSystemMessage(Component.translatable(key + (self.equals(history.firstMaster())
                        ? "first_master_you" : "first_master_other")));
            }
            if (history.formerMaster() != null) {
                player.sendSystemMessage(Component.translatable(key + "won"));
            }
            if (history.challenger() != null && history.challengerWins() > 0) {
                player.sendSystemMessage(Component.translatable(key + "challenged", history.challengerWins())
                        .withStyle(ChatFormatting.RED));
            }
            PlayerFeedback.actionBar(player, Component.translatable(key + "done"));
            return true;
        }
    }
}
