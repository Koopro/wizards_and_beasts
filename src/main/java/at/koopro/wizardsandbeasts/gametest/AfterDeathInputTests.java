package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.ability.AbilityIds;
import at.koopro.wizardsandbeasts.ability.select.AbilitySelectionHelper;
import at.koopro.wizardsandbeasts.ability.select.AbilitySelectionState;
import at.koopro.wizardsandbeasts.ability.trigger.AbilityTriggerHandler;
import at.koopro.wizardsandbeasts.event.spell.ExpectoPatronumAuraHandler;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.imperio.ImperioCommand;
import at.koopro.wizardsandbeasts.spell.imperio.ImperioControlState;
import at.koopro.wizardsandbeasts.spell.imperio.ImperioServerLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.GameType;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.gameTime;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.spellData;

/**
 * Magic a dead player must not work.
 *
 * <p>The wand release refuses a corpse ({@code CastReleaseGate.CASTER_NOT_ALIVE}). The other ways a player's
 * input or state reaches a spell did not: a packet already on the wire when its sender dies is read after the
 * death, and nothing about dying stopped an ability, an Imperius command or a Patronus aura from acting for the
 * body. Each scenario runs the living case first, so a refusal afterwards is about death and nothing else.
 */
public final class AfterDeathInputTests {

    /**
     * Each scenario stands on its own height. Obscurus Grasp pulls and slows everything within six blocks and
     * the Patronus aura pushes within six, and game-test columns sit six blocks apart, so at ground level these
     * reached into their neighbours' scenarios. Above {@code SpellClashTests}' lanes (up to y 17) for the same
     * reason.
     */
    private static final int OBSCURIAL_Y = 25;
    private static final int IMPERIO_Y = 29;
    private static final int PATRONUS_Y = 33;

