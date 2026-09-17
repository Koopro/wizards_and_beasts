package at.koopro.wizardsandbeasts.loot;

import at.koopro.wizardsandbeasts.item.spell.SpellSourceItem;
import at.koopro.wizardsandbeasts.registry.CanonItemRegistry;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.learning.SpellSource;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Adds a spell source — a book by default, or any item named by {@code item} — with one spell from
 * {@code spells} written in it.
 *
 * <p>The bootstrap for spell learning. With the teacher gone nobody sells a first spell, so the first
 * one has to be somewhere in the world: a village library, a stronghold's shelves, a cache somebody
 * left behind. Every copy after that is scribed by a player who already knows it, at a
 * {@code SpellScriptoriumBlock}.
 *
 * <p><b>Which</b> spell is rolled, not fixed. A found book you can choose the contents of is a
 * catalogue with extra steps, and a catalogue is what this system exists to not be. The pool is
 * per-modifier so a pack decides what a village shelf can hold versus what a stronghold can.
 *
 * <p>A pool entry naming a spell that does not exist, is unimplemented, or is an Obscurial ability is
 * skipped at roll time rather than at load: the spell registry is populated long after a datapack
 * parses, and refusing to load the modifier would take the working entries down with the broken one.
 * If nothing in the pool survives, no source is added.
 *
 * <p>{@code item} is optional and defaults to the Standard Book of Spells. It exists so the same
 * modifier can seed a village shelf with a reusable textbook and a mineshaft with a single torn page
 * that is spent on reading — two different bargains out of one rolled spell, and no second class to
 * keep in step with this one. An {@code item} that is not a spell source would produce a stack
 * carrying a component nothing reads, so it is refused at parse time.
 */
@NullMarked
public class SpellbookLootModifier extends LootModifier {

    /**
     * An item that is actually a spell source. Validated here rather than trusted, because the
     * failure it prevents is silent: a plain item with a {@code spell_source} component on it looks
     * like a normal drop, teaches nothing when used, and gives no clue why.
     */
    private static final Codec<Item> SOURCE_ITEM_CODEC = BuiltInRegistries.ITEM.byNameCodec()
            .validate(item -> item instanceof SpellSourceItem
                    ? DataResult.success(item)
                    : DataResult.error(() -> BuiltInRegistries.ITEM.getKey(item) + " is not a spell source"));

    public static final MapCodec<SpellbookLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).and(instance.group(
                    Codec.STRING.listOf().fieldOf("spells").forGetter(m -> m.spells),
                    Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(m -> m.chance),
                    SOURCE_ITEM_CODEC.optionalFieldOf("item").forGetter(m -> m.item)
            )).apply(instance, SpellbookLootModifier::new));

    private final List<String> spells;
    private final float chance;
    private final Optional<Item> item;

    public SpellbookLootModifier(LootItemCondition[] conditions, List<String> spells, float chance,
                                 Optional<Item> item) {
        super(conditions);
        this.spells = List.copyOf(spells);
        this.chance = chance;
        this.item = item;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (chance < 1.0f && context.getRandom().nextFloat() >= chance) {
            return generatedLoot;
        }
        Spell spell = rollSpell(context);
        if (spell == null) {
            return generatedLoot;
        }
        ItemStack source = new ItemStack(item.orElseGet(CanonItemRegistry.STANDARD_BOOK_OF_SPELLS));
        SpellSource.write(source, spell);
        generatedLoot.add(source);
        return generatedLoot;
    }

    /**
     * One spell from the pool, or {@code null} if none of it is writable.
     *
     * <p>Rolls an index and walks forward from it rather than filtering the list first: the pool is a
     * handful of entries read on every chest opened in range, and building a fresh list each time to
     * throw it away is work the common case (every entry valid) does not need.
     */
    private @Nullable Spell rollSpell(LootContext context) {
        if (spells.isEmpty()) {
            return null;
        }
        int start = context.getRandom().nextInt(spells.size());
        for (int offset = 0; offset < spells.size(); offset++) {
            Spell spell = Spells.byId(spells.get((start + offset) % spells.size()));
            if (spell != null && spell.isImplemented()
                    && !at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules.isObscurialAbility(spell)) {
                return spell;
            }
        }
        return null;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
