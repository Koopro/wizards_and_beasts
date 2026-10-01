package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminRejection;
import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.admin.AdminSettingService;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.spell.SpellProperty;
import at.koopro.wizardsandbeasts.admin.spell.SpellSettingIds;
import at.koopro.wizardsandbeasts.admin.spell.SpellTestService;
import at.koopro.wizardsandbeasts.network.admin.AdminChangeSettingC2SPayload;
import at.koopro.wizardsandbeasts.network.admin.AdminNetworkService;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellNetworkService;
import at.koopro.wizardsandbeasts.network.spell.SpellTuningSyncS2CPayload;
import at.koopro.wizardsandbeasts.spell.cast.CastResult;
import at.koopro.wizardsandbeasts.spell.cast.SpellCastGate;
import at.koopro.wizardsandbeasts.spell.cast.SpellCastService;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.learning.SpellLearningEligibility;
import at.koopro.wizardsandbeasts.spell.tuning.SpellAvailability;
import at.koopro.wizardsandbeasts.spell.tuning.SpellOverride;
import at.koopro.wizardsandbeasts.spell.tuning.SpellTuning;
import at.koopro.wizardsandbeasts.spell.tuning.SpellTuningService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.EnumSet;
import java.util.List;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The Magic section's server boundary against the live spell registry and the real cast pipeline.
 *
 * <p>Every scenario changes shared state (spell overrides, config rules) and puts it back inside the same
 * synchronous step, so parallel scenarios never see it — and restores through the same service, so the restore
 * is itself validated.
 */
public final class AdminMagicTests {

    private static final String SPELL = "arresto_momentum";
    private static final String PREREQUISITE = "wingardium_leviosa";
    private static final AdminContext CONSOLE = AdminContext.detached(null, "game-test", EnumSet.allOf(AdminCapability.class));

