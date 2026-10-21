package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.apparition.ApparitionAnchors;
import at.koopro.wizardsandbeasts.apparition.PlayerApparitionPoints;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.skill.SkillDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.skill.build.BuildProfile;
import at.koopro.wizardsandbeasts.skill.build.BuildRules;
import at.koopro.wizardsandbeasts.skill.build.Builds;
import at.koopro.wizardsandbeasts.skill.build.SkillRespecService;
import at.koopro.wizardsandbeasts.skill.build.capstone.CounterJinx;
import at.koopro.wizardsandbeasts.skill.build.capstone.HealersHands;
import at.koopro.wizardsandbeasts.skill.build.capstone.ScholarsMethod;
import at.koopro.wizardsandbeasts.skill.build.capstone.SeasonedTraveller;
import at.koopro.wizardsandbeasts.skill.build.capstone.WandkeepersHands;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.skill.vocation.VocationHelper;
import at.koopro.wizardsandbeasts.skill.vocation.VocationManager;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.sync.PlayerStateSyncService;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceService;
import at.koopro.wizardsandbeasts.wand.allegiance.WandBondState;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * Builds on a live player: spending and adjacency, the capstone gate and its "one mastery" rule, the two respec
 * doors, persistence and sync, module gating, two players side by side, and each capstone's mechanic reaching the
 * system it changes.
 *
 * <p>The rules themselves are unit-tested ({@code BuildRulesTest}, {@code CapstoneRulesTest},
 * {@code ArchetypeDataTest}); these check the wiring — that {@code evaluateUnlock}, the vocation, the vault, the
 * spell book and the sync payload all see the same build.
 */
public final class BuildTests {

    private static final Identifier DUELIST = id("duelist");
    private static final Identifier WANDMASTER = id("wandlore");
    /** The Duelist's cheapest road to its capstone: depth 2 is nonverbal casting and Confringo. */
    private static final List<String> DUELIST_ROAD = List.of(
            "wizard_core", "basic_casting", "expelliarmus_unlock", "stupefy_unlock", "duelling_forms",
            "nonverbal_casting");
    private static final List<String> WANDMASTER_ROAD = List.of(
            "wizard_core", "wand_study", "wand_appraisal", "wandlore_figures", "allegiance_grip", "wand_true_master");

