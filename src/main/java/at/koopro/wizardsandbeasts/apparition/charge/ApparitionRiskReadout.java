package at.koopro.wizardsandbeasts.apparition.charge;

import at.koopro.wizardsandbeasts.apparition.splinch.SplinchResolver;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * What is working against a wizard mid-hold, said while they can still do something about it.
 *
 * <p>The splinch ladder is deterministic, which is what makes it fair — but only if the player can see its inputs.
 * Moving, a full pack, hunger, water and the lack of a licence each tighten the window, and none of them showed
 * anywhere until the splinch. This reads the same {@link Destabilization} the release will be judged on, on the
 * server, and puts it on the action bar a few times a second: "steady", or what is wrong and how much slack is left.
 */
@NullMarked
public final class ApparitionRiskReadout {

    /** Ticks between readouts during a hold. */
    public static final int INTERVAL_TICKS = 10;

    private ApparitionRiskReadout() {}

    public static void show(ServerPlayer player, Destabilization destabilization) {
        PlayerFeedback.actionBar(player, line(destabilization));
    }

    /** The readout for {@code destabilization}. */
    public static Component line(Destabilization destabilization) {
        List<String> factors = factors(destabilization);
        if (factors.isEmpty()) {
            return Component.translatable("apparition.wizards_and_beasts.risk.steady").withStyle(ChatFormatting.GREEN);
        }
        MutableComponent list = Component.empty();
        for (int i = 0; i < factors.size(); i++) {
            if (i > 0) {
                list.append(Component.literal(", "));
            }
            list.append(Component.translatable("apparition.wizards_and_beasts.risk.factor." + factors.get(i)));
        }
        int margin = SplinchResolver.cleanMargin(destabilization);
        if (margin < 0) {
            return Component.translatable("apparition.wizards_and_beasts.risk.doomed", list)
                    .withStyle(ChatFormatting.RED);
        }
        return Component.translatable("apparition.wizards_and_beasts.risk.unsteady", list, margin)
                .withStyle(ChatFormatting.GOLD);
    }

    /** The factor ids in effect, in the ladder's order. */
    public static List<String> factors(Destabilization d) {
        List<String> out = new ArrayList<>();
        if (d.damageInstances() > 0) {
            out.add("hurt");
        }
        if (d.movingFast()) {
            out.add("moving");
        }
        if (d.submerged()) {
            out.add("submerged");
        }
        if (d.encumbered()) {
            out.add("encumbered");
        }
        if (d.famished()) {
            out.add("famished");
        }
        if (d.sideAlong()) {
            out.add("side_along");
        }
        if (!d.licensed()) {
            out.add("unlicensed");
        }
        return out;
    }
}
