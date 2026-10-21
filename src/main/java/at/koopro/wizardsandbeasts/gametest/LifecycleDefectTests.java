package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.apparition.ApparitionServerLogic;
import at.koopro.wizardsandbeasts.apparition.sidealong.SideAlongService;
import at.koopro.wizardsandbeasts.event.felix.FelixEvents;
import at.koopro.wizardsandbeasts.felix.FelixFortune;
import at.koopro.wizardsandbeasts.item.consumable.ElixirOfLife;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.cast.CastResult;
import at.koopro.wizardsandbeasts.spell.cast.SpellCastService;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.imperio.ImperioCommand;
import at.koopro.wizardsandbeasts.spell.imperio.ImperioControlState;
import at.koopro.wizardsandbeasts.spell.imperio.ImperioServerLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * Regression scenarios for the 2026-10-06 player-visible defect pass (KNOWN_ISSUES §8a, §8c). Each one runs the
 * living or positive case first where there is one, so a failure afterwards is about the defect and nothing else.
 *
 * <p>The Imperius, Patronus and side-along scenarios stand on their own heights, above the lanes
 * {@link AfterDeathInputTests} uses (up to y 33): ATTACK_NEAREST looks sixteen blocks around the puppet and the
 * Patronus aura pushes within six.
 */
public final class LifecycleDefectTests {

    private static final int IMPERIO_Y = 45;
    private static final int PATRONUS_Y = 52;
    private static final int SIDE_ALONG_Y = 59;

