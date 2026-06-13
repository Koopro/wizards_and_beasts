package at.koopro.wizardsandbeasts.wand;

import at.koopro.wizardsandbeasts.util.WandHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Thin display facade over the existing wand-use gate ({@link WandHelper#isWandBondedTo}).
 * Produces human-readable eligibility info for tooltips and the character sheet.
 *
 * <p>Side-safe: reads only network-synchronized data components from the stack — no
 * client-only APIs and no server round-trips.
 */
public final class WandEligibility {

    private WandEligibility() {}

    public record Result(
            boolean eligible,
            @Nullable Component reason,      // null if eligible
            @Nullable Component detailLine1, // allegiance score
            @Nullable Component detailLine2, // bond state
            @Nullable Component detailLine3  // integrity
    ) {}

    /**
     * Evaluate whether the given player can use the given wand stack.
     * Called on both sides — must not touch client-only APIs.
     */
    public static Result evaluate(@NonNull Player player, @NonNull ItemStack wandStack) {
        Optional<UUID> master = WandComponents.getMaster(wandStack);
        boolean eligible = WandHelper.isWandBondedTo(player, wandStack);

        Component reason = null;
        Component bondLine;
        if (eligible) {
            bondLine = Component.translatable("wandcraft.eligibility.detail.bond.you");
        } else if (master.isEmpty()) {
            reason = Component.translatable("wandcraft.eligibility.reason.not_attuned");
            bondLine = Component.translatable("wandcraft.eligibility.detail.bond.none");
        } else {
            reason = Component.translatable("wandcraft.eligibility.reason.wrong_master");
            bondLine = Component.translatable("wandcraft.eligibility.detail.bond.other");
        }

        Component allegianceLine = Component.translatable("wandcraft.eligibility.detail.allegiance",
                Math.round(WandComponents.getAllegianceScore(wandStack) * 100f));
        Component integrityLine = Component.translatable("wandcraft.eligibility.detail.integrity",
                Math.round(WandComponents.getIntegrity(wandStack) * 100f));

        return new Result(eligible, reason, allegianceLine, bondLine, integrityLine);
    }
}