    private AfterDeathInputTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("after_death_obscurial_ability_refused", "after death: an ability press from a corpse",
                AfterDeathInputTests::obscurialAbilityFromCorpseIsRefused);
        tests.add("after_death_imperio_command_refused", "after death: an Imperius command from a corpse",
                AfterDeathInputTests::imperioCommandFromCorpseIsRefused);
        tests.add("after_death_patronus_aura_ends", "after death: a Patronus aura ends with its caster",
                AfterDeathInputTests::patronusAuraEndsWithItsCaster);
    }

    // ── A: the ability wheel ────────────────────────────────────────────────────────────────────

    /**
     * An Obscurial in the obscurus fires Obscurus Grasp from the ability wheel, then dies with the key still
     * pressed. The wheel's handler is the one server entry for every ability press.
     */
    private static void obscurialAbilityFromCorpseIsRefused(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "wandb-dead-obscurial", GameType.SURVIVAL);
        parkAt(helper, player, OBSCURIAL_Y);
        PlayerHeritageData heritage = player.getData(ModAttachments.HERITAGE_DATA.get());
        heritage.setSelectedHeritage(Heritage.WIZARDKIND);
        heritage.setCondition(at.koopro.wizardsandbeasts.heritage.ConditionOrigin.UNLEASHED);
        heritage.setActiveFormId("obscurial_dark");
        String grasp = Spells.OBSCURUS_GRASP.getId();
        PlayerSpellData data = spellData(player);

        helper.startSequence()
                .thenExecute(() -> {
                    AbilitySelectionHelper.select(player, AbilityIds.OBSCURUS_GRASP);
                    AbilityTriggerHandler.use(player, AbilitySelectionState.SLOT_SELECTED);
                    check(helper, data.getCastCount(grasp) == 1,
                            () -> "the living Obscurial's Grasp did not resolve, so the refusal below would prove "
                                    + "nothing: count " + data.getCastCount(grasp) + ", rejects " + data.getRejectCounts());
                    data.setCooldown(grasp, 0L);

                    player.kill(helper.getLevel());
                    check(helper, !player.isAlive(), () -> "the player survived kill()");
                    AbilityTriggerHandler.use(player, AbilitySelectionState.SLOT_SELECTED);
                    check(helper, data.getCastCount(grasp) == 1,
                            () -> "a dead Obscurial fired Obscurus Grasp: count " + data.getCastCount(grasp));
                })
                .thenExecute(() -> retire(helper, player))
                .thenSucceed();
    }

    // ── B: the Imperius command menu ────────────────────────────────────────────────────────────

    private static void imperioCommandFromCorpseIsRefused(GameTestHelper helper) {
        Runnable releaseDarkArts = WizardTestSupport.leaseModule(Module.DARK_ARTS);
        ServerPlayer caster = WizardTestSupport.placeMockPlayer(helper, "wandb-dead-imperio", GameType.SURVIVAL);
        parkAt(helper, caster, IMPERIO_Y);
        BlockPos min = new BlockPos(0, IMPERIO_Y, 0);
        BlockPos max = new BlockPos(2, IMPERIO_Y, 2);
        // The victim stands two blocks out on both axes, which crosses a chunk boundary one run in six; a
        // victim in an unforced chunk is invisible to the UUID lookup the command itself does.
        WizardTestSupport.forceChunks(helper, min, max);
        Pig victim = helper.spawn(EntityType.PIG, new BlockPos(2, IMPERIO_Y, 2));
        victim.setNoAi(true);
        victim.setNoGravity(true);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min, max))
                .thenExecute(() -> {
                    ImperioServerLogic.beginControl(helper.getLevel(), caster, victim, 400);
                    ImperioServerLogic.applyCommand(caster, ImperioCommand.ATTACK_NEAREST);
                    check(helper, command(victim) == ImperioCommand.ATTACK_NEAREST,
                            () -> "the living caster's command did not reach the victim: " + state(victim)
                                    + " | caster alive=" + caster.isAlive() + " health=" + caster.getHealth()
                                    + " removed=" + caster.isRemoved() + " | victim alive=" + victim.isAlive()
                                    + " removed=" + victim.isRemoved() + " sameLevel=" + (victim.level() == caster.level())
                                    + " darkArts=" + ModuleManager.isEnabled(Module.DARK_ARTS)
                                    + " caster=" + caster.getUUID()
                                    + " victimLookup=" + (helper.getLevel().getEntity(victim.getUUID()) != null)
                                    + " victimAt=" + victim.blockPosition() + " casterAt=" + caster.blockPosition());

                    caster.kill(helper.getLevel());
                    check(helper, !caster.isAlive(), () -> "the caster survived kill()");
                    ImperioServerLogic.applyCommand(caster, ImperioCommand.STAND_STILL);
                    check(helper, command(victim) == ImperioCommand.ATTACK_NEAREST,
                            () -> "a dead caster commanded the Imperius victim: " + state(victim));
                })
                .thenExecute(() -> {
                    victim.discard();
                    retire(helper, caster);
                    releaseDarkArts.run();
                })
                .thenSucceed();
    }

    /** {@link WizardTestSupport#parkAtOrigin} lifted to this scenario's own height. */
    private static void parkAt(GameTestHelper helper, ServerPlayer player, int y) {
        net.minecraft.core.BlockPos at = helper.absolutePos(new net.minecraft.core.BlockPos(0, y, 0));
        player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        player.setNoGravity(true);
    }

    private static ImperioCommand command(Pig victim) {
        return ImperioCommand.byOrdinal(state(victim).imperioCommandOrdinal());
    }

    private static ImperioControlState state(Pig victim) {
        return victim.getData(ModAttachments.IMPERIO_CONTROL_STATE.get());
    }

    // ── C: the Patronus aura ────────────────────────────────────────────────────────────────────

    /**
     * A Patronus aura pushes back and hurts dark-aligned things near its caster. The dark-aligned thing here is a
     * husk: undead, so dark-aligned; a husk, so it does not burn in daylight; no AI and no gravity, so the only
     * thing that can change its health is the aura. (A second player in the obscurus was tried first — the form's
     * own upkeep hurts it now and then, which read as the aura outliving its caster.) The aura must stop when its
     * caster dies, not run out its clock around the body.
     */
    private static void patronusAuraEndsWithItsCaster(GameTestHelper helper) {
        ServerPlayer caster = WizardTestSupport.placeMockPlayer(helper, "wandb-dead-patronus", GameType.SURVIVAL);
        parkAt(helper, caster, PATRONUS_Y);
        BlockPos min = new BlockPos(0, PATRONUS_Y, 0);
        BlockPos max = new BlockPos(2, PATRONUS_Y, 0);
        WizardTestSupport.forceChunks(helper, min, max);
        Husk dark = helper.spawn(EntityType.HUSK, new BlockPos(0, PATRONUS_Y, 0));
        dark.setNoAi(true);
        dark.setNoGravity(true);
        dark.teleportTo(caster.getX() + 2.0, caster.getY(), caster.getZ());
        float full = dark.getMaxHealth();

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min, max))
                .thenExecute(() -> ExpectoPatronumAuraHandler.activate(caster,
                        helper.getLevel().getServer().overworld().getGameTime() + 400))
                .thenWaitUntil(() -> check(helper, dark.getHealth() < full,
                        () -> "the living caster's aura never touched the husk"))
                .thenExecute(() -> {
                    dark.setHealth(full);
                    caster.kill(helper.getLevel());
                    check(helper, !caster.isAlive(), () -> "the caster survived kill()");
                })
                .thenIdle(25)
                .thenExecute(() -> check(helper, dark.getHealth() >= full,
                        () -> "the aura kept hurting after its caster died: husk health " + dark.getHealth()
                                + " at game tick " + gameTime(helper)))
                .thenExecute(() -> {
                    dark.discard();
                    retire(helper, caster);
                })
                .thenSucceed();
    }
}
