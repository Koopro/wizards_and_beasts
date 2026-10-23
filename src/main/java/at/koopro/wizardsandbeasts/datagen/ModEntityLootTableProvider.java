package at.koopro.wizardsandbeasts.datagen;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModCreatures;
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
 * Entity drops for the data-configured creature roster ({@link ModCreatures#MANIFEST}) that nobody wrote by hand.
 * Beings and non-beings (centaur, merperson, boggart, obscurus, ...) get a deliberately empty table so they are
 * covered without dropping anything.
 *
 * <p>Every creature with a material worth guarding -- the dragons, unicorn, thestral, occamy, the wand-core bearers
 * -- has a hand-written table in {@code data/wizards_and_beasts/loot_table/entities/} instead, listed in
 * {@link HandWrittenLoot}; this provider writes nothing for those ids.
 */
public class ModEntityLootTableProvider extends EntityLootSubProvider {

    private final List<EntityType<?>> covered = new ArrayList<>();

    public ModEntityLootTableProvider(HolderLookup.Provider registries) {
        super(FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    public void generate() {
        for (var entry : ModCreatures.ENTITIES.entrySet()) {
            if (!HandWrittenLoot.ENTITY_TABLES.contains(entry.getKey())) {
                addCovered(entry.getValue().get(), tableFor(entry.getKey()));
            }
        }
    }

    private LootTable.Builder tableFor(String id) {
        return switch (id) {
            case "leprechaun" -> simple(modItem("leprechaun_gold"), 2, 5);
            // fire-aligned
            case "salamander" -> simple(Items.BLAZE_POWDER, 0, 2);
            case "ashwinder" -> simple(Items.BLAZE_POWDER, 0, 1);
            case "fire_crab" -> withRare(Items.BLAZE_POWDER, 0, 1, Items.EMERALD, 0.20f);
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
            case "griffin", "snallygaster" ->
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
