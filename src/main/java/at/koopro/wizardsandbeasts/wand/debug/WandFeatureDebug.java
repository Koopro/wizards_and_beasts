package at.koopro.wizardsandbeasts.wand.debug;

import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.customization.WandConfiguration;
import at.koopro.wizardsandbeasts.wand.customization.WandSlot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The wand in the player's hand: what it is made of, whose it is, and how well it is answering.
 *
 * <p>Allegiance is the field worth putting first. A wand below its bonding threshold casts at a
 * penalty and refuses some spells outright, and that reads to a player as "the mod is broken"
 * because nothing in the world says so. Printing the score against the master's UUID answers both
 * halves — whether it is bonded, and whether it is bonded to <em>them</em>.
 */
@NullMarked
public final class WandFeatureDebug implements FeatureDebugSection {

    @Override
    public String id() {
        return "wands";
    }

    @Override
    public String title() {
        return "Wand";
    }

    @Override
    public String summary() {
        return "Held wand: wood, core, flexibility, allegiance, integrity, fitted modules.";
    }

    @Override
    public @Nullable Module module() {
        return Module.WANDS;
    }

    @Override
    public void append(DebugReport report, ServerPlayer target, Detail detail) {
        ItemStack wand = findWand(target);
        if (wand == null) {
            report.row("  held wand", "none in either hand");
            return;
        }
        report.row("  item", wand.getHoverName().getString());
        report.row("  wood / core", WandComponents.getWood(wand) + " / " + WandComponents.getCore(wand));

        Optional<UUID> master = WandComponents.getMaster(wand);
        float allegiance = WandComponents.getAllegianceScore(wand);
        boolean ownedByTarget = master.map(target.getUUID()::equals).orElse(false);
        report.state("  master", master.map(id -> ownedByTarget ? "this player" : id.toString())
                        .orElse("unbonded"),
                ownedByTarget ? ChatPalette.OK : master.isPresent() ? ChatPalette.BAD : ChatPalette.MUTED);
        report.bar("  allegiance", allegiance);
        if (detail == Detail.BRIEF) {
            return;
        }

        report.row("  flexibility", String.valueOf(WandComponents.getFlexibility(wand)));
        Float length = WandComponents.getLength(wand);
        report.row("  length", length == null ? "(unset)" : String.format("%.1f\"", length));
        report.bar("  integrity", WandComponents.getIntegrity(wand));
        report.bar("  corruption", WandComponents.getCorruption(wand));
        report.row("  casts", WandComponents.getCastCount(wand));

        report.section("  modules");
        WandConfiguration configuration = wand.get(WandComponents.WAND_CONFIGURATION.get());
        if (configuration == null) {
            report.row("    —", "no configuration component (renders as the base wand)");
            return;
        }
        for (WandSlot slot : WandSlot.renderOrder()) {
            configuration.getModule(slot).ifPresentOrElse(
                    id -> report.row("    " + slot.slotId(), id.toString()),
                    () -> report.state("    " + slot.slotId(),
                            slot.isRequired() ? "EMPTY — required" : "empty",
                            slot.isRequired() ? ChatPalette.BAD : ChatPalette.MUTED));
        }
    }

    /**
     * The wand this dump is about.
     *
     * <p>Main hand first, then off hand. Not an inventory scan: which wand is <em>held</em> is the
     * one every cast reads, and a report about a spare in the backpack would answer a question the
     * caster is not asking.
     */
    private static @Nullable ItemStack findWand(ServerPlayer target) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = target.getItemInHand(hand);
            if (!held.isEmpty() && held.has(WandComponents.WAND_WOOD.get())) {
                return held;
            }
        }
        return null;
    }
}
