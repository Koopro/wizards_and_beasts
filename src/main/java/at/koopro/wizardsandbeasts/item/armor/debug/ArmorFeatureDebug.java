package at.koopro.wizardsandbeasts.item.armor.debug;

import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.item.armor.WizardArmorItem;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What this wizard is wearing, and whether the set is complete.
 *
 * <p>There is no armour attachment to read: a worn piece is an {@link ItemStack} in an equipment
 * slot, and everything the mod does with robes is derived from that. So this is a scan of the four
 * armour slots, reporting for each whether the piece is one of the mod's and what it was built from.
 */
@NullMarked
public final class ArmorFeatureDebug implements FeatureDebugSection {

    private static final EquipmentSlot[] ARMOUR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    @Override
    public String id() {
        return "armor";
    }

    @Override
    public String title() {
        return "Worn Armour";
    }

    @Override
    public String summary() {
        return "The four armour slots: which pieces are the mod's, and their material and type.";
    }

    @Override
    public @Nullable Module module() {
        return Module.ARTEFACTS;
    }

    @Override
    public void append(DebugReport report, ServerPlayer target, Detail detail) {
        int wizardPieces = 0;
        for (EquipmentSlot slot : ARMOUR_SLOTS) {
            if (target.getItemBySlot(slot).getItem() instanceof WizardArmorItem) {
                wizardPieces++;
            }
        }
        report.state("  wizarding pieces worn", wizardPieces + " / 4",
                wizardPieces == 4 ? ChatPalette.OK : ChatPalette.MUTED);
        if (detail == Detail.BRIEF) {
            return;
        }

        for (EquipmentSlot slot : ARMOUR_SLOTS) {
            ItemStack worn = target.getItemBySlot(slot);
            if (worn.isEmpty()) {
                report.row("  " + slot.getName(), "(empty)");
                continue;
            }
            report.row("  " + slot.getName(), worn.getHoverName().getString());
            report.row("    item", BuiltInRegistries.ITEM.getKey(worn.getItem()).toString());
            if (worn.getItem() instanceof WizardArmorItem wizardArmour) {
                report.row("    type", String.valueOf(wizardArmour.armorType()));
                report.row("    material", String.valueOf(wizardArmour.material()));
            }
            if (worn.isDamageableItem()) {
                report.bar("    durability",
                        1f - (float) worn.getDamageValue() / Math.max(1, worn.getMaxDamage()));
            }
        }
    }
}
