package at.koopro.wizardsandbeasts.equipment.debug;

import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.equipment.EquipmentAPI;
import at.koopro.wizardsandbeasts.equipment.EquipmentType;
import at.koopro.wizardsandbeasts.equipment.EquipmentVisibility;
import at.koopro.wizardsandbeasts.equipment.WearableDefinition;
import at.koopro.wizardsandbeasts.item.armor.RobeHood;
import at.koopro.wizardsandbeasts.module.Module;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Everything a wizard is wearing, as the server sees it: vanilla armour, every W&amp;B equipment type,
 * and which armour pieces the outfit hides.
 *
 * <p>{@code /wandb debug feature equipment}, and the over-player debug panel. The visibility shown is
 * computed on the server from the real Curios inventory — the same rule the client applies to the
 * render state, minus cosmetic slots — so a mismatch with what is on screen points at sync, not at
 * the rule.
 */
@NullMarked
public final class EquipmentFeatureDebug implements FeatureDebugSection {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    @Override
    public String id() {
        return "equipment";
    }

    @Override
    public String title() {
        return "Equipment";
    }

    @Override
    public String summary() {
        return "Vanilla armour, Curios-worn robes/cloaks/headwear/jewellery, and armour hidden by clothing.";
    }

    @Override
    public @Nullable Module module() {
        return null;
    }

    @Override
    public void append(DebugReport report, ServerPlayer target, Detail detail) {
        if (!EquipmentAPI.canWear(target)) {
            report.warn("  no Curios inventory — Curios slots are not assigned to this entity");
            return;
        }
        EquipmentAPI.Snapshot snapshot = EquipmentAPI.snapshot(target);
        EquipmentVisibility.Result visibility = snapshot.visibility();

        report.section("  Vanilla armour");
        for (Map.Entry<EquipmentSlot, ItemStack> entry : snapshot.armor().entrySet()) {
            ItemStack worn = entry.getValue();
            report.row("    " + entry.getKey().getName(), describe(worn));
            if (detail == Detail.FULL && worn.isDamageableItem()) {
                report.bar("      durability",
                        1f - (float) worn.getDamageValue() / Math.max(1, worn.getMaxDamage()));
            }
        }

        report.section("  Curios");
        for (EquipmentType type : EquipmentType.values()) {
            List<EquipmentAPI.WornPiece> pieces = snapshot.of(type);
            String label = "    " + type.getSerializedName() + " [" + type.slotId() + "]";
            if (pieces.isEmpty()) {
                if (detail == Detail.FULL) {
                    report.row(label, "-");
                }
                continue;
            }
            for (EquipmentAPI.WornPiece piece : pieces) {
                report.row(label + "#" + piece.index(), describe(piece.stack()));
                if (detail == Detail.FULL && piece.definition() != null) {
                    appendDefinition(report, piece.definition(), piece.stack());
                }
            }
        }
        if (detail == Detail.FULL) {
            report.row("    ring slots", EquipmentAPI.slotCount(target, EquipmentType.RING));
            long foreign = snapshot.worn().stream().filter(p -> p.definition() == null).count();
            if (foreign > 0) {
                report.row("    other mods' curios", foreign);
            }
        }

        report.section("  Visual hiding");
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            boolean visible = visibility.isArmorVisible(slot);
            report.state("    " + slot.getName(), visible ? "visible" : "hidden",
                    visible ? ChatPalette.OK : ChatPalette.MUTED);
        }
        report.flag("    concealed (Deathly Hallow)", visibility.concealed());
    }

    private static void appendDefinition(DebugReport report, WearableDefinition definition, ItemStack stack) {
        report.row("      layer", definition.layer().getSerializedName());
        if (!definition.hiddenSlots(RobeHood.isUp(stack)).isEmpty()) {
            report.row("      hides", definition.hiddenSlots(RobeHood.isUp(stack)).toString());
        }
        definition.faction().ifPresent(faction -> report.row("      faction", faction));
        for (WearableDefinition.Modifier modifier : definition.attributes()) {
            report.row("      grants", modifier.attribute().getRegisteredName() + " "
                    + modifier.amount() + " (" + modifier.operation().getSerializedName() + ")");
        }
    }

    private static String describe(ItemStack stack) {
        if (stack.isEmpty()) {
            return "(empty)";
        }
        return stack.getHoverName().getString() + "  " + BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
}
