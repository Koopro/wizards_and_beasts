package at.koopro.wizardsandbeasts.treasure;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Set;

/**
 * Puts cursed artefacts into the chests of treasure sites ({@link TreasureSites}).
 *
 * <p>A chest is rolled once, when it is first opened, so each tomb holds what it holds: a passer-by finds an
 * artefact at the site's {@code chance}, a wizard on a Gringotts expedition to that kind of site at its
 * {@code expedition_chance}. At most one artefact per chest. Off with {@link Module#GRINGOTTS}: without a bank
 * there is no one to surrender treasure to.
 */
@NullMarked
public class TreasureLootModifier extends LootModifier {

    public static final MapCodec<TreasureLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).apply(instance, TreasureLootModifier::new));

    public TreasureLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!ModuleManager.isEnabled(Module.GRINGOTTS) || context.hasParameter(LootContextParams.BLOCK_STATE)) {
            return generatedLoot;
        }
        List<Identifier> sites = TreasureSites.forLootTable(context.getQueriedLootTableId());
        if (sites.isEmpty()) {
            return generatedLoot;
        }
        Entity opener = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
        Set<Identifier> expeditions = opener instanceof ServerPlayer player ? Expeditions.targets(player) : Set.of();
        for (Identifier siteId : sites) {
            TreasureSite site = TreasureSites.get(siteId);
            if (site == null) {
                continue;
            }
            float chance = expeditions.contains(siteId) ? site.expeditionChance() : site.chance();
            if (context.getRandom().nextFloat() < chance) {
                generatedLoot.add(TreasureArtefacts.create(siteId, site, context.getRandom()));
                break;
            }
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
