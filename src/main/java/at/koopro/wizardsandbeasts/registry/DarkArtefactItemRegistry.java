package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.item.cloak.CloakItem;
import at.koopro.wizardsandbeasts.item.consumable.PhilosophersStoneItem;
import at.koopro.wizardsandbeasts.item.darkartefact.GauntRingItem;
import at.koopro.wizardsandbeasts.item.darkartefact.HufflepuffsCupItem;
import at.koopro.wizardsandbeasts.item.darkartefact.RavenclawsDiademItem;
import at.koopro.wizardsandbeasts.item.darkartefact.RiddlesDiaryItem;
import at.koopro.wizardsandbeasts.item.darkartefact.SlytherinsLocketItem;
import at.koopro.wizardsandbeasts.item.hallow.ResurrectionStoneItem;
import at.koopro.wizardsandbeasts.item.trunk.EnchantedTrunkItem;
import at.koopro.wizardsandbeasts.item.trunk.MoodysTrunkItem;
import at.koopro.wizardsandbeasts.trunk.TrunkTier;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.registries.DeferredItem;

public final class DarkArtefactItemRegistry {

    // ── Horcrux Vessels ──────────────────────────────────────────

    public static final DeferredItem<RiddlesDiaryItem> RIDDLES_DIARY =
            ModItems.ITEMS.registerItem("riddles_diary", props -> new RiddlesDiaryItem(props.stacksTo(1)));

    public static final DeferredItem<GauntRingItem> MARVOLO_GAUNTS_RING =
            ModItems.ITEMS.registerItem("marvolo_gaunts_ring", props -> new GauntRingItem(props.stacksTo(1)));

    public static final DeferredItem<SlytherinsLocketItem> SLYTHERINS_LOCKET =
            ModItems.ITEMS.registerItem("slytherins_locket", props -> new SlytherinsLocketItem(props.stacksTo(1)));

    public static final DeferredItem<HufflepuffsCupItem> HUFFLEPUFFS_CUP =
            ModItems.ITEMS.registerItem("hufflepuffs_cup", props -> new HufflepuffsCupItem(props.stacksTo(1)));

    public static final DeferredItem<RavenclawsDiademItem> RAVENCLAWS_DIADEM =
            ModItems.ITEMS.registerItem("ravenclaws_diadem", props -> new RavenclawsDiademItem(props.stacksTo(1)));

    // ── Unique Artefacts ─────────────────────────────────────────

    public static final DeferredItem<PhilosophersStoneItem> PHILOSOPHERS_STONE =
            ModItems.ITEMS.registerItem("philosophers_stone", props -> new PhilosophersStoneItem(props.stacksTo(1)));

    // ── Deathly Hallows ──────────────────────────────────────────

    public static final DeferredItem<ResurrectionStoneItem> RESURRECTION_STONE =
            ModItems.ITEMS.registerItem("resurrection_stone", props -> new ResurrectionStoneItem(props.stacksTo(1)));

    public static final DeferredItem<CloakItem> INVISIBILITY_CLOAK =
            ModItems.ITEMS.registerItem("invisibility_cloak",
                    props -> new CloakItem(props.stacksTo(1).equippable(EquipmentSlot.CHEST), false));
    public static final DeferredItem<CloakItem> DEATHLY_HALLOW_CLOAK =
            ModItems.ITEMS.registerItem("deathly_hallow_cloak",
                    props -> new CloakItem(props.stacksTo(1).equippable(EquipmentSlot.CHEST), true));

    // ── Containers ───────────────────────────────────────────────

    public static final DeferredItem<EnchantedTrunkItem> ENCHANTED_TRUNK =
            ModItems.ITEMS.registerItem("enchanted_trunk", props -> new EnchantedTrunkItem(TrunkTier.TIER_1, props.stacksTo(1)));
    public static final DeferredItem<EnchantedTrunkItem> EXPANDED_TRUNK =
            ModItems.ITEMS.registerItem("expanded_trunk", props -> new EnchantedTrunkItem(TrunkTier.TIER_2, props.stacksTo(1)));
    public static final DeferredItem<EnchantedTrunkItem> MASTERS_TRUNK =
            ModItems.ITEMS.registerItem("masters_trunk", props -> new EnchantedTrunkItem(TrunkTier.TIER_3, props.stacksTo(1)));

    public static final DeferredItem<MoodysTrunkItem> MOODYS_TRUNK =
            ModItems.ITEMS.registerItem("moodys_trunk", props -> new MoodysTrunkItem(props.stacksTo(1)));

    private DarkArtefactItemRegistry() {}
}