    private BuildTests() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("wizards_and_beasts", path);
    }

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("builds_spending_and_the_capstone_gate",
                "builds: points are spent along adjacency, and a capstone needs its road, declares the build, and is one per wizard",
                BuildTests::spendingAndCapstone);
        tests.add("builds_unlearn_and_respec",
                "builds: unlearning respects the web's shape, takes back the web's spells, and the first full respec is free",
                BuildTests::unlearnAndRespec);
        tests.add("builds_persist_and_sync",
                "builds: the allocation and the free-respec token survive a save and reach the client with the respec terms",
                BuildTests::persistAndSync);
        tests.add("builds_module_gating",
                "builds: the Dark Arts web and its capstone are sealed while the Dark Arts are switched off",
                BuildTests::moduleGating);
        tests.add("builds_two_players_independent",
                "builds: one wizard's mastery neither blocks nor lends anything to another's",
                BuildTests::twoPlayers);
        tests.add("builds_capstones_reach_their_systems",
                "builds: Wandkeeper mends and steadies wands, Healer lifts a stun, Scholar halves a gate, Explorer holds more",
                BuildTests::capstoneMechanics);
    }

    private static ServerPlayer wizard(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name);
        WizardTestSupport.parkAtOrigin(helper, player);
        WizardTestSupport.makeWandkind(player);
        // Capstones ask for a N.E.W.T. (education.QualificationGates); that gate is EducationTests' business.
        WizardTestSupport.grantNewts(player);
        return player;
    }

    private static boolean walk(ServerPlayer player, List<String> road) {
        for (String node : road) {
            if (SkillSystemAPI.getSkillData(player).getSkillLevel(node) == 0 && !SkillSystemAPI.tryUnlock(player, node)) {
                return false;
            }
        }
        return true;
    }

    private static String reason(ServerPlayer player, String node) {
        return SkillSystemAPI.evaluateUnlock(player, SkillTrees.byId(node)).reason();
    }

    // ── scenarios ───────────────────────────────────────────────────────────────────────────────

    private static void spendingAndCapstone(GameTestHelper helper) {
        ServerPlayer player = wizard(helper, "BuildDuelist");
        try {
            SkillSystemAPI.awardPoints(player, SkillSystemAPI.MAX_SKILL_POINTS);
            PlayerSkillData data = SkillSystemAPI.getSkillData(player);
            int start = data.getSkillPoints();

            check(helper, "not_adjacent".equals(reason(player, "basic_casting")),
                    () -> "a node off the web was allocatable: " + reason(player, "basic_casting"));
            check(helper, walk(player, DUELIST_ROAD), () -> "the Duelist's road could not be walked: stuck before "
                    + DUELIST_ROAD.stream().filter(n -> !data.hasSkill(n)).findFirst().orElse("?"));
            int spent = DUELIST_ROAD.stream().mapToInt(n -> SkillTrees.byId(n).getPointCost()).sum();
            check(helper, data.getSkillPoints() == start - spent,
                    () -> "spent " + (start - data.getSkillPoints()) + " points, the road costs " + spent);

            check(helper, BuildRules.CAPSTONE_SHALLOW.equals(reason(player, "duelling_riposte")),
                    () -> "one advanced node opened a depth-2 capstone: " + reason(player, "duelling_riposte"));
            check(helper, !SkillSystemAPI.tryUnlock(player, "duelling_riposte"), () -> "a shut capstone was bought");

            check(helper, SkillSystemAPI.tryUnlock(player, "confringo_unlock"), () -> "Confringo was refused");
            check(helper, SkillSystemAPI.tryUnlock(player, "duelling_riposte"),
                    () -> "the open capstone was refused: " + reason(player, "duelling_riposte"));
            check(helper, VocationHelper.getPrimary(player).filter(DUELIST::equals).isPresent(),
                    () -> "taking the capstone did not declare the Duelist: " + VocationHelper.getPrimary(player));
            BuildProfile profile = Builds.profileOf(player);
            check(helper, profile.status() == BuildProfile.Status.MASTER && profile.current() != null
                            && profile.current().id().equals(DUELIST),
                    () -> "the profile does not name a Duelist master: " + profile.status() + " " + profile.current());
            check(helper, SkillSystemAPI.hasAbility(player, CounterJinx.ABILITY), () -> "the capstone granted nothing");

            check(helper, VocationManager.commit(player, WANDMASTER) == VocationManager.CommitResult.MASTERY_HELD,
                    () -> "a Duelist master re-declared as another build");
            for (String node : WANDMASTER_ROAD) {
                SkillSystemAPI.forceUnlock(player, node);
            }
            check(helper, BuildRules.CAPSTONE_TAKEN.equals(reason(player, "wandkeepers_hands")),
                    () -> "a second mastery was open: " + reason(player, "wandkeepers_hands"));
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void unlearnAndRespec(GameTestHelper helper) {
        ServerPlayer player = wizard(helper, "BuildRespec");
        try {
            SkillSystemAPI.awardPoints(player, 20);
            PlayerSkillData data = SkillSystemAPI.getSkillData(player);
            PlayerVaultData vault = player.getData(ModAttachments.VAULT_DATA.get());
            vault.depositGalleons(1);
            Spell expelliarmus = Spells.byId("expelliarmus");
            PlayerSpellData spells = WizardTestSupport.spellData(player);
            check(helper, expelliarmus != null && !spells.knowsSpell(expelliarmus.getId()),
                    () -> "the fixture wizard already knows Expelliarmus");

            check(helper, walk(player, List.of("wizard_core", "basic_casting", "expelliarmus_unlock")),
                    () -> "the short road could not be walked");
            check(helper, spells.knowsSpell(expelliarmus.getId()), () -> "the node did not teach its spell");

            SkillRespecService.Quote bridge = SkillRespecService.quoteUnlearn(player, "basic_casting");
            check(helper, "would_strand".equals(bridge.reason()) && bridge.stranded().contains("expelliarmus_unlock"),
                    () -> "unlearning a bridge was allowed: " + bridge);

            int pointsBefore = data.getSkillPoints();
            long coinsBefore = vault.getTotalInKnuts();
            SkillRespecService.Outcome leaf = SkillRespecService.unlearn(player, "expelliarmus_unlock");
            check(helper, leaf.done(), () -> "unlearning a leaf failed: " + leaf.result());
            check(helper, data.getSkillPoints() == pointsBefore + 2, () -> "unlearning refunded "
                    + (data.getSkillPoints() - pointsBefore) + " points, not Expelliarmus' 2");
            check(helper, vault.getTotalInKnuts() == coinsBefore - leaf.quote().feeKnuts(),
                    () -> "charged " + (coinsBefore - vault.getTotalInKnuts()) + " Knuts against a quote of "
                            + leaf.quote().feeKnuts());
            check(helper, !SkillRespecService.billingOn() || leaf.quote().feeKnuts() > 0,
                    () -> "with billing on, unlearning was free");
            check(helper, !spells.knowsSpell(expelliarmus.getId()), () -> "the web's spell outlived its node");

            check(helper, data.isFreeRespecAvailable(), () -> "a new character was owed no free respec");
            SkillRespecService.Outcome first = SkillRespecService.respec(player);
            check(helper, first.done() && first.quote().feeKnuts() == 0,
                    () -> "the first respec was not free: " + first.result() + " " + first.quote().feeKnuts());
            check(helper, data.getUnlockedSkills().isEmpty() && data.getSkillPoints() == data.getTotalPointsEarned(),
                    () -> "the respec left nodes or kept points: " + data.getUnlockedSkills());

            if (SkillRespecService.billingOn() && at.koopro.wizardsandbeasts.Config.skillRespecCostKnuts > 0) {
                check(helper, !data.isFreeRespecAvailable(), () -> "the free respec was not spent");
                SkillSystemAPI.tryUnlock(player, "wizard_core");
                long purse = vault.getTotalInKnuts();
                vault.withdrawSmartKnuts(purse);
                SkillRespecService.Outcome broke = SkillRespecService.respec(player);
                check(helper, "cannot_afford".equals(broke.result()) && data.hasSkill("wizard_core"),
                        () -> "an unpaid respec went through: " + broke.result());
                vault.depositGalleons(1);
                SkillRespecService.Outcome paid = SkillRespecService.respec(player);
                check(helper, paid.done() && paid.quote().feeKnuts() == at.koopro.wizardsandbeasts.Config.skillRespecCostKnuts,
                        () -> "the second respec was not charged the configured fee: " + paid.quote().feeKnuts());
            }
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void persistAndSync(GameTestHelper helper) {
        ServerPlayer player = wizard(helper, "BuildSaved");
        try {
            SkillSystemAPI.awardPoints(player, 10);
            walk(player, List.of("wizard_core", "basic_casting"));
            PlayerSkillData data = SkillSystemAPI.getSkillData(player);
            data.markFreeRespecUsed();

            CompoundTag saved = data.save();
            PlayerSkillData reloaded = new PlayerSkillData();
            reloaded.load(saved);
            check(helper, reloaded.getUnlockedSkills().equals(data.getUnlockedSkills())
                            && reloaded.getSkillPoints() == data.getSkillPoints()
                            && !reloaded.isFreeRespecAvailable(),
                    () -> "a save lost part of the build: " + reloaded.getUnlockedSkills());

            WizardTestSupport.drainClientboundPayloads(player);
            PlayerStateSyncService.syncSkills(player);
            Optional<SkillDataSyncS2CPayload> sync = WizardTestSupport.drainClientboundPayloads(player).stream()
                    .filter(SkillDataSyncS2CPayload.class::isInstance).map(SkillDataSyncS2CPayload.class::cast)
                    .reduce((a, b) -> b);
            check(helper, sync.isPresent(), () -> "no skill sync reached the client");
            SkillDataSyncS2CPayload payload = sync.get();
            check(helper, payload.unlockedSkills().containsKey("basic_casting")
                            && !payload.respec().freeRespecAvailable()
                            && payload.respec().unlearnPerPointKnuts() == SkillRespecService.effectiveUnlearnPerPoint(),
                    () -> "the sync carried the wrong build or terms: " + payload);

            var buf = new net.minecraft.network.FriendlyByteBuf(Unpooled.buffer());
            SkillDataSyncS2CPayload.STREAM_CODEC.encode(buf, payload);
            SkillDataSyncS2CPayload decoded = SkillDataSyncS2CPayload.STREAM_CODEC.decode(buf);
            check(helper, decoded.equals(payload), () -> "the payload did not survive the wire: " + decoded);
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    /**
     * Resilient to scenarios running side by side: DARK_ARTS ships off, but another scenario may hold a lease on it,
     * so the "off" half is asserted only when it is actually off, and the "on" half under this scenario's own lease.
     */
    private static void moduleGating(GameTestHelper helper) {
        ServerPlayer player = wizard(helper, "BuildDarkStudent");
        Runnable lease = null;
        try {
            SkillSystemAPI.awardPoints(player, 20);
            SkillSystemAPI.tryUnlock(player, "wizard_core");
            if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
                check(helper, "module_disabled".equals(reason(player, "dark_knowledge")),
                        () -> "the Dark Arts web was open with the module off: " + reason(player, "dark_knowledge"));
                check(helper, Builds.profileOf(player).progressOf(id("dark_arts")).isEmpty()
                                || BuildRules.BUILD_MODULE_DISABLED.equals(
                                BuildRules.capstoneRefusal("avada_kedavra_unlock", java.util.Map.of(), Builds.all(),
                                        ModuleManager::isEnabled)),
                        () -> "the Dark Arts capstone was offered with the module off");
                check(helper, VocationManager.commit(player, id("dark_arts")) != VocationManager.CommitResult.OK,
                        () -> "the Dark Arts build was declared with the module off");
            }
            lease = WizardTestSupport.leaseModule(Module.DARK_ARTS);
            check(helper, "ok".equals(reason(player, "dark_knowledge")),
                    () -> "the Dark Arts web stayed shut with the module on: " + reason(player, "dark_knowledge"));
            helper.succeed();
        } finally {
            if (lease != null) {
                lease.run();
            }
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void twoPlayers(GameTestHelper helper) {
        ServerPlayer duelist = wizard(helper, "BuildPairDuelist");
        ServerPlayer wandmaker = wizard(helper, "BuildPairWandmaster");
        try {
            for (ServerPlayer p : List.of(duelist, wandmaker)) {
                SkillSystemAPI.awardPoints(p, SkillSystemAPI.MAX_SKILL_POINTS);
            }
            walk(duelist, DUELIST_ROAD);
            walk(duelist, List.of("confringo_unlock", "duelling_riposte"));
            check(helper, walk(wandmaker, WANDMASTER_ROAD) && SkillSystemAPI.tryUnlock(wandmaker, "wandkeepers_hands"),
                    () -> "another wizard's mastery blocked this one: " + reason(wandmaker, "wandkeepers_hands"));
            check(helper, !SkillSystemAPI.hasAbility(wandmaker, CounterJinx.ABILITY)
                            && !SkillSystemAPI.hasAbility(duelist, WandkeepersHands.ABILITY),
                    () -> "a capstone leaked between players");

            long now = helper.getLevel().getGameTime();
            String stupefy = Spells.byId("stupefy").getId();
            for (ServerPlayer p : List.of(duelist, wandmaker)) {
                WizardTestSupport.spellData(p).setCooldown(stupefy, now + 400);
            }
            CounterJinx.onWardBlock(wandmaker);
            CounterJinx.onWardBlock(duelist);
            check(helper, !WizardTestSupport.spellData(duelist).isOnCooldown(stupefy, now),
                    () -> "the Duelist's block did not ready Stupefy");
            check(helper, WizardTestSupport.spellData(wandmaker).isOnCooldown(stupefy, now),
                    () -> "a block readied a non-Duelist's spell");

            SkillRespecService.respec(duelist);
            check(helper, SkillSystemAPI.hasAbility(wandmaker, WandkeepersHands.ABILITY),
                    () -> "one wizard's respec took another's capstone");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, duelist);
            WizardTestSupport.retire(helper, wandmaker);
        }
    }

    private static void capstoneMechanics(GameTestHelper helper) {
        ServerPlayer wizard = wizard(helper, "BuildCapstones");
        ServerPlayer ally = wizard(helper, "BuildPatient");
        try {
            // Wandkeeper: a damaged wand mended by Reparo, a snapped one not; a stranger's wand answers reluctantly.
            ItemStack ownWand = WizardTestSupport.giveBondedWand(wizard);
            ItemStack damaged = ownWand.copy();
            damaged.set(WandComponents.WAND_INTEGRITY.get(), 0.5f);
            check(helper, !WandAllegianceService.mendWand(wizard, ownWand, damaged),
                    () -> "ordinary magic mended a wand");
            ItemStack foreign = ownWand.copy();
            foreign.set(WandComponents.WAND_MASTER.get(), Optional.of(UUID.randomUUID()));
            check(helper, WandAllegianceService.castingState(wizard, foreign) == WandBondState.UNFAMILIAR,
                    () -> "a stranger's wand was familiar before the capstone");
            SkillSystemAPI.forceUnlock(wizard, WandkeepersHands.ABILITY);
            check(helper, WandAllegianceService.mendWand(wizard, ownWand, damaged)
                            && WandComponents.getIntegrity(damaged) >= 1.0f,
                    () -> "the Wandkeeper's Reparo did not mend the wand");
            ItemStack snapped = ownWand.copy();
            snapped.set(WandComponents.WAND_INTEGRITY.get(), 0.05f);
            check(helper, !WandAllegianceService.mendWand(wizard, ownWand, snapped),
                    () -> "a Wandkeeper mended a snapped wand");
            check(helper, WandAllegianceService.castingState(wizard, foreign) == WandBondState.RELUCTANT
                            && WandAllegianceService.stateFor(wizard.getUUID(), foreign) == WandBondState.UNFAMILIAR,
                    () -> "the capstone changed the wand's record, or did not change how it answers");

            // Healer: Episkey's tail lifts a stun from the faced ally.
            ally.addEffect(new MobEffectInstance(ModEffects.STUPEFY, 200));
            check(helper, !HealersHands.afterEpiskey(wizard, ally) && ally.hasEffect(ModEffects.STUPEFY),
                    () -> "Episkey lifted a stun without the capstone");
            SkillSystemAPI.forceUnlock(wizard, HealersHands.ABILITY);
            check(helper, HealersHands.afterEpiskey(wizard, ally) && !ally.hasEffect(ModEffects.STUPEFY),
                    () -> "the Healer's Episkey left the ally stunned");

            // Scholar: Bombarda waits on a Proficient Diffindo; half the practice opens it.
            Spell bombarda = Spells.byId("bombarda");
            Spell diffindo = Spells.byId("diffindo");
            PlayerSpellData spells = WizardTestSupport.spellData(wizard);
            spells.learnSpell(diffindo.getId());
            int gate = at.koopro.wizardsandbeasts.spell.core.Proficiency.PROFICIENT.getCastsRequired();
            spells.setSuccessfulHits(diffindo.getId(), ScholarsMethod.gate(gate));
            check(helper, !bombarda.getRequirement().isMet(wizard, spells),
                    () -> "half the practice was enough without the capstone");
            SkillSystemAPI.forceUnlock(wizard, ScholarsMethod.ABILITY);
            check(helper, bombarda.getRequirement().isMet(wizard, spells),
                    () -> "the Scholar's Method did not halve Diffindo's gate for Bombarda");

            // Explorer: four more destinations and the one lift of the ceiling.
            int capacityBefore = ApparitionAnchors.capacity(wizard);
            check(helper, ApparitionAnchors.ceiling(wizard) == PlayerApparitionPoints.MAX_POINTS,
                    () -> "the ceiling moved without the capstone");
            SkillSystemAPI.forceUnlock(wizard, SeasonedTraveller.ABILITY);
            check(helper, ApparitionAnchors.capacity(wizard) == capacityBefore + SeasonedTraveller.EXTRA_DESTINATIONS
                            && ApparitionAnchors.ceiling(wizard) == PlayerApparitionPoints.SEASONED_CEILING,
                    () -> "the Seasoned Traveller holds no more destinations");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, wizard);
            WizardTestSupport.retire(helper, ally);
        }
    }
}
