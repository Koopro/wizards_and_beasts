package at.koopro.wizardsandbeasts.datagen;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModCreatures;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.loot.EntityLootSubProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Entity drops for the data-configured creature roster ({@link ModCreatures#MANIFEST}) plus the
 * bespoke beasts with an obvious material. Beings and non-beings (centaur, merperson, boggart,
 * obscurus, ...) get a deliberately empty table so they are covered without dropping anything.
 * Niffler drops stay hand-authored in {@code data/wizards_and_beasts/loot_table/entities/}.
 */
public class ModEntityLootTableProvider extends EntityLootSubProvider {

    private final List<EntityType<?>> covered = new ArrayList<>();

    public ModEntityLootTableProvider(HolderLookup.Provider registries) {
        super(FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    public void generate() {
        for (var entry : ModCreatures.ENTITIES.entrySet()) {
            addCovered(entry.getValue().get(), tableFor(entry.getKey()));
        }
        addCovered(ModEntities.MOONCALF.get(), simple(modItem("mooncalf_dung"), 0, 2));
        addCovered(ModEntities.THESTRAL.get(),
                withRare(Items.LEATHER, 0, 2, modItem("thestral_tail_hair"), 0.35f));
        addCovered(ModEntities.PHOENIX.get(),
                withRare(Items.FEATHER, 1, 2, modItem("phoenix_feather"), 0.50f));
        addCovered(ModEntities.AUGUREY.get(), simple(Items.FEATHER, 1, 2));
        addCovered(ModEntities.BOWTRUCKLE.get(), simple(Items.STICK, 0, 2));
        addCovered(ModEntities.RUNESPOOR.get(), simple(Items.SPIDER_EYE, 0, 2));
        addCovered(ModEntities.HIDEBEHIND.get(),
                two(modItem("hidebehind_shadow_essence"), 0, 2, modItem("hidebehind_claw"), 0, 1));
    }

    private LootTable.Builder tableFor(String id) {
        if (ModCreatures.DRAGON_IDS.contains(id)) {
            return withRare(Items.LEATHER, 1, 3, modItem("dragon_heartstring"), 0.30f);
        }
        return switch (id) {
            // signature wand/potion materials
            case "unicorn" -> simple(modItem("unicorn_hair"), 1, 2);
            case "demiguise" -> simple(modItem("demiguise_hair"), 1, 2);
            case "erumpent" -> withRare(Items.LEATHER, 1, 2, modItem("erumpent_horn"), 0.50f);
            case "occamy" -> withRare(Items.FEATHER, 1, 2, modItem("occamy_eggshell"), 0.35f);
            case "leprechaun" -> simple(modItem("leprechaun_gold"), 2, 5);
            // fire-aligned
            case "salamander" -> simple(Items.BLAZE_POWDER, 0, 2);
            case "ashwinder" -> simple(Items.BLAZE_POWDER, 0, 1);
            case "fire_crab" -> withRare(Items.BLAZE_POWDER, 0, 1, Items.EMERALD, 0.20f);
            // venomous / arthropod
            case "acromantula" -> two(Items.STRING, 1, 3, Items.SPIDER_EYE, 0, 1);
            case "basilisk" -> two(Items.BONE, 1, 3, Items.SPIDER_EYE, 0, 2);
            // Phase 3 additions
            case "ghoul" -> two(modItem("ghoul_slime"), 0, 2, Items.BONE, 0, 1);
            case "golden_snidget" -> withRare(Items.FEATHER, 0, 1, modItem("golden_snidget_feather"), 0.40f);
            case "granian" -> two(modItem("granian_hair"), 0, 2, Items.LEATHER, 0, 2);
            case "horned_serpent" -> withRare(Items.PRISMARINE_SHARD, 0, 2, modItem("horned_serpent_gem"), 0.25f);
            case "pukwudgie" -> two(Items.ARROW, 0, 2, modItem("pukwudgie_venom_sac"), 0, 1);
            case "yeti" -> two(modItem("yeti_fur"), 0, 2, Items.BONE, 0, 1);
            case "rougarou" -> two(modItem("rougarou_hair"), 0, 1, Items.LEATHER, 0, 2);
            // Wand-core bearers: these cores had no survival source, leaving the wandmaker's bench
            // usable only with unicorn hair.
            case "thunderbird" -> withRare(Items.FEATHER, 1, 2, modItem("thunderbird_tail_feather"), 0.35f);
            case "wampus_cat" -> withRare(Items.LEATHER, 0, 2, modItem("wampus_cat_hair"), 0.40f);
            case "troll" -> withRare(Items.BONE, 1, 2, modItem("troll_whisker"), 0.40f);
            case "matagot" -> two(modItem("matagot_essence"), 0, 1, Items.PHANTOM_MEMBRANE, 0, 1);
            case "bundimun" -> simple(Items.SLIME_BALL, 0, 2);
            case "lobalug" -> withRare(Items.INK_SAC, 0, 2, modItem("gillyweed"), 0.35f);
            // aquatic. Gillyweed is a water plant with no world feature behind it, so the merfolk-water
            // beasts carry it — without a source the item was creative-only.
            case "kelpie", "kappa" -> withRare(Items.KELP, 1, 3, modItem("gillyweed"), 0.25f);
            case "grindylow" -> two(Items.KELP, 0, 2, modItem("gillyweed"), 0, 1);
            case "plimpy", "ramora" -> simple(Items.COD, 1, 1);
            case "shrake" -> simple(Items.PRISMARINE_SHARD, 0, 1);
            case "sea_serpent" -> simple(Items.PRISMARINE_SHARD, 1, 3);
            case "hippocampus" -> simple(Items.PRISMARINE_SHARD, 0, 2);
            // birds
            case "fwooper", "jobberknoll", "diricawl" -> simple(Items.FEATHER, 1, 2);
            // winged mounts and raptors
            case "abraxan", "aethonan" -> two(Items.LEATHER, 1, 2, Items.FEATHER, 0, 2);
            case "hippogriff", "griffin", "snallygaster" ->
                    two(Items.FEATHER, 1, 2, Items.LEATHER, 0, 2);
            // shadow-stuff
            case "lethifold" -> simple(Items.PHANTOM_MEMBRANE, 0, 2);
            case "swooping_evil" -> simple(Items.PHANTOM_MEMBRANE, 0, 1);
            // big hides
            case "graphorn", "nundu", "tebo", "reem", "zouwu", "hodag", "quintaped",
                 "manticore", "chimaera", "sphinx" -> simple(Items.LEATHER, 1, 3);
            case "qilin" -> simple(Items.GOLD_NUGGET, 0, 1);
            // small critters
            case "kneazle", "crup", "jarvey", "knarl", "moke", "nogtail", "clabbert",
                 "mackled_malaclaw" -> simple(Items.RABBIT_HIDE, 0, 1);
            case "puffskein", "pygmy_puff", "billywig", "doxy", "chizpurfle", "glumbumble" ->
                    simple(Items.STRING, 0, 1);
            case "fairy" -> simple(Items.GLOWSTONE_DUST, 0, 1);
            case "flobberworm", "murtlap", "dugbog" -> simple(Items.SLIME_BALL, 0, 2);
            case "horklump" -> simple(Items.BROWN_MUSHROOM, 1, 2);
            // beings and non-beings drop nothing
            default -> LootTable.lootTable();
        };
    }

    private void addCovered(EntityType<?> type, LootTable.Builder builder) {
        covered.add(type);
        add(type, builder);
    }

    @Override
    protected Stream<EntityType<?>> getKnownEntityTypes() {
        return covered.stream();
    }

    private static LootTable.Builder simple(Item item, int min, int max) {
        return LootTable.lootTable().withPool(pool(item, min, max));
    }

    private static LootTable.Builder two(Item first, int min1, int max1, Item second, int min2, int max2) {
        return LootTable.lootTable().withPool(pool(first, min1, max1)).withPool(pool(second, min2, max2));
    }

    private static LootTable.Builder withRare(Item common, int min, int max, Item rare, float chance) {
        return LootTable.lootTable()
                .withPool(pool(common, min, max))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0f))
                        .when(LootItemRandomChanceCondition.randomChance(chance))
                        .add(LootItem.lootTableItem(rare)));
    }

    private static LootPool.Builder pool(Item item, int min, int max) {
        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0f))
                .add(LootItem.lootTableItem(item)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max))));
    }

    private static Item modItem(String path) {
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path));
    }
}