    private AdminMagicTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("admin_magic_disabled_spell_cannot_cast",
                "admin magic: a disabled spell is refused at the release gate and by the beam re-check; enabling restores it",
                AdminMagicTests::disabledSpellCannotCast);
        tests.add("admin_magic_unforgivables_rule",
                "admin magic: refusing the Unforgivables refuses exactly them, and allowing them again needs confirmation",
                AdminMagicTests::unforgivablesRule);
        tests.add("admin_magic_values_are_validated",
                "admin magic: out-of-range, malformed, unknown and cyclic spell values are refused and change nothing",
                AdminMagicTests::valuesAreValidated);
        tests.add("admin_magic_unauthorised_cannot_touch_spells",
                "admin magic: a non-admin can neither change, list, reset nor test spells",
                AdminMagicTests::unauthorisedCannotTouchSpells);
        tests.add("admin_magic_prerequisite_override_is_enforced",
                "admin magic: an overridden prerequisite gates casting and learning; removing one needs confirmation",
                AdminMagicTests::prerequisiteOverrideIsEnforced);
        tests.add("admin_magic_tuning_syncs_to_clients",
                "admin magic: a changed cooldown reaches the cast accessor at once and every client in a sync",
                AdminMagicTests::tuningSyncsToClients);
        tests.add("admin_magic_test_cast_targets_are_server_chosen",
                "admin magic: a test cast hits the target the server finds, and refuses targets it cannot find or use",
                AdminMagicTests::testCastTargetsAreServerChosen);
    }

    // ── G: test casts ──

    private static void testCastTargetsAreServerChosen(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        java.util.UUID listed = AdminFrameworkTests.firstAllowListedUuid();
        ServerPlayer admin = listed == null
                ? WizardTestSupport.placeMockPlayer(helper, "AdminMagicTester", GameType.SURVIVAL)
                : WizardTestSupport.placeMockPlayer(helper, "AdminMagicTester", listed, GameType.SURVIVAL);
        boolean opped = listed == null;
        if (opped) {
            server.getPlayerList().op(admin.nameAndId());
        }
        // The dummy is spawned three blocks out, which can cross a chunk boundary: force and wait for the chunks
        // first, or whether the server can find it depends on the batch layout (tasks/lessons.md).
        BlockPos min = new BlockPos(-1, 0, -1);
        BlockPos max = new BlockPos(4, 2, 4);
        WizardTestSupport.forceChunks(helper, min, max);
        final boolean deop = opped;
        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min, max))
                .thenExecute(() -> runTestCastScenario(helper, server, admin, deop))
                .thenSucceed();
    }

    private static void runTestCastScenario(GameTestHelper helper, MinecraftServer server, ServerPlayer admin, boolean opped) {
        try {
            WizardTestSupport.parkAtOrigin(helper, admin);
            WizardTestSupport.makeWandkind(admin);
            WizardTestSupport.giveBondedWand(admin);
            String stupefy = canonical("stupefy");

            SpellTestService.Outcome nobody = SpellTestService.test(admin, stupefy, SpellTestService.TargetMode.NEAREST_DUMMY, null);
            check(helper, !nobody.success() && nobody.messageKey().endsWith("no_target"),
                    () -> "with no dummy around, the test did not refuse for lack of a target: " + nobody);

            helper.spawn(at.koopro.wizardsandbeasts.registry.ModEntities.DUELLING_DUMMY.get(), new net.minecraft.core.BlockPos(3, 1, 3));
            SpellTestService.Outcome hit = SpellTestService.test(admin, stupefy, SpellTestService.TargetMode.NEAREST_DUMMY, null);
            check(helper, hit.success(), () -> "a test cast at a dummy in range failed: " + hit);
            check(helper, !WizardTestSupport.spellData(admin).isOnCooldown(stupefy, admin.level().getGameTime()),
                    () -> "a test cast stamped a cooldown");

            SpellTestService.Outcome self = SpellTestService.test(admin, stupefy, SpellTestService.TargetMode.SELF, null);
            check(helper, !self.success() && self.messageKey().endsWith("needs_target"),
                    () -> "a projectile was test-cast at its own caster: " + self);
            SpellTestService.Outcome beam = SpellTestService.test(admin, canonical("crucio"), SpellTestService.TargetMode.NEAREST_DUMMY, null);
            check(helper, !beam.success() && beam.messageKey().endsWith("beam"), () -> "a held beam was test-cast: " + beam);
            SpellTestService.Outcome stranger = SpellTestService.test(admin, stupefy, SpellTestService.TargetMode.PLAYER, java.util.UUID.randomUUID());
            check(helper, !stranger.success() && stranger.messageKey().endsWith("no_target"),
                    () -> "a test cast accepted a player who is not here: " + stranger);
        } finally {
            if (opped) {
                server.getPlayerList().deop(admin.nameAndId());
            }
            WizardTestSupport.retire(helper, admin);
        }
    }

    private static AdminSettingService service() {
        return AdminSettings.service();
    }

    private static Identifier setting(String spell, SpellProperty property) {
        return SpellSettingIds.of(canonical(spell), property);
    }

    private static String canonical(String spell) {
        Spell resolved = Spells.byId(spell);
        return resolved == null ? spell : resolved.getId();
    }

    /** Clears every override the scenario may have left on these spells, through the store itself. */
    private static void clearOverrides(MinecraftServer server, String... spells) {
        for (String spell : spells) {
            SpellTuningService.update(server, canonical(spell), current -> SpellOverride.NONE);
        }
    }

    private static ServerPlayer readyCaster(GameTestHelper helper, String name, String spell, String prerequisite) {
        ServerPlayer caster = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, caster);
        WizardTestSupport.makeWandkind(caster);
        WizardTestSupport.giveBondedWand(caster);
        WizardTestSupport.settleAllegiance(caster);
        WizardTestSupport.learnAndSelect(helper, caster, spell, prerequisite);
        return caster;
    }

    private static SpellCastGate gate(ServerPlayer caster) {
        PlayerSpellData data = WizardTestSupport.spellData(caster);
        Spell spell = Spells.byId(data.getActiveSpellId());
        return SpellCastService.evaluateGate(caster, data, data.getActiveSpellId(), spell, caster.level().getGameTime());
    }

    // ── A: disabled spells ──

    private static void disabledSpellCannotCast(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer caster = readyCaster(helper, "AdminMagicCaster", SPELL, PREREQUISITE);
        String spellId = canonical(SPELL);
        try {
            check(helper, gate(caster) == null, () -> "setup: the test caster cannot cast before anything is disabled: " + gate(caster));

            AdminResult disabled = service().change(CONSOLE, setting(SPELL, SpellProperty.ENABLED), "false");
            check(helper, disabled.applied(), () -> "disabling the spell was not applied: " + disabled);
            check(helper, !SpellTuning.enabled(spellId), () -> "the tuning layer still reports the spell enabled");
            check(helper, gate(caster) == SpellCastGate.SPELL_DISABLED, () -> "the release gate did not refuse it: " + gate(caster));

            PlayerSpellData data = WizardTestSupport.spellData(caster);
            check(helper, SpellCastService.releaseWouldBeRefused(caster, data, spellId, Spells.byId(spellId), caster.level().getGameTime()),
                    () -> "the per-tick beam re-check would let a disabled spell channel");

            int castsBefore = data.getCastCount(spellId);
            CastResult result = SpellCastService.completeWandCastRelease(caster);
            check(helper, result == CastResult.REJECTED, () -> "a disabled spell was cast: " + result);
            check(helper, data.getCastCount(spellId) == castsBefore, () -> "a refused cast still counted");
            check(helper, !data.isOnCooldown(spellId, caster.level().getGameTime()), () -> "a refused cast stamped a cooldown");
            check(helper, data.getRejectCounts().keySet().stream().anyMatch(k -> k.startsWith("spell_disabled")),
                    () -> "the refusal was not recorded as spell_disabled: " + data.getRejectCounts());

            AdminResult enabled = service().reset(CONSOLE, setting(SPELL, SpellProperty.ENABLED), false);
            check(helper, enabled.applied() && SpellTuning.enabled(spellId), () -> "re-enabling did not apply: " + enabled);
            check(helper, gate(caster) == null, () -> "re-enabled, the gate still refuses: " + gate(caster));
            CastResult after = SpellCastService.completeWandCastRelease(caster);
            check(helper, after == CastResult.SUCCESS, () -> "re-enabled, the real cast did not succeed: " + after);
            helper.succeed();
        } finally {
            clearOverrides(server, SPELL);
            WizardTestSupport.retire(helper, caster);
        }
    }

    // ── B: the Unforgivables rule ──

    private static void unforgivablesRule(GameTestHelper helper) {
        Identifier rule = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "allow_unforgivable_curses");
        Spell avada = Spells.byId("avada_kedavra");
        Spell arresto = Spells.byId(SPELL);
        boolean original = Config.allowUnforgivableCurses;
        try {
            check(helper, avada != null && arresto != null, () -> "setup: test spells missing");
            check(helper, SpellAvailability.isUnforgivable(avada), () -> "the spell law does not class Avada Kedavra Unforgivable");

            AdminResult refuse = service().change(CONSOLE, rule, "false");
            check(helper, refuse.applied() && !SpellTuning.globals().allowUnforgivables(),
                    () -> "refusing the Unforgivables did not reach the tuning layer: " + refuse);
            check(helper, !SpellAvailability.castAllowed(avada), () -> "Avada Kedavra is still castable");
            check(helper, SpellAvailability.castAllowed(arresto), () -> "an ordinary spell was caught by the Unforgivables rule");

            AdminResult unconfirmed = service().change(CONSOLE, rule, "true");
            check(helper, unconfirmed.needsConfirmation() && !SpellTuning.globals().allowUnforgivables(),
                    () -> "allowing the Unforgivables again was applied without confirmation: " + unconfirmed);
            AdminResult confirmed = service().change(CONSOLE, rule, "true", true);
            check(helper, confirmed.applied() && SpellAvailability.castAllowed(avada),
                    () -> "the confirmed change did not re-arm them: " + confirmed);

            // A single spell switched back on is the same question, asked per spell.
            service().change(CONSOLE, setting("avada_kedavra", SpellProperty.ENABLED), "false");
            AdminResult rearm = service().change(CONSOLE, setting("avada_kedavra", SpellProperty.ENABLED), "true");
            check(helper, rearm.needsConfirmation(), () -> "re-enabling one Unforgivable did not ask: " + rearm);
            helper.succeed();
        } finally {
            service().change(CONSOLE, rule, Boolean.toString(original), true);
            clearOverrides(helper.getLevel().getServer(), "avada_kedavra");
        }
    }

    // ── C: validation ──

    private static void valuesAreValidated(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try {
            expect(helper, service().change(CONSOLE, setting(SPELL, SpellProperty.COOLDOWN_TICKS), "-5"), AdminRejection.OUT_OF_RANGE);
            expect(helper, service().change(CONSOLE, setting(SPELL, SpellProperty.COOLDOWN_TICKS), "soon"), AdminRejection.INVALID_VALUE);
            expect(helper, service().change(CONSOLE, setting("stupefy", SpellProperty.DAMAGE), "5000"), AdminRejection.OUT_OF_RANGE);
            // Lumos deals no damage, so it has no damage setting at all.
            expect(helper, service().change(CONSOLE, setting("lumos", SpellProperty.DAMAGE), "4"), AdminRejection.UNKNOWN_SETTING);
            expect(helper, service().change(CONSOLE, setting("no_such_spell", SpellProperty.ENABLED), "false"), AdminRejection.UNKNOWN_SETTING);
            expect(helper, service().change(CONSOLE, setting(SPELL, SpellProperty.PREREQUISITE), "no_such_spell"), AdminRejection.CONFLICT);
            expect(helper, service().change(CONSOLE, setting(SPELL, SpellProperty.PREREQUISITE), "lumos@grandmaster"), AdminRejection.INVALID_VALUE);
            // A prerequisite that leads back to the spell itself would make it unlearnable for everyone.
            expect(helper, service().change(CONSOLE, setting(SPELL, SpellProperty.PREREQUISITE), SPELL), AdminRejection.CONFLICT);
            AdminResult toLeviosa = service().change(CONSOLE, setting(PREREQUISITE, SpellProperty.PREREQUISITE), SPELL);
            check(helper, toLeviosa.rejection() == AdminRejection.CONFLICT
                            && "admin.wizards_and_beasts.conflict.prerequisite_cycle".equals(toLeviosa.detailKey()),
                    () -> "a two-spell prerequisite cycle was not caught: " + toLeviosa);
            expect(helper, service().change(CONSOLE, setting(SPELL, SpellProperty.REQUIRED_SKILL), "no_such_skill"), AdminRejection.CONFLICT);
            check(helper, !SpellTuning.local().overrides().containsKey(canonical(SPELL))
                            && !SpellTuning.local().overrides().containsKey(canonical("stupefy")),
                    () -> "a refused value left an override behind: " + SpellTuning.local().overrides().keySet());
            helper.succeed();
        } finally {
            clearOverrides(server, SPELL, PREREQUISITE, "stupefy");
        }
    }

    private static void expect(GameTestHelper helper, AdminResult result, AdminRejection expected) {
        check(helper, result.rejection() == expected, () -> "expected " + expected + " for " + result.settingId() + ", got " + result);
    }

    // ── D: authority ──

    private static void unauthorisedCannotTouchSpells(GameTestHelper helper) {
        ServerPlayer outsider = WizardTestSupport.placeMockPlayer(helper, "AdminMagicOutsider", GameType.SURVIVAL);
        try {
            AdminResult change = AdminNetworkService.change(outsider,
                    new AdminChangeSettingC2SPayload(1, setting(SPELL, SpellProperty.ENABLED), "false", true));
            check(helper, change.rejection() == AdminRejection.UNAUTHORIZED, () -> "a non-admin changed a spell: " + change);
            check(helper, SpellTuning.enabled(canonical(SPELL)), () -> "the spell was disabled by a non-admin");
            check(helper, !AdminSpellNetworkService.sendList(outsider), () -> "a non-admin was sent the spell list");
            check(helper, !AdminSpellNetworkService.sendDetail(outsider, canonical(SPELL)), () -> "a non-admin was sent a spell page");
            SpellTestService.Outcome test = SpellTestService.test(outsider, canonical("stupefy"), SpellTestService.TargetMode.LOOKED_AT, null);
            check(helper, !test.success() && test.messageKey().endsWith("unauthorized"), () -> "a non-admin test-cast: " + test);
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, outsider);
        }
    }

    // ── E: prerequisites ──

    private static void prerequisiteOverrideIsEnforced(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer caster = readyCaster(helper, "AdminMagicStudent", SPELL, PREREQUISITE);
        boolean enforced = Config.enforceSpellRequirements;
        try {
            Config.enforceSpellRequirements = true;
            check(helper, gate(caster) == null, () -> "setup: with the authored prerequisite known, the cast is refused: " + gate(caster));

            AdminResult lumos = service().change(CONSOLE, setting(SPELL, SpellProperty.PREREQUISITE), "lumos");
            check(helper, lumos.applied(), () -> "a valid prerequisite override was refused: " + lumos);
            check(helper, gate(caster) == SpellCastGate.REQUIREMENTS_UNMET,
                    () -> "the overridden prerequisite is not enforced at the cast: " + gate(caster));
            check(helper, !SpellLearningEligibility.evaluate(null, Spells.byId(SPELL), new PlayerSpellData(), null).learnable(),
                    () -> "the overridden prerequisite is not enforced for learning");

            WizardTestSupport.spellData(caster).learnSpell(canonical("lumos"));
            check(helper, gate(caster) == null, () -> "knowing the new prerequisite did not open the cast: " + gate(caster));

            AdminResult none = service().change(CONSOLE, setting(SPELL, SpellProperty.PREREQUISITE), "none");
            check(helper, none.needsConfirmation(), () -> "removing an authored prerequisite did not ask: " + none);
            helper.succeed();
        } finally {
            Config.enforceSpellRequirements = enforced;
            clearOverrides(server, SPELL);
            WizardTestSupport.retire(helper, caster);
        }
    }

    // ── F: sync ──

    private static void tuningSyncsToClients(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer watcher = WizardTestSupport.placeMockPlayer(helper, "AdminMagicWatcher", GameType.SURVIVAL);
        Spell spell = Spells.byId(SPELL);
        try {
            check(helper, spell != null, () -> "setup: test spell missing");
            WizardTestSupport.drainClientboundPayloads(watcher);
            int authored = spell.getAuthoredCooldownTicks();
            int target = authored + 7;
            AdminResult result = service().change(CONSOLE, setting(SPELL, SpellProperty.COOLDOWN_TICKS), Integer.toString(target));
            check(helper, result.applied() && result.value().equals(Integer.toString(target)), () -> "the cooldown change failed: " + result);
            check(helper, spell.getBaseCooldownTicks() == target,
                    () -> "the cast accessor does not read the new cooldown: " + spell.getBaseCooldownTicks());

            List<CustomPacketPayload> sent = WizardTestSupport.drainClientboundPayloads(watcher);
            check(helper, sent.stream().anyMatch(p -> p instanceof SpellTuningSyncS2CPayload sync
                            && sync.snapshot().override(spell.getId()).cooldownTicks().orElse(-1) == target),
                    () -> "no client was told the new cooldown: " + sent);

            AdminResult held = service().change(CONSOLE, setting(SPELL, SpellProperty.COOLDOWN_TICKS), "0");
            check(helper, held.needsConfirmation() && spell.getBaseCooldownTicks() == target,
                    () -> "slashing a cooldown below half did not ask first: " + held);

            AdminResult reset = service().reset(CONSOLE, setting(SPELL, SpellProperty.COOLDOWN_TICKS), false);
            check(helper, reset.applied() && spell.getBaseCooldownTicks() == authored
                            && !SpellTuning.local().overrides().containsKey(spell.getId()),
                    () -> "resetting to the authored value left an override: " + SpellTuning.local().overrides());
            helper.succeed();
        } finally {
            clearOverrides(server, SPELL);
            WizardTestSupport.retire(helper, watcher);
        }
    }
}
