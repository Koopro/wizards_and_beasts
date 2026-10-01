package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.AnimagusForms;
import at.koopro.wizardsandbeasts.animagus.AnimagusFormBinding;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.event.item.HandOfGloryTickHandler;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.item.consumable.ElixirOfLife;
import at.koopro.wizardsandbeasts.ministry.trace.Incident;
import at.koopro.wizardsandbeasts.ministry.trace.LegalClass;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryCaseData;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryTrace;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModCreatures;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.resistance.MagicResistance;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceService;
import at.koopro.wizardsandbeasts.wand.allegiance.WandBondHistory;
import at.koopro.wizardsandbeasts.wand.ollivander.OllivanderPrice;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The rulings of documentation/CANON_AUDIT.md §11, one scenario per contradiction that has server behaviour worth
 * pinning (C-1's pure rule and C-3's arithmetic are unit-tested).
 */
public final class CanonRulingTests {

    private CanonRulingTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("canon_unforgivable_crime_needs_a_person",
                "canon C-2: an Unforgivable on a creature is Dark magic; on a person it is the Unforgivable offence",
                CanonRulingTests::unforgivableNeedsAPerson);
        tests.add("canon_one_defeat_wins_a_wand",
                "canon C-3: an ordinary wand changes allegiance on a single defeat of its master",
                CanonRulingTests::oneDefeatWinsAWand);
        tests.add("canon_animagus_form_is_innate",
                "canon C-4: an Animagus form is drawn from who the wizard is, the same every time, and has a body",
                CanonRulingTests::animagusFormIsInnate);
        tests.add("canon_half_giants_hold_wands",
                "canon C-5: a half-giant may use a wand; a full giant may not",
                CanonRulingTests::halfGiantsHoldWands);
        tests.add("canon_hand_of_glory_blinds_nobody",
                "canon C-7: a lit Hand of Glory lets its holder see through darkness and blinds no one else",
                CanonRulingTests::handOfGloryBlindsNobody);
        tests.add("canon_second_wand_costs_seven_galleons",
                "canon C-8: the first wand is free; the next costs seven Galleons, taken all or nothing",
                CanonRulingTests::secondWandCosts);
        tests.add("canon_elixir_spares_one_death_a_day",
                "canon C-9: the Elixir of Life keeps its drinker alive once, then must be drunk again tomorrow",
                CanonRulingTests::elixirSparesOneDeath);
        tests.add("canon_dragon_needs_half_a_dozen_stunners",
                "canon C-12: a dragon shrugs off Stunners until about six land at once",
                CanonRulingTests::dragonNeedsHalfADozen);
    }

    @SuppressWarnings("deprecation") // The cache-only setter is the point: nothing is persisted or broadcast.
    private static void unforgivableNeedsAPerson(GameTestHelper helper) {
        ServerPlayer caster = player(helper, "CursingWizard", GameType.SURVIVAL);
        boolean wasOn = ModuleManager.isEnabled(Module.MINISTRY);
        ModuleManager.setState(Module.MINISTRY, ModuleManager.State.ENABLED);
        try {
            var cow = helper.spawn(EntityType.COW, new BlockPos(2, 1, 0));
            cow.setNoAi(true);
            var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(0, 1, 2));
            villager.setNoAi(true);
            MinistryCaseData data = MinistryCaseData.get(helper.getLevel().getServer());

            MinistryTrace.onUnforgivableUse(caster, "avada_kedavra", SpellCategory.DARK_ARTS, cow);
            Incident onCow = latestDarkAct(data, caster);
            check(helper, onCow != null && onCow.legalClass() == LegalClass.DARK,
                    () -> "the Killing Curse on a cow was filed as " + (onCow == null ? "nothing" : onCow.legalClass()));

            MinistryTrace.onUnforgivableUse(caster, "avada_kedavra", SpellCategory.DARK_ARTS, villager);
            Incident onVillager = latestDarkAct(data, caster);
            check(helper, onVillager != null && onVillager.legalClass() == LegalClass.UNFORGIVABLE,
                    () -> "the Killing Curse on a villager was filed as "
                            + (onVillager == null ? "nothing" : onVillager.legalClass()));
            data.forgetDarkActs(caster.getUUID());
            helper.succeed();
        } finally {
            if (!wasOn) {
                ModuleManager.setState(Module.MINISTRY, ModuleManager.State.DISABLED);
            }
            WizardTestSupport.retire(helper, caster);
        }
    }

    private static Incident latestDarkAct(MinistryCaseData data, ServerPlayer caster) {
        List<Long> acts = data.darkActs(caster.getUUID());
        return acts.isEmpty() ? null : data.incident(acts.getLast());
    }

    private static void oneDefeatWinsAWand(GameTestHelper helper) {
        ServerPlayer master = player(helper, "WandMaster", GameType.SURVIVAL);
        ServerPlayer victor = player(helper, "WandVictor", GameType.SURVIVAL);
        try {
            // Draco's wand: hawthorn and unicorn hair (Deathly Hallows ch. 24).
            ItemStack wand = new ItemStack(WandItemRegistry.WAND.get());
            wand.set(WandComponents.WAND_WOOD.get(), Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "hawthorn"));
            wand.set(WandComponents.WAND_CORE.get(), Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "unicorn_hair"));
            wand.set(WandComponents.WAND_FLEXIBILITY.get(), WandFlexibility.PLIANT);
            wand.set(WandComponents.WAND_LENGTH.get(), 10.0f);
            wand.set(WandComponents.WAND_INTEGRITY.get(), 1.0f);
            ModDataComponents.refreshElderWandMarker(wand);
            wand.set(WandComponents.WAND_MASTER.get(), Optional.of(master.getUUID()));
            wand.set(WandComponents.WAND_ALLEGIANCE_SCORE.get(), 1.0f);
            wand.set(WandComponents.WAND_BOND_HISTORY.get(), WandBondHistory.EMPTY.withFirstMasterIfAbsent(master.getUUID()));
            master.setItemInHand(InteractionHand.MAIN_HAND, wand);

            WandAllegianceService.onDefeat(master, victor, WandAllegianceService.DefeatKind.DISARM,
                    List.of(master.getMainHandItem()));
            check(helper, WandComponents.getMaster(master.getMainHandItem()).equals(Optional.of(victor.getUUID())),
                    () -> "one defeat did not win Draco's wand");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, master);
            WizardTestSupport.retire(helper, victor);
        }
    }

    private static void animagusFormIsInnate(GameTestHelper helper) {
        UUID someone = UUID.fromString("7a1e0000-0000-4000-8000-0000000000aa");
        String form = AnimagusForms.innateFormId(someone);
        check(helper, form.equals(AnimagusForms.innateFormId(someone)), () -> "the same wizard drew two animals");
        check(helper, AnimagusForms.isAnimagusForm(form), () -> "an innate form outside the Animagus forms: " + form);
        boolean anyDefined = AnimagusForms.IDS.stream().anyMatch(id -> AnimagusFormBinding.resolve(id).isPresent());
        check(helper, !anyDefined || AnimagusFormBinding.resolve(form).isPresent(),
                () -> "an innate form with no body definition: " + form);
        helper.succeed();
    }

    private static void halfGiantsHoldWands(GameTestHelper helper) {
        ServerPlayer half = player(helper, "HalfGiant", GameType.SURVIVAL);
        ServerPlayer full = player(helper, "FullGiant", GameType.SURVIVAL);
        try {
            HeritageAPI.commit(half, Heritage.GIANT, HeritageVariant.GIANT_HALF);
            HeritageAPI.commit(full, Heritage.GIANT, HeritageVariant.GIANT_FULL);
            check(helper, half.getData(ModAttachments.HERITAGE_DATA.get()).canUseWand(),
                    () -> "a half-giant could not use a wand (Hagrid, Madame Maxime)");
            check(helper, !full.getData(ModAttachments.HERITAGE_DATA.get()).canUseWand(),
                    () -> "a full giant could use a wand");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, half);
            WizardTestSupport.retire(helper, full);
        }
    }

    private static void handOfGloryBlindsNobody(GameTestHelper helper) {
        ServerPlayer holder = player(helper, "GloryHolder", GameType.SURVIVAL);
        ServerPlayer bystander = player(helper, "Bystander", GameType.SURVIVAL);
        Runnable darkArts = WizardTestSupport.leaseModule(Module.DARK_ARTS);
        try {
            ItemStack hand = new ItemStack(TrinketItemRegistry.HAND_OF_GLORY.get());
            hand.set(ModDataComponents.HAND_OF_GLORY_CANDLE_LIT.get(), true);
            holder.setItemInHand(InteractionHand.MAIN_HAND, hand);
            holder.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0));
            HandOfGloryTickHandler.onPlayerTick(new PlayerTickEvent.Post(holder));
            check(helper, holder.hasEffect(MobEffects.NIGHT_VISION) && !holder.hasEffect(MobEffects.BLINDNESS),
                    () -> "the holder did not see through the darkness");
            check(helper, !bystander.hasEffect(MobEffects.BLINDNESS), () -> "a bystander was blinded");
            helper.succeed();
        } finally {
            darkArts.run();
            WizardTestSupport.retire(helper, holder);
            WizardTestSupport.retire(helper, bystander);
        }
    }

    private static void secondWandCosts(GameTestHelper helper) {
        ServerPlayer buyer = player(helper, "WandBuyer", GameType.SURVIVAL);
        try {
            check(helper, OllivanderPrice.priceFor(buyer) == 0, () -> "the first wand was not free");
            OllivanderPrice.recordSale(buyer);
            int price = OllivanderPrice.priceFor(buyer);
            check(helper, price == 7 * 17 * 29, () -> "the second wand did not cost seven Galleons: " + price);
            PlayerVaultData vault = buyer.getData(ModAttachments.VAULT_DATA.get());
            vault.depositKnuts(100);
            check(helper, !OllivanderPrice.pay(buyer) && vault.getKnuts() == 100,
                    () -> "a short vault paid, or lost coins: " + vault.getKnuts());
            vault.depositKnuts(price);
            check(helper, OllivanderPrice.pay(buyer), () -> "a full vault could not pay");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, buyer);
        }
    }

    private static void elixirSparesOneDeath(GameTestHelper helper) {
        ServerPlayer flamel = player(helper, "Flamel", GameType.SURVIVAL);
        try {
            check(helper, ElixirOfLife.canDrink(flamel), () -> "setup: could not drink");
            ElixirOfLife.drink(flamel);
            check(helper, !ElixirOfLife.canDrink(flamel), () -> "a second draught the same day");
            flamel.kill(helper.getLevel());
            check(helper, flamel.isAlive() && flamel.getHealth() > 0, () -> "the Elixir did not hold death off");
            check(helper, !ElixirOfLife.sustains(flamel), () -> "the Elixir held on after being spent");
            flamel.invulnerableTime = 0; // vanilla ignores a second lethal blow inside the hurt cooldown
            flamel.kill(helper.getLevel());
            check(helper, !flamel.isAlive(), () -> "the Elixir saved the drinker twice in one day");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, flamel);
        }
    }

    @SuppressWarnings("unchecked")
    private static void dragonNeedsHalfADozen(GameTestHelper helper) {
        ServerPlayer stunner = player(helper, "DragonTamer", GameType.SURVIVAL);
        try {
            EntityType<GenericBeastEntity> type =
                    (EntityType<GenericBeastEntity>) ModCreatures.ENTITIES.get("hungarian_horntail").get();
            GenericBeastEntity horntail = helper.spawn(type, new BlockPos(3, 1, 3));
            horntail.setNoAi(true);
            Spell stupefy = Spells.byId("wizards_and_beasts:stupefy");
            for (int i = 1; i <= 5; i++) {
                int n = i;
                check(helper, !MagicResistance.takesHold(stupefy, horntail, stunner),
                        () -> "Stunner " + n + " took hold on a dragon");
            }
            check(helper, MagicResistance.takesHold(stupefy, horntail, stunner),
                    () -> "six Stunners at once did not take hold on a dragon");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, stunner);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name, GameType mode) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, mode);
        WizardTestSupport.parkAtOrigin(helper, player);
        return player;
    }
}
