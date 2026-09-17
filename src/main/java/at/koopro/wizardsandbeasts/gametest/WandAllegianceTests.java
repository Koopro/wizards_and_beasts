package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;
import at.koopro.wizardsandbeasts.spell.cast.SpellCastService;
import at.koopro.wizardsandbeasts.spell.cast.SpellRejectCodes;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.wand.WandAttachments;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceRules;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceService;
import at.koopro.wizardsandbeasts.wand.allegiance.WandBondHistory;
import at.koopro.wizardsandbeasts.wand.allegiance.WandBondState;
import at.koopro.wizardsandbeasts.wand.elder.ElderWandSavedData;
import at.koopro.wizardsandbeasts.wand.resonance.WandResonanceSystem;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WandCastLifecycleTests.beginCast;
import static at.koopro.wizardsandbeasts.gametest.WandCastLifecycleTests.clearCooldowns;
import static at.koopro.wizardsandbeasts.gametest.WandCastLifecycleTests.releaseWand;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.spellData;

/**
 * Wands as relationships, in a live server: a wand choosing a wizard, being used by a stranger, dropped and picked
 * up, won by disarming, stunning and killing its master, saved and reloaded, cracked by a blast, and the Elder Wand
 * being won rather than made.
 *
 * <p>Bolts are real: {@code /wandb magic spell cast} from the console fires a real {@code SpellProjectileEntity},
 * whose hit runs the same Expelliarmus and Stupefy code a duel does. Each scenario stands on its own stone platform at
 * its own height with its chunks forced — see {@link WizardTestSupport#forceChunks} — because dropped wands fall,
 * bolts fly, and neighbouring scenarios' area effects reach six blocks.
 *
 * <p>Every scenario that moves allegiance also asserts the state no longer written — the legacy allegiance record
 * and the player-side bonded-wand attachment — stays unwritten, so no second copy of who a wand serves can drift.
 */
public final class WandAllegianceTests {

    private static final String ARRESTO = "arresto_momentum";
    private static final String ARRESTO_PREREQUISITE = "wingardium_leviosa";

    private static final int FIRST_BOND_Y = 41;
    private static final int SAVE_Y = 45;
    private static final int STRANGER_Y = 49;
    private static final int DROP_Y = 53;
    private static final int DISARM_Y = 57;
    private static final int STUN_Y = 61;
    private static final int KILL_Y = 65;
    private static final int ELDER_Y = 69;
    private static final int BLAST_Y = 80;

