package at.koopro.wizardsandbeasts.loot;

import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.bestiary.harvest.HarvestGate;
import at.koopro.wizardsandbeasts.bestiary.harvest.HarvestRule;
import at.koopro.wizardsandbeasts.bestiary.harvest.HarvestTable;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Rare creature materials, yielded only to a killer who has studied the creature.
 *
 * <p>This closes the Bestiary's dead edge. Discovery tiers were read by exactly two things before this —
 * a stat number and an exam grade — so mastering a creature bought a figure on a screen and nothing a
 * player could hold. A tier now decides whether the rarest part of a beast comes off it.
 *
 * <h2>It only ever adds</h2>
 *
 * <p>The modifier appends to {@code generatedLoot} and never inspects, filters or removes what is
 * already there. Basic drops are therefore untouched <em>by construction</em> rather than by care: an
 * unstudied player killing a unicorn gets exactly the loot table's unicorn hair, the same as before this
 * existed, and no code path here can take it away.
 *
 * <h2>Server authority</h2>
 *
 * <p>Loot generation is server-side and the tier is read from the killer's server-side attachment. The
 * client is never consulted and is never told about harvest lockouts.
 *
 * <h2>Ordering</h2>
 *
 * <p>Listed <b>before</b> {@code module_gated} in {@code global_loot_modifiers.json}, deliberately. That
 * modifier strips any rolled stack whose module is switched off, and it can only strip what has already
 * been added — running after it would let this one smuggle a MAGIZOOLOGY item onto a server with the
 * module disabled.
 */
@NullMarked
public class BestiaryHarvestLootModifier extends LootModifier {

    public static final MapCodec<BestiaryHarvestLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).apply(instance, BestiaryHarvestLootModifier::new));

    public BestiaryHarvestLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    /**
     * True while study-gated materials exist at all.
     *
     * <p>Both modules are required. {@code BESTIARY} is where tiers come from — with it off nothing can
     * advance, so a tier gate would make these materials permanently unobtainable rather than merely
     * hard to get. {@code MAGIZOOLOGY} owns beast drops, which is what these are.
     */
    public static boolean isActive() {
        return ModuleManager.isEnabled(Module.BESTIARY) && ModuleManager.isEnabled(Module.MAGIZOOLOGY);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (HarvestTable.isEmpty() || !isActive()) {
            return generatedLoot;
        }
        // A table rolled with a BLOCK_STATE is someone breaking a block, not harvesting a beast.
        if (context.hasParameter(LootContextParams.BLOCK_STATE)) {
            return generatedLoot;
        }

        Entity victim = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if (victim == null) {
            return generatedLoot;
        }
        List<HarvestRule> rules = HarvestTable.forEntityType(
                victim.getType().builtInRegistryHolder().key().identifier());
        if (rules.isEmpty()) {
            return generatedLoot;
        }

        // No killer, no harvest. A beast that burned to death or was killed by another mob was studied
        // by nobody, and there is no tier to check — so the rare part simply is not taken. This is also
        // what stops a mob-crusher farm from producing rare materials with no player involved at all.
        Player killer = context.getOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER);
        if (!(killer instanceof ServerPlayer player) || player.isSpectator()) {
            return generatedLoot;
        }

        long now = player.level().getGameTime();
        for (HarvestRule rule : rules) {
            DiscoveryTier held = BestiaryDataHelper.getTier(player, rule.entry());
            long last = BestiaryDataHelper.getLastHarvestTick(player, rule.entry());

            if (!HarvestGate.isEligible(rule, held, last, now)) {
                continue;
            }
            if (context.getRandom().nextFloat() >= rule.chance()) {
                // A failed roll deliberately does not start the lockout. The cooldown throttles what a
                // player *receives*; charging it for a miss would make an unlucky player wait as long as
                // a lucky one, which reads as the feature being broken.
                continue;
            }

            generatedLoot.add(new ItemStack(rule.item(),
                    HarvestGate.countFor(rule, context.getRandom().nextFloat())));
            BestiaryDataHelper.recordHarvest(player, rule.entry(), now);
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