    private LifecycleDefectTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.addAlone("config_is_not_rewritten_while_the_server_runs",
                "config: the common TOML is a fixed point — five seconds of ticks rewrite nothing",
                LifecycleDefectTests::configIsStable);
        tests.add("elixir_does_not_save_from_kill", "death: the Elixir of Life spares a fatal blow, never /kill",
                LifecycleDefectTests::elixirDoesNotSaveFromKill);
        tests.add("felix_does_not_save_from_kill", "death: Felix Felicis never turns aside /kill or the void",
                LifecycleDefectTests::felixDoesNotSaveFromKill);
        tests.add("imperio_mob_obeys_after_the_first_tick", "imperio: a controlled mob carries out its command",
                LifecycleDefectTests::imperioMobObeys);
        tests.add("imperio_mob_control_runs_out", "imperio: a controlled mob's control expires and frees the caster",
                LifecycleDefectTests::imperioMobControlExpires);
        tests.add("imperio_stale_saved_control_is_cleared", "imperio: a mob loaded under control nobody holds is free",
                LifecycleDefectTests::imperioStaleControlCleared);
        tests.add("patronus_without_a_memory_costs_nothing", "patronus: a charm that never forms costs no cooldown",
                LifecycleDefectTests::patronusFizzleIsFree);
        tests.add("side_along_partner_must_be_alive_and_here",
                "apparition: an accepted partner is taken only alive and in the caster's world",
                LifecycleDefectTests::sideAlongPartnerAliveAndHere);
    }

    // ── config ──────────────────────────────────────────────────────────────────────────────────

    /**
     * FML's watcher reloads the file on every change and corrects it when the spec disagrees, and a correction is
     * a change. A spec that is not a fixed point of its own correction rewrote the file about once a second.
     * Alone in its batch: admin scenarios save config on purpose.
     */
    private static void configIsStable(GameTestHelper helper) {
        Path file = FMLPaths.CONFIGDIR.get().resolve("wizards_and_beasts-common.toml");
        long[] before = new long[1];
        byte[][] content = new byte[1][];
        helper.startSequence()
                .thenExecute(() -> {
                    check(helper, Files.exists(file), () -> "no common config at " + file);
                    try {
                        before[0] = Files.getLastModifiedTime(file).toMillis();
                        content[0] = Files.readAllBytes(file);
                    } catch (java.io.IOException e) {
                        helper.fail("could not read " + file + ": " + e);
                    }
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    try {
                        long after = Files.getLastModifiedTime(file).toMillis();
                        byte[] now = Files.readAllBytes(file);
                        check(helper, after == before[0] && Arrays.equals(now, content[0]),
                                () -> "the common config was rewritten while nothing changed it ("
                                        + content[0].length + " -> " + now.length + " bytes)");
                    } catch (java.io.IOException e) {
                        helper.fail("could not re-read " + file + ": " + e);
                    }
                })
                .thenSucceed();
    }

    // ── unblockable damage ──────────────────────────────────────────────────────────────────────

    private static void elixirDoesNotSaveFromKill(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "ElixirDrinker", GameType.SURVIVAL);
        try {
            WizardTestSupport.parkAtOrigin(helper, player);
            ElixirOfLife.drink(player);
            player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), 1000f);
            check(helper, player.isAlive(), () -> "the Elixir did not spare an ordinary fatal blow (setup)");

            ElixirOfLife.drink(player);
            player.invulnerableTime = 0;
            player.kill(helper.getLevel());
            check(helper, !player.isAlive(), () -> "the Elixir of Life spared a player from /kill");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    /**
     * Felix's save is a roll (60% at full strength), so eight separate lucky players face first /kill, then an
     * ordinary lethal blow. /kill must never be turned aside; at least one ordinary blow must be (all eight failing
     * is under 0.1%) — which is also what proves the save works at all: a fresh dose's "never saved" sentinel used
     * to overflow and block every save. The handler is called with the event the damage pipeline would post.
     */
    private static void felixDoesNotSaveFromKill(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int ordinarySaves = 0;
        for (int i = 0; i < 8; i++) {
            ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "LuckyDrinker" + i, GameType.SURVIVAL);
            try {
                FelixFortune.drink(player, FelixFortune.DEFAULT_DURATION_TICKS, FelixFortune.MAX_STRENGTH,
                        FelixFortune.DEFAULT_COOLDOWN_TICKS, FelixFortune.OverdosePolicy.REFUSE);
                LivingIncomingDamageEvent kill = new LivingIncomingDamageEvent(player,
                        new DamageContainer(level.damageSources().genericKill(), Float.MAX_VALUE));
                FelixEvents.onIncomingDamage(kill);
                int n = i;
                check(helper, !kill.isCanceled(), () -> "Felix Felicis turned aside /kill (player " + n + ")");

                LivingIncomingDamageEvent blow = new LivingIncomingDamageEvent(player,
                        new DamageContainer(level.damageSources().magic(), 1000f));
                FelixEvents.onIncomingDamage(blow);
                if (blow.isCanceled()) {
                    ordinarySaves++;
                }
            } finally {
                retire(helper, player);
            }
        }
        int saves = ordinarySaves;
        check(helper, saves > 0, () -> "Felix Felicis saved none of eight lucky players from a lethal blow");
        helper.succeed();
    }

    // ── Imperio ─────────────────────────────────────────────────────────────────────────────────

    private static void imperioMobObeys(GameTestHelper helper) {
        Runnable releaseDarkArts = WizardTestSupport.leaseModule(Module.DARK_ARTS);
        ServerPlayer caster = WizardTestSupport.placeMockPlayer(helper, "ImperioCommander", GameType.SURVIVAL);
        parkAt(helper, caster, IMPERIO_Y);
        BlockPos min = new BlockPos(0, IMPERIO_Y, 0);
        BlockPos max = new BlockPos(3, IMPERIO_Y, 3);
        WizardTestSupport.forceChunks(helper, min, max);
        Pig puppet = helper.spawn(EntityType.PIG, new BlockPos(2, IMPERIO_Y, 2));
        puppet.setNoGravity(true);
        ArmorStand other = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(3, IMPERIO_Y, 2));
        other.setNoGravity(true);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min, max))
                .thenExecute(() -> {
                    ImperioServerLogic.beginControl(helper.getLevel(), caster, puppet, 400);
                    ImperioServerLogic.applyCommand(caster, ImperioCommand.ATTACK_NEAREST);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    check(helper, puppet.getTarget() == other,
                            () -> "the Imperius puppet did not turn on the nearest creature: target " + puppet.getTarget());
                    int left = puppet.getData(ModAttachments.IMPERIO_CONTROL_STATE.get()).remainingTicks();
                    check(helper, left < 400, () -> "the puppet's control did not count down: " + left);
                })
                .thenExecute(() -> {
                    ImperioServerLogic.releaseVictim(puppet);
                    puppet.discard();
                    other.discard();
                    retire(helper, caster);
                    releaseDarkArts.run();
                })
                .thenSucceed();
    }

    private static void imperioMobControlExpires(GameTestHelper helper) {
        Runnable releaseDarkArts = WizardTestSupport.leaseModule(Module.DARK_ARTS);
        ServerPlayer caster = WizardTestSupport.placeMockPlayer(helper, "ImperioBrief", GameType.SURVIVAL);
        parkAt(helper, caster, IMPERIO_Y);
        BlockPos min = new BlockPos(0, IMPERIO_Y, 0);
        BlockPos max = new BlockPos(2, IMPERIO_Y, 2);
        WizardTestSupport.forceChunks(helper, min, max);
        Pig puppet = helper.spawn(EntityType.PIG, new BlockPos(2, IMPERIO_Y, 2));
        puppet.setNoAi(true);
        puppet.setNoGravity(true);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min, max))
                .thenExecute(() -> ImperioServerLogic.beginControl(helper.getLevel(), caster, puppet, 20))
                .thenIdle(30)
                .thenExecute(() -> {
                    check(helper, !puppet.getData(ModAttachments.IMPERIO_CONTROL_STATE.get()).isControlled(),
                            () -> "a 20-tick Imperius was still holding the mob 30 ticks later");
                    check(helper, !ImperioServerLogic.holdsVictim(caster.getUUID()),
                            () -> "the caster still held an expired victim");
                })
                .thenExecute(() -> {
                    puppet.discard();
                    retire(helper, caster);
                    releaseDarkArts.run();
                })
                .thenSucceed();
    }

    /** A mob saved under control is loaded with the state but no caster mapping: it must come back free. */
    private static void imperioStaleControlCleared(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pig pig = EntityType.PIG.create(level, EntitySpawnReason.LOAD);
        check(helper, pig != null, () -> "could not create a pig");
        BlockPos at = helper.absolutePos(new BlockPos(1, IMPERIO_Y + 3, 1));
        pig.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        pig.setNoAi(true);
        pig.setNoGravity(true);
        pig.setData(ModAttachments.IMPERIO_CONTROL_STATE.get(),
                new ImperioControlState(true, UUID.randomUUID(), 400, 0f, ImperioCommand.STAND_STILL.ordinal(), false));
        level.addFreshEntity(pig);
        try {
            check(helper, !pig.getData(ModAttachments.IMPERIO_CONTROL_STATE.get()).isControlled(),
                    () -> "a mob loaded under an Imperius nobody holds stayed controlled");
            helper.succeed();
        } finally {
            pig.discard();
        }
    }

    // ── Expecto Patronum ────────────────────────────────────────────────────────────────────────

    /**
     * No happy enough memory: the charm never forms and costs nothing — no cooldown, no cast. A Patronus that does
     * form is a cast and is charged. Through {@code SpellCastService.completeWandCastRelease}, the release's own
     * path past the session gate.
     */
    private static void patronusFizzleIsFree(GameTestHelper helper) {
        ServerPlayer caster = WizardTestSupport.placeMockPlayer(helper, "PatronusHopeful", GameType.SURVIVAL);
        try {
            parkAt(helper, caster, PATRONUS_Y);
            WizardTestSupport.makeWandkind(caster);
            WizardTestSupport.giveBondedWand(caster);
            WizardTestSupport.settleAllegiance(caster);
            String patronus = WizardTestSupport.learnAndSelect(helper, caster, "expecto_patronum", "protego");
            PlayerSpellData data = caster.getData(ModAttachments.SPELL_DATA.get());
            data.setSuccessfulHits(Spells.byId("protego").getId(), Proficiency.MASTERED.getCastsRequired());

            caster.setData(ModAttachments.HAPPINESS.get(), 0f);
            caster.setData(ModAttachments.DARK_CORRUPTION.get(), 100f);
            CastResult fizzled = SpellCastService.completeWandCastRelease(caster);
            long now = helper.getLevel().getGameTime();
            check(helper, fizzled != CastResult.SUCCESS, () -> "a Patronus with no memory to hold was cast");
            check(helper, !data.isOnCooldown(patronus, now) && data.getCastCount(patronus) == 0,
                    () -> "a Patronus that never formed cost a cooldown or a cast: cooldown="
                            + data.isOnCooldown(patronus, now) + " casts=" + data.getCastCount(patronus));
            check(helper, !data.isGlobalCooldownActive(now), () -> "a Patronus that never formed cost the global cooldown");

            caster.setData(ModAttachments.HAPPINESS.get(), 100f);
            caster.setData(ModAttachments.DARK_CORRUPTION.get(), 0f);
            CastResult formed = SpellCastService.completeWandCastRelease(caster);
            check(helper, formed == CastResult.SUCCESS && data.isOnCooldown(patronus, now),
                    () -> "a Patronus that formed was not charged: result=" + formed);
            helper.succeed();
        } finally {
            helper.getLevel().getEntitiesOfClass(at.koopro.wizardsandbeasts.entity.spell.PatronusEntity.class,
                    caster.getBoundingBox().inflate(16.0)).forEach(net.minecraft.world.entity.Entity::discard);
            retire(helper, caster);
        }
    }

    // ── side-along ──────────────────────────────────────────────────────────────────────────────

    private static void sideAlongPartnerAliveAndHere(GameTestHelper helper) {
        ServerPlayer caster = WizardTestSupport.placeMockPlayer(helper, "SideAlongCaster", GameType.SURVIVAL);
        ServerPlayer elsewhere = WizardTestSupport.placeMockPlayer(helper, "SideAlongAway", GameType.SURVIVAL);
        ServerPlayer fallen = WizardTestSupport.placeMockPlayer(helper, "SideAlongFallen", GameType.SURVIVAL);
        try {
            parkAt(helper, caster, SIDE_ALONG_Y);
            Vec3 beside = caster.position().add(0.5, 0, 0);

            // An agreed partner beside the caster is taken.
            elsewhere.teleportTo(beside.x, beside.y, beside.z);
            elsewhere.setNoGravity(true);
            SideAlongService.offer(caster, elsewhere);
            check(helper, SideAlongService.accept(elsewhere), () -> "setup: the offer was not accepted");
            check(helper, ApparitionServerLogic.findSideAlongPassenger(caster) == elsewhere,
                    () -> "setup: an agreed partner beside the caster was not taken");

            // The same coordinates in another dimension are not beside anyone.
            ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
            elsewhere.teleport(new TeleportTransition(nether, beside, Vec3.ZERO, 0f, 0f, TeleportTransition.DO_NOTHING));
            check(helper, elsewhere.level() != caster.level(), () -> "setup: the partner did not change dimension");
            check(helper, ApparitionServerLogic.findSideAlongPassenger(caster) != elsewhere,
                    () -> "a partner in another dimension was taken along");
            SideAlongService.clearPartner(caster);

            // A partner on the death screen is not taken either.
            fallen.teleportTo(beside.x, beside.y, beside.z);
            fallen.setNoGravity(true);
            SideAlongService.offer(caster, fallen);
            check(helper, SideAlongService.accept(fallen), () -> "setup: the second offer was not accepted");
            fallen.kill(helper.getLevel());
            check(helper, !fallen.isAlive(), () -> "setup: the partner survived kill()");
            check(helper, ApparitionServerLogic.findSideAlongPassenger(caster) != fallen,
                    () -> "a dead partner was taken along");
            helper.succeed();
        } finally {
            SideAlongService.clearPartner(caster);
            retire(helper, fallen);
            retire(helper, elsewhere);
            retire(helper, caster);
        }
    }

    private static void parkAt(GameTestHelper helper, ServerPlayer player, int y) {
        BlockPos at = helper.absolutePos(new BlockPos(0, y, 0));
        player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        player.setNoGravity(true);
    }
}