    private WandAllegianceTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("wand_bond_chosen_by_resonance", "wand: a wand chooses its wizard, and refuses before it has",
                WandAllegianceTests::aWandChoosesItsWizard);
        tests.add("wand_bond_survives_save_and_reconnect", "wand: allegiance survives a save, a reload and a reconnect",
                WandAllegianceTests::allegianceSurvivesSaveAndReconnect);
        tests.add("wand_bond_stranger_casts_poorly", "wand: another wizard's wand casts, poorly, and stays theirs",
                WandAllegianceTests::aStrangerCastsPoorly);
        tests.add("wand_bond_hawthorn_turns_on_strangers", "wand: hawthorn backfires in a stranger's hand",
                WandAllegianceTests::hawthornTurnsOnAStranger);
        tests.add("wand_bond_picking_up_wins_nothing", "wand: dropping and picking up a wand moves no allegiance",
                WandAllegianceTests::pickingUpWinsNothing);
        tests.add("wand_bond_disarm_wins_dragon", "wand: disarming the master wins a dragon heartstring wand",
                WandAllegianceTests::disarmingWinsADragonWand);
        tests.add("wand_bond_stuns_and_the_masters_answer", "wand: stuns count, and the master's cast answers them",
                WandAllegianceTests::stunsCountAndTheMasterAnswers);
        tests.add("wand_bond_kill_by_wizard_not_by_world", "wand: a wizard's kill wins a wand, a death by the world does not",
                WandAllegianceTests::aKillWinsButADeathDoesNot);
        tests.add("wand_bond_elder_wand_is_won", "wand: the Elder Wand is won, not made, and mends wands",
                WandAllegianceTests::theElderWandIsWonNotMade);
        tests.add("wand_bond_blast_cracks_and_breaks", "wand: blasts crack a held wand, and a broken one backfires",
                WandAllegianceTests::blastsCrackAndABrokenWandBackfires);
    }

    // ── A: the wand chooses the wizard ──────────────────────────────────────────────────────────

    private static void aWandChoosesItsWizard(GameTestHelper helper) {
        stage(helper, FIRST_BOND_Y);
        ServerPlayer wizard = wizard(helper, "wandb-bond-chosen", GameType.CREATIVE, FIRST_BOND_Y, 1, 1);
        ItemStack wand = wand("holly", "phoenix_feather");
        wizard.setItemInHand(InteractionHand.MAIN_HAND, wand);
        String spell = canonical(ARRESTO);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(FIRST_BOND_Y), max(FIRST_BOND_Y)))
                .thenExecute(() -> {
                    SpellCastService.completeWandCastRelease(wizard);
                    check(helper, spellData(wizard).getCastCount(spell) == 0,
                            () -> "a wand that has chosen nobody cast for its holder");
                    check(helper, spellData(wizard).getRejectCounts().getOrDefault(SpellRejectCodes.WAND_NOT_BONDED, 0) == 1,
                            () -> "the refusal was not recorded as an unchosen wand: " + spellData(wizard).getRejectCounts());

                    WandResonanceSystem.applyResonance(wizard, wand, 1.0f, helper.getLevel().registryAccess());
                    check(helper, WandComponents.getMaster(wand).equals(Optional.of(wizard.getUUID())),
                            () -> "a perfect resonance did not make the wizard its master: " + describe(wand));
                    check(helper, WandAllegianceService.stateFor(wizard.getUUID(), wand) == WandBondState.LOYAL,
                            () -> "a perfect match should start loyal, not " + describe(wand));
                    check(helper, wizard.getUUID().equals(WandComponents.getBondHistory(wand).firstMaster()),
                            () -> "the first master was not recorded: " + describe(wand));
                    checkNoLegacyState(helper, wizard, wand);
                })
                .thenExecute(() -> beginCast(helper, wizard))
                .thenIdle(1)
                .thenExecute(() -> {
                    releaseWand(wizard);
                    check(helper, spellData(wizard).getCastCount(spell) == 1,
                            () -> "the wand that chose its wizard would not cast for them: " + describe(wand));
                })
                .thenExecute(() -> retire(helper, wizard))
                .thenSucceed();
    }

    // ── B: nothing about allegiance is lost on a save ───────────────────────────────────────────

    private static void allegianceSurvivesSaveAndReconnect(GameTestHelper helper) {
        stage(helper, SAVE_Y);
        ServerPlayer wizard = wizard(helper, "wandb-bond-saved", GameType.CREATIVE, SAVE_Y, 1, 1);
        UUID id = wizard.getUUID();
        ItemStack wand = mastered(wand("ash", "unicorn_hair"), id, 0.6f);
        wand.set(WandComponents.WAND_BOND_HISTORY.get(), new WandBondHistory(id, UUID.randomUUID(), UUID.randomUUID(), 1, 777L));
        wand.set(WandComponents.WAND_INTEGRITY.get(), 0.7f);
        wand.set(WandComponents.WAND_CORRUPTION.get(), 0.2f);
        wizard.setItemInHand(InteractionHand.MAIN_HAND, wand);
        ItemStack before = wand.copy();
        ServerPlayer[] back = new ServerPlayer[1];

        helper.startSequence()
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
                    Tag saved = ItemStack.CODEC.encodeStart(ops, wand).getOrThrow();
                    ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
                    check(helper, ItemStack.isSameItemSameComponents(before, loaded),
                            () -> "the wand came back from disk different: " + describe(before) + " -> " + describe(loaded));

                    ElderWandSavedData elder = new ElderWandSavedData();
                    UUID instance = UUID.randomUUID();
                    elder.register(instance);
                    elder.setMaster(id);
                    var codec = ElderWandSavedData.TYPE.codecFactory().create(null);
                    ElderWandSavedData reloaded = codec.parse(NbtOps.INSTANCE,
                            codec.encodeStart(NbtOps.INSTANCE, elder).getOrThrow()).getOrThrow();
                    check(helper, instance.equals(reloaded.getInstanceId()) && id.equals(reloaded.getMaster()),
                            () -> "the Elder Wand's master did not survive a restart");

                    String name = wizard.getGameProfile().name();
                    retire(helper, wizard);
                    back[0] = WizardTestSupport.placeMockPlayer(helper, name, id, GameType.CREATIVE);
                    ItemStack reconnected = back[0].getMainHandItem();
                    check(helper, ItemStack.isSameItemSameComponents(before, reconnected),
                            () -> "the wand in hand changed across a reconnect: " + describe(before) + " -> " + describe(reconnected));
                    check(helper, WandAllegianceService.stateFor(id, reconnected) == WandBondState.ACCEPTING,
                            () -> "the reconnected wizard's wand no longer knows them: " + describe(reconnected));
                })
                .thenExecute(() -> retire(helper, back[0]))
                .thenSucceed();
    }

    // ── C: another wizard's wand ────────────────────────────────────────────────────────────────

    private static void aStrangerCastsPoorly(GameTestHelper helper) {
        stage(helper, STRANGER_Y);
        ServerPlayer owner = wizard(helper, "wandb-bond-owner", GameType.CREATIVE, STRANGER_Y, 0, 1);
        ServerPlayer stranger = wizard(helper, "wandb-bond-stranger", GameType.CREATIVE, STRANGER_Y, 3, 1);
        ItemStack wand = mastered(wand("holly", "phoenix_feather"), owner.getUUID(), 1.0f);
        stranger.setItemInHand(InteractionHand.MAIN_HAND, wand);
        ItemStack before = wand.copy();
        String spell = canonical(ARRESTO);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(STRANGER_Y), max(STRANGER_Y)))
                .thenExecute(() -> beginCast(helper, stranger))
                .thenIdle(1)
                .thenExecute(() -> {
                    releaseWand(stranger);
                    check(helper, spellData(stranger).getCastCount(spell) == 1,
                            () -> "another wizard's wand refused to cast at all; it should serve, poorly");
                    check(helper, WandAllegianceService.stateFor(stranger.getUUID(), wand) == WandBondState.UNFAMILIAR,
                            () -> "a stranger's hold on the wand is not unfamiliar: " + describe(wand));
                    var registries = helper.getLevel().registryAccess();
                    float strangerPower = WandAllegianceService.powerMultiplier(stranger, wand, registries);
                    float ownerPower = WandAllegianceService.powerMultiplier(owner, wand, registries);
                    check(helper, strangerPower < 1.0f && strangerPower < ownerPower,
                            () -> "a stranger drew " + strangerPower + "x from the wand, its master " + ownerPower + "x");
                    check(helper, ItemStack.isSameItemSameComponents(before, wand),
                            () -> "casting with another's wand changed its allegiance: " + describe(before) + " -> " + describe(wand));
                    checkNoLegacyState(helper, stranger, wand);
                })
                .thenExecute(() -> {
                    retire(helper, owner);
                    retire(helper, stranger);
                })
                .thenSucceed();
    }

    private static void hawthornTurnsOnAStranger(GameTestHelper helper) {
        stage(helper, BLAST_Y + 4);
        ServerPlayer owner = wizard(helper, "wandb-hawthorn-owner", GameType.SURVIVAL, BLAST_Y + 4, 0, 1);
        ServerPlayer stranger = wizard(helper, "wandb-hawthorn-stranger", GameType.SURVIVAL, BLAST_Y + 4, 3, 1);
        ItemStack wand = mastered(wand("hawthorn", "dragon_heartstring"), owner.getUUID(), 1.0f);
        stranger.setItemInHand(InteractionHand.MAIN_HAND, wand);
        String spell = canonical(ARRESTO);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(BLAST_Y + 4), max(BLAST_Y + 4)))
                .thenExecute(() -> beginCast(helper, stranger))
                .thenIdle(1)
                .thenExecute(() -> {
                    float health = stranger.getHealth();
                    releaseWand(stranger);
                    check(helper, spellData(stranger).getCastCount(spell) == 0,
                            () -> "a hawthorn wand cast for a stranger instead of backfiring");
                    check(helper, stranger.getHealth() < health,
                            () -> "the backfire did not touch the stranger: health " + stranger.getHealth());
                    check(helper, spellData(stranger).getRejectCounts().keySet().stream()
                                    .anyMatch(key -> key.startsWith(SpellRejectCodes.WAND_BACKFIRE)),
                            () -> "the backfire was not recorded: " + spellData(stranger).getRejectCounts());

                    owner.setItemInHand(InteractionHand.MAIN_HAND, stranger.getMainHandItem().copy());
                    stranger.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                })
                .thenExecute(() -> beginCast(helper, owner))
                .thenIdle(1)
                .thenExecute(() -> {
                    releaseWand(owner);
                    check(helper, spellData(owner).getCastCount(spell) == 1,
                            () -> "the hawthorn wand turned on its own master too");
                })
                .thenExecute(() -> {
                    retire(helper, owner);
                    retire(helper, stranger);
                })
                .thenSucceed();
    }

    // ── D: theft is not conquest ────────────────────────────────────────────────────────────────

    private static void pickingUpWinsNothing(GameTestHelper helper) {
        stage(helper, DROP_Y);
        ServerPlayer owner = wizard(helper, "wandb-drop-owner", GameType.CREATIVE, DROP_Y, 1, 1);
        ServerPlayer finder = wizard(helper, "wandb-drop-finder", GameType.CREATIVE, DROP_Y, 3, 1);
        ItemStack wand = mastered(wand("yew", "veela_hair"), owner.getUUID(), 0.8f);
        owner.setItemInHand(InteractionHand.MAIN_HAND, wand);
        ItemStack before = wand.copy();

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(DROP_Y), max(DROP_Y)))
                .thenExecute(() -> {
                    owner.drop(true);
                    check(helper, owner.getMainHandItem().isEmpty(), () -> "the owner did not drop the wand");
                })
                .thenWaitUntil(() -> check(helper, !droppedWands(helper, DROP_Y).isEmpty(), () -> "no dropped wand appeared"))
                .thenExecute(() -> {
                    ItemEntity dropped = droppedWands(helper, DROP_Y).getFirst();
                    dropped.setNoPickUpDelay();
                    dropped.playerTouch(finder);
                    ItemStack picked = findWand(finder);
                    check(helper, !picked.isEmpty(), () -> "the finder did not pick the wand up");
                    check(helper, ItemStack.isSameItemSameComponents(before, picked),
                            () -> "picking the wand up changed its allegiance: " + describe(before) + " -> " + describe(picked));
                    check(helper, WandAllegianceService.stateFor(finder.getUUID(), picked) == WandBondState.UNFAMILIAR,
                            () -> "a picked-up wand should be unfamiliar to its finder: " + describe(picked));
                    checkNoLegacyState(helper, finder, picked);
                })
                .thenExecute(() -> {
                    retire(helper, owner);
                    retire(helper, finder);
                })
                .thenSucceed();
    }

    // ── E: a disarming bolt wins a dragon heartstring ───────────────────────────────────────────

    private static void disarmingWinsADragonWand(GameTestHelper helper) {
        stage(helper, DISARM_Y);
        ServerPlayer master = wizard(helper, "wandb-disarm-master", GameType.CREATIVE, DISARM_Y, 1, 4);
        ServerPlayer duellist = duellist(helper, "wandb-disarm-duellist", DISARM_Y, "expelliarmus");
        ItemStack wand = mastered(wand("holly", "dragon_heartstring"), master.getUUID(), 1.0f);
        master.setItemInHand(InteractionHand.MAIN_HAND, wand);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(DISARM_Y), max(DISARM_Y)))
                .thenExecute(() -> {
                    List<String> said = castAs(helper, duellist, "expelliarmus");
                    check(helper, said.isEmpty() || said.stream().noneMatch(line -> line.contains("cannot")),
                            () -> "the cast command refused: " + said);
                })
                .thenWaitUntil(() -> check(helper, master.getMainHandItem().isEmpty(),
                        () -> "the Expelliarmus bolt never disarmed the master; hand holds " + describe(master.getMainHandItem())))
                .thenWaitUntil(() -> check(helper, !droppedWands(helper, DISARM_Y).isEmpty(), () -> "the disarmed wand is nowhere"))
                .thenExecute(() -> {
                    ItemStack won = droppedWands(helper, DISARM_Y).getFirst().getItem();
                    check(helper, WandComponents.getMaster(won).equals(Optional.of(duellist.getUUID())),
                            () -> "disarming the master did not win the dragon heartstring wand: " + describe(won));
                    check(helper, WandAllegianceService.stateFor(duellist.getUUID(), won) == WandBondState.ACCEPTING,
                            () -> "dragon heartstring bonds strongly with its current owner; it should accept them at once: " + describe(won));
                    WandBondHistory history = WandComponents.getBondHistory(won);
                    check(helper, master.getUUID().equals(history.formerMaster()) && master.getUUID().equals(history.firstMaster())
                                    && history.challenger() == null,
                            () -> "the won wand's history is wrong: " + history);
                    checkNoLegacyState(helper, duellist, won);
                })
                .thenExecute(() -> {
                    discardDropped(helper, DISARM_Y);
                    retire(helper, master);
                    retire(helper, duellist);
                })
                .thenSucceed();
    }

    // ── F: stuns count; the master's cast answers them ──────────────────────────────────────────

    private static void stunsCountAndTheMasterAnswers(GameTestHelper helper) {
        stage(helper, STUN_Y);
        ServerPlayer master = wizard(helper, "wandb-stun-master", GameType.SURVIVAL, STUN_Y, 1, 4);
        ServerPlayer duellist = duellist(helper, "wandb-stun-duellist", STUN_Y, "stupefy");
        ItemStack wand = mastered(wand("holly", "veela_hair"), master.getUUID(), 1.0f);
        master.setItemInHand(InteractionHand.MAIN_HAND, wand);
        Spell arresto = Spells.byId(ARRESTO);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(STUN_Y), max(STUN_Y)))
                .thenExecute(() -> castAs(helper, duellist, "stupefy"))
                .thenWaitUntil(() -> check(helper, wins(master) == 1,
                        () -> "the first stun did not count as a defeat: " + describe(master.getMainHandItem())))
                .thenExecute(() -> {
                    check(helper, WandComponents.getMaster(master.getMainHandItem()).equals(Optional.of(master.getUUID())),
                            () -> "an ordinary wand changed hands on a single defeat");
                    master.setHealth(master.getMaxHealth());
                    WandAllegianceService.onSuccessfulCast(master, master.getMainHandItem(), arresto);
                    check(helper, wins(master) == 0,
                            () -> "the master's successful cast did not answer the challenge: " + describe(master.getMainHandItem()));
                    castAs(helper, duellist, "stupefy");
                })
                .thenWaitUntil(() -> check(helper, wins(master) == 1, () -> "the second stun did not count"))
                .thenExecute(() -> {
                    check(helper, WandComponents.getMaster(master.getMainHandItem()).equals(Optional.of(master.getUUID())),
                            () -> "the wand changed hands although the master had answered the first defeat");
                    master.setHealth(master.getMaxHealth());
                    castAs(helper, duellist, "stupefy");
                })
                .thenWaitUntil(() -> check(helper,
                        WandComponents.getMaster(master.getMainHandItem()).equals(Optional.of(duellist.getUUID())),
                        () -> "two defeats in a row did not win the wand: " + describe(master.getMainHandItem())))
                .thenExecute(() -> {
                    ItemStack won = master.getMainHandItem();
                    check(helper, WandAllegianceService.stateFor(duellist.getUUID(), won) == WandBondState.RELUCTANT,
                            () -> "a newly won ordinary wand should be reluctant: " + describe(won));
                    checkNoLegacyState(helper, duellist, won);
                })
                .thenExecute(() -> {
                    retire(helper, master);
                    retire(helper, duellist);
                })
                .thenSucceed();
    }

    // ── G: a wizard's kill is a defeat; a fall is not ───────────────────────────────────────────

    private static void aKillWinsButADeathDoesNot(GameTestHelper helper) {
        stage(helper, KILL_Y);
        ServerPlayer victim = wizard(helper, "wandb-kill-victim", GameType.SURVIVAL, KILL_Y, 1, 1);
        ServerPlayer killer = wizard(helper, "wandb-kill-killer", GameType.SURVIVAL, KILL_Y, 3, 1);
        ServerPlayer faller = wizard(helper, "wandb-kill-faller", GameType.SURVIVAL, KILL_Y, 2, 3);
        victim.setItemInHand(InteractionHand.MAIN_HAND, mastered(wand("holly", "dragon_heartstring"), victim.getUUID(), 1.0f));
        faller.setItemInHand(InteractionHand.MAIN_HAND, mastered(wand("holly", "dragon_heartstring"), faller.getUUID(), 1.0f));

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(KILL_Y), max(KILL_Y)))
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    victim.hurtServer(level, level.damageSources().playerAttack(killer), 1000.0f);
                    faller.hurtServer(level, level.damageSources().generic(), 1000.0f);
                    check(helper, !victim.isAlive() && !faller.isAlive(), () -> "the test could not kill its players");
                })
                .thenWaitUntil(() -> check(helper, wandsOf(helper, KILL_Y, victim, faller).size() == 2,
                        () -> "expected both dead wizards' wands, found " + wandsOf(helper, KILL_Y, victim, faller).size()))
                .thenExecute(() -> {
                    for (ItemStack wand : wandsOf(helper, KILL_Y, victim, faller)) {
                        UUID first = WandComponents.getBondHistory(wand).firstMaster();
                        UUID master = WandComponents.getMaster(wand).orElse(null);
                        if (victim.getUUID().equals(first)) {
                            check(helper, killer.getUUID().equals(master),
                                    () -> "the wizard who killed the master did not win the dragon heartstring wand: " + describe(wand));
                        } else {
                            check(helper, faller.getUUID().equals(master),
                                    () -> "a death nobody caused moved the wand's allegiance: " + describe(wand));
                        }
                        checkNoLegacyState(helper, killer, wand);
                    }
                })
                .thenExecute(() -> {
                    discardDropped(helper, KILL_Y);
                    retire(helper, victim);
                    retire(helper, killer);
                    retire(helper, faller);
                })
                .thenSucceed();
    }

    // ── H: the Elder Wand ───────────────────────────────────────────────────────────────────────

    /**
     * One scenario, because the Elder Wand is one object per world: its saved master is shared by everything, and a
     * second Elder Wand in a parallel scenario would dissolve as a counterfeit.
     */
    private static void theElderWandIsWonNotMade(GameTestHelper helper) {
        stage(helper, ELDER_Y);
        ElderWandSavedData data = ElderWandSavedData.get(helper.getLevel());
        data.reset();
        ServerPlayer maker = wizard(helper, "wandb-elder-maker", GameType.CREATIVE, ELDER_Y, 0, 1);
        ServerPlayer rival = wizard(helper, "wandb-elder-rival", GameType.CREATIVE, ELDER_Y, 2, 1);
        ServerPlayer usurper = wizard(helper, "wandb-elder-usurper", GameType.SURVIVAL, ELDER_Y, 4, 1);
        ServerPlayer broken = wizard(helper, "wandb-elder-broken", GameType.SURVIVAL, ELDER_Y, 2, 3);
        ItemStack elder = wand("elder", "thestral_tail_hair");
        maker.setItemInHand(InteractionHand.MAIN_HAND, elder);
        rival.setItemInHand(InteractionHand.MAIN_HAND, mastered(wand("holly", "unicorn_hair"), rival.getUUID(), 1.0f));

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(ELDER_Y), max(ELDER_Y)))
                .thenExecuteFor(25, maker::doTick)
                .thenExecute(() -> {
                    check(helper, ModDataComponents.isElderWand(maker.getMainHandItem()), () -> "the made wand is not the Elder Wand");
                    check(helper, data.isRegistered(), () -> "holding the Elder Wand never registered it");
                    beginCast(helper, maker);
                    maker.releaseUsingItem();
                    check(helper, WandComponents.getMaster(maker.getMainHandItem()).isEmpty() && data.getMaster() == null,
                            () -> "making, holding or raising the Elder Wand made its maker its master: " + describe(maker.getMainHandItem()));
                    var registries = helper.getLevel().registryAccess();
                    check(helper, WandAllegianceService.powerMultiplier(maker, maker.getMainHandItem(), registries)
                                    < WandAllegianceRules.FOREIGN_POWER,
                            () -> "an unmastered Elder Wand should serve worse than an ordinary stranger's wand");

                    // The maker, wielding it, defeats a rival: now it has judged them the superior.
                    WandAllegianceService.onDefeat(rival, maker, WandAllegianceService.DefeatKind.DISARM,
                            WandAllegianceService.heldWands(rival));
                    check(helper, maker.getUUID().equals(data.getMaster()), () -> "defeating a rival while wielding it did not win the Elder Wand");
                    check(helper, WandAllegianceService.stateFor(maker.getUUID(), maker.getMainHandItem()) == WandBondState.RELUCTANT,
                            () -> "a newly won Elder Wand should be reluctant: " + describe(maker.getMainHandItem()));
                    check(helper, WandComponents.getMaster(rival.getMainHandItem()).equals(Optional.of(rival.getUUID())),
                            () -> "one defeat won the rival's unicorn wand too: " + describe(rival.getMainHandItem()));

                    // A usurper stuns the master, never touching the Elder Wand: its allegiance follows the defeat.
                    WandAllegianceService.onDefeat(maker, usurper, WandAllegianceService.DefeatKind.STUN, List.of());
                    check(helper, usurper.getUUID().equals(data.getMaster()), () -> "the Elder Wand did not follow its master's defeat");
                    check(helper, WandComponents.getMaster(maker.getMainHandItem()).equals(Optional.of(usurper.getUUID())),
                            () -> "the Elder Wand in the defeated master's hand still answers to them: " + describe(maker.getMainHandItem()));
                })
                .thenExecute(maker::doTick)
                .thenExecute(() -> {
                    check(helper, WandComponents.getMaster(maker.getMainHandItem()).equals(Optional.of(usurper.getUUID())),
                            () -> "the saved master and the stack disagreed after a tick: " + describe(maker.getMainHandItem()));

                    // Only the Elder Wand mends a wand. First, ordinary Reparo refuses.
                    ItemStack cracked = mastered(wand("holly", "phoenix_feather"), usurper.getUUID(), 1.0f);
                    cracked.set(WandComponents.WAND_INTEGRITY.get(), 0.05f);
                    usurper.setItemInHand(InteractionHand.MAIN_HAND, mastered(wand("holly", "phoenix_feather"), usurper.getUUID(), 1.0f));
                    usurper.setItemInHand(InteractionHand.OFF_HAND, cracked);
                    WizardTestSupport.learnAndSelect(helper, usurper, "reparo", "lumos");
                    beginCast(helper, usurper);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    releaseWand(usurper);
                    check(helper, WandComponents.getIntegrity(usurper.getOffhandItem()) <= WandAllegianceRules.BROKEN_AT,
                            () -> "ordinary Reparo mended a broken wand");
                    ItemStack won = maker.getMainHandItem().copy();
                    won.set(WandComponents.WAND_ALLEGIANCE_SCORE.get(), 1.0f);
                    maker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    usurper.setItemInHand(InteractionHand.MAIN_HAND, won);
                    clearReparoCooldown(usurper);
                    beginCast(helper, usurper);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    releaseWand(usurper);
                    check(helper, WandComponents.getIntegrity(usurper.getOffhandItem()) >= 1.0f,
                            () -> "the Elder Wand's Reparo did not mend the broken wand: " + describe(usurper.getOffhandItem()));

                    // A broken wand backfires, every time, on its own master.
                    ItemStack snapped = mastered(wand("holly", "phoenix_feather"), broken.getUUID(), 1.0f);
                    snapped.set(WandComponents.WAND_INTEGRITY.get(), 0.0f);
                    broken.setItemInHand(InteractionHand.MAIN_HAND, snapped);
                    beginCast(helper, broken);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    float health = broken.getHealth();
                    releaseWand(broken);
                    check(helper, spellData(broken).getCastCount(canonical(ARRESTO)) == 0,
                            () -> "a broken wand cast instead of backfiring");
                    check(helper, broken.getHealth() < health, () -> "the broken wand's backfire hurt nobody");
                })
                .thenExecute(() -> {
                    data.reset();
                    retire(helper, maker);
                    retire(helper, rival);
                    retire(helper, usurper);
                    retire(helper, broken);
                })
                .thenSucceed();
    }

    // ── I: blasts ───────────────────────────────────────────────────────────────────────────────

    private static void blastsCrackAndABrokenWandBackfires(GameTestHelper helper) {
        stage(helper, BLAST_Y);
        ServerPlayer wizard = wizard(helper, "wandb-blast", GameType.SURVIVAL, BLAST_Y, 2, 2);
        // Tough enough to live through the blast: a dead wizard's wand drops, and the hand reads empty.
        wizard.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500.0);
        wizard.setHealth(500.0f);
        ItemStack wand = mastered(wand("rowan", "unicorn_hair"), wizard.getUUID(), 1.0f);
        wizard.setItemInHand(InteractionHand.MAIN_HAND, wand);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(BLAST_Y), max(BLAST_Y)))
                .thenExecute(() -> {
                    Vec3 at = wizard.position();
                    helper.getLevel().explode(null, at.x, at.y + 0.5, at.z, 3.0f, Level.ExplosionInteraction.NONE);
                    check(helper, wizard.isAlive() && wizard.getMainHandItem() == wand,
                            () -> "the wizard did not keep hold of the wand through the blast; health " + wizard.getHealth());
                    float integrity = WandComponents.getIntegrity(wand);
                    check(helper, integrity < 1.0f,
                            () -> "a blast at the wizard's feet left the held wand untouched; wizard health " + wizard.getHealth());
                })
                .thenExecute(() -> retire(helper, wizard))
                .thenSucceed();
    }

    // ── shared ──────────────────────────────────────────────────────────────────────────────────

    private static BlockPos min(int y) {
        return new BlockPos(0, y - 1, 0);
    }

    private static BlockPos max(int y) {
        return new BlockPos(4, y + 2, 4);
    }

    /** Stone underfoot, air above, chunks forced. */
    private static void stage(GameTestHelper helper, int y) {
        WizardTestSupport.forceChunks(helper, min(y), max(y));
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, y - 1, z), Blocks.STONE);
                for (int dy = 0; dy <= 2; dy++) {
                    helper.setBlock(new BlockPos(x, y + dy, z), Blocks.AIR);
                }
            }
        }
    }

    private static ServerPlayer wizard(GameTestHelper helper, String name, GameType mode, int y, int x, int z) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, mode);
        BlockPos at = helper.absolutePos(new BlockPos(x, y, z));
        player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0f, 0.0f);
        player.setNoGravity(true);
        WizardTestSupport.makeWandkind(player);
        WizardTestSupport.learnAndSelect(helper, player, ARRESTO, ARRESTO_PREREQUISITE);
        return player;
    }

    /** A wizard at the platform's near edge facing +Z, four blocks from the target, who has mastered the bolt. */
    private static ServerPlayer duellist(GameTestHelper helper, String name, int y, String spellId) {
        ServerPlayer player = wizard(helper, name, GameType.CREATIVE, y, 1, 0);
        Spell spell = Spells.byId(spellId);
        if (spell == null) {
            helper.fail("test spell '" + spellId + "' is not registered");
            return player;
        }
        spellData(player).learnSpell(spell.getId());
        // Mastered, so a mastered wand's resistance roll (proficiency below its bond x 0.7) never saves the master.
        spellData(player).setSuccessfulHits(spell.getId(), 10_000);
        return player;
    }

    private static List<String> castAs(GameTestHelper helper, ServerPlayer caster, String spellId) {
        List<String> said = new ArrayList<>();
        CommandSource listener = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                said.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack().withSource(listener);
        helper.getLevel().getServer().getCommands().performPrefixedCommand(source,
                "execute as " + caster.getStringUUID() + " at @s run wandb magic spell cast " + spellId);
        return said;
    }

    /** A wand built from datapack ids — every wood and core, not only the four the old enum knows. */
    private static ItemStack wand(String wood, String core) {
        ItemStack stack = new ItemStack(WandItemRegistry.WAND.get());
        stack.set(WandComponents.WAND_WOOD.get(), Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, wood));
        stack.set(WandComponents.WAND_CORE.get(), Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, core));
        stack.set(WandComponents.WAND_FLEXIBILITY.get(), WandFlexibility.PLIANT);
        stack.set(WandComponents.WAND_LENGTH.get(), 12.5f);
        stack.set(WandComponents.WAND_INTEGRITY.get(), 1.0f);
        ModDataComponents.refreshElderWandMarker(stack);
        return stack;
    }

    private static ItemStack mastered(ItemStack wand, UUID master, float bond) {
        wand.set(WandComponents.WAND_MASTER.get(), Optional.of(master));
        wand.set(WandComponents.WAND_ALLEGIANCE_SCORE.get(), bond);
        wand.set(WandComponents.WAND_BOND_HISTORY.get(), WandBondHistory.EMPTY.withFirstMasterIfAbsent(master));
        return wand;
    }

    private static int wins(ServerPlayer master) {
        return WandComponents.getBondHistory(master.getMainHandItem()).challengerWins();
    }

    private static AABB box(GameTestHelper helper, int y) {
        Vec3 lo = Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(-2, y - 3, -2)));
        Vec3 hi = Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(7, y + 4, 7)));
        return new AABB(lo, hi);
    }

    private static List<ItemEntity> droppedWands(GameTestHelper helper, int y) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, box(helper, y),
                item -> item.getItem().getItem() instanceof at.koopro.wizardsandbeasts.item.wand.WandItem);
    }

    /** Dead wizards' wands, wherever the death left them: on the ground, or still in an inventory kept by a gamerule. */
    private static List<ItemStack> wandsOf(GameTestHelper helper, int y, ServerPlayer... players) {
        List<ItemStack> out = new ArrayList<>();
        droppedWands(helper, y).forEach(item -> out.add(item.getItem()));
        for (ServerPlayer player : players) {
            ItemStack kept = findWand(player);
            if (!kept.isEmpty()) {
                out.add(kept);
            }
        }
        return out;
    }

    private static ItemStack findWand(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof at.koopro.wizardsandbeasts.item.wand.WandItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void discardDropped(GameTestHelper helper, int y) {
        droppedWands(helper, y).forEach(ItemEntity::discard);
    }

    private static void clearReparoCooldown(ServerPlayer player) {
        PlayerSpellData data = spellData(player);
        Spell reparo = Spells.byId("reparo");
        if (reparo != null) {
            data.setCooldown(reparo.getId(), 0L);
        }
        data.setGlobalCooldownEndTick(0L);
    }

    private static String canonical(String spellId) {
        Spell spell = Spells.byId(spellId);
        return spell == null ? spellId : spell.getId();
    }

    /** The legacy allegiance record and the bonded-wand attachment are no longer written by anything. */
    private static void checkNoLegacyState(GameTestHelper helper, ServerPlayer holder, ItemStack wand) {
        check(helper, wand.get(ModDataComponents.WAND_ALLEGIANCE.get()) == null,
                () -> "the legacy allegiance record was written again: " + wand.get(ModDataComponents.WAND_ALLEGIANCE.get()));
        check(helper, holder.getData(WandAttachments.BONDED_WAND.get()).isEmpty(),
                () -> "the bonded-wand attachment was written again: " + holder.getData(WandAttachments.BONDED_WAND.get()));
    }

    private static String describe(ItemStack wand) {
        if (wand.isEmpty()) {
            return "<empty>";
        }
        return "master=" + WandComponents.getMaster(wand).map(UUID::toString).orElse("none")
                + " bond=" + WandComponents.getAllegianceScore(wand)
                + " integrity=" + WandComponents.getIntegrity(wand)
                + " history=" + WandComponents.getBondHistory(wand)
                + " elder=" + ModDataComponents.isElderWand(wand);
    }
}
