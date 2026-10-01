package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.admin.AdminRejection;
import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.admin.AdminSettingService;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.visual.BeamVisualAdminService.PresetOp;
import at.koopro.wizardsandbeasts.admin.visual.BeamVisualSettingProvider;
import at.koopro.wizardsandbeasts.network.admin.AdminChangeSettingC2SPayload;
import at.koopro.wizardsandbeasts.network.admin.AdminNetworkService;
import at.koopro.wizardsandbeasts.network.admin.AdminVisualPayloads;
import at.koopro.wizardsandbeasts.network.visual.BeamVisualSyncS2CPayload;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.visual.beam.BeamPreset;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisual;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualData;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualDefaults;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualProperty;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualService;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisuals;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The Visuals section's server boundary: beam looks as settings (validation, sync to players, reset to the code's
 * default), the preset library (built-ins protected), authority, and proof that a look change moves no gameplay value.
 * Every change is undone through the same service.
 */
public final class AdminVisualTests {

    private static final AdminContext CONSOLE = AdminContext.detached(null, "game-test", EnumSet.allOf(AdminCapability.class));
    /** An administrator in every area but visuals. */
    private static final AdminContext NO_VISUAL = AdminContext.detached(null, "game-test-no-visual",
            EnumSet.complementOf(EnumSet.of(AdminCapability.VISUAL)));
    private static final String CRUCIO = BeamVisualDefaults.CRUCIO;

    private AdminVisualTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("admin_visual_beam_look_syncs_and_resets",
                "admin visuals: a beam look change is stored, sent to every player, and a reset returns the code's default",
                AdminVisualTests::lookSyncsAndResets);
        tests.add("admin_visual_invalid_values_rejected",
                "admin visuals: malformed, out-of-range and unknown beam values are refused, and so is an admin without the visual capability",
                AdminVisualTests::invalidValuesRejected);
        tests.add("admin_visual_presets_and_builtins",
                "admin visuals: presets are created, duplicated, renamed, saved and deleted; built-in defaults refuse all of it; non-admins are refused",
                AdminVisualTests::presetsAndBuiltins);
        tests.add("admin_visual_gameplay_unchanged",
                "admin visuals: a look change moves no spell value; only impact particles scale with impact intensity",
                AdminVisualTests::gameplayUnchanged);
    }

    private static AdminSettingService service() {
        return AdminSettings.service();
    }

    private static Identifier id(BeamVisualProperty property) {
        return BeamVisualSettingProvider.id(CRUCIO, property);
    }

    private static void restore(MinecraftServer server) {
        for (BeamVisualProperty property : BeamVisualProperty.values()) {
            service().reset(CONSOLE, id(property), true);
        }
        for (String preset : BeamVisualData.get(server).presets().keySet()) {
            if (preset.startsWith("gametest")) {
                BeamVisualService.delete(server, preset);
            }
        }
    }

    private static void lookSyncsAndResets(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer watcher = WizardTestSupport.placeMockPlayer(helper, "VisualWatcher", GameType.SURVIVAL);
        try {
            BeamVisual authored = BeamVisualService.authored(CRUCIO);
            check(helper, authored != null && BeamVisualData.get(server).overridesFor(CRUCIO).isEmpty(),
                    () -> "crucio starts customised: " + BeamVisualData.get(server).overridesFor(CRUCIO));
            WizardTestSupport.drainClientboundPayloads(watcher);

            AdminResult width = service().change(CONSOLE, id(BeamVisualProperty.CORE_WIDTH), "5", true);
            AdminResult colour = service().change(CONSOLE, id(BeamVisualProperty.GLOW_COLOR), "ff00aa", true);
            check(helper, width.applied() && colour.applied(), () -> "look change refused: " + width + " / " + colour);
            check(helper, "#FF00AA".equals(colour.value()), () -> "colour not stored canonically: " + colour.value());
            BeamVisual effective = BeamVisualService.effective(server, CRUCIO);
            check(helper, effective != null && effective.coreWidth() == 5 && effective.glowColor() == 0xFF00AA,
                    () -> "effective look did not change: " + effective);

            // Every player was sent the new table — the change's own sync, not a tick loop.
            List<CustomPacketPayload> sent = WizardTestSupport.drainClientboundPayloads(watcher);
            BeamVisualSyncS2CPayload last = null;
            for (CustomPacketPayload payload : sent) {
                if (payload instanceof BeamVisualSyncS2CPayload sync) {
                    last = sync;
                }
            }
            BeamVisualSyncS2CPayload synced = last;
            check(helper, synced != null && "5".equals(synced.overrides().getOrDefault(CRUCIO, Map.of()).get("core_width"))
                            && "#FF00AA".equals(synced.overrides().get(CRUCIO).get("glow_color")),
                    () -> "the player was not sent the new look: " + synced);
            long syncs = sent.stream().filter(p -> p instanceof BeamVisualSyncS2CPayload).count();
            check(helper, syncs == 2, () -> "expected one sync per applied change, got " + syncs);

            // Setting the authored value removes the override; reset returns the default.
            AdminResult same = service().change(CONSOLE, id(BeamVisualProperty.CORE_WIDTH),
                    BeamVisualProperty.CORE_WIDTH.text(authored), true);
            check(helper, same.applied() && !BeamVisualData.get(server).overridesFor(CRUCIO).containsKey("core_width"),
                    () -> "writing the default kept an override: " + BeamVisualData.get(server).overridesFor(CRUCIO));
            AdminResult reset = service().reset(CONSOLE, id(BeamVisualProperty.GLOW_COLOR), true);
            check(helper, reset.applied() && BeamVisualData.get(server).overridesFor(CRUCIO).isEmpty(),
                    () -> "reset left an override: " + BeamVisualData.get(server).overridesFor(CRUCIO));
            check(helper, authored.equals(BeamVisualService.effective(server, CRUCIO)),
                    () -> "the default was not recovered: " + BeamVisualService.effective(server, CRUCIO));
            check(helper, "default".equals(BeamVisualService.presetLabel(server, CRUCIO)), () -> "label after reset");
            helper.succeed();
        } finally {
            restore(server);
            WizardTestSupport.retire(helper, watcher);
        }
    }

    private static void invalidValuesRejected(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try {
            record Case(BeamVisualProperty property, String text) {}
            List<Case> cases = List.of(
                    new Case(BeamVisualProperty.CORE_WIDTH, "13"), new Case(BeamVisualProperty.CORE_WIDTH, "0"),
                    new Case(BeamVisualProperty.CORE_WIDTH, "wide"), new Case(BeamVisualProperty.GLOW_SHELLS, "3"),
                    new Case(BeamVisualProperty.CORE_BRIGHTNESS, "NaN"), new Case(BeamVisualProperty.CORE_BRIGHTNESS, "1.5"),
                    new Case(BeamVisualProperty.CORE_COLOR, "#GG0000"), new Case(BeamVisualProperty.CORE_COLOR, "red"),
                    new Case(BeamVisualProperty.SHAPE, "SPIRAL"), new Case(BeamVisualProperty.ENABLED, "maybe"),
                    new Case(BeamVisualProperty.IMPACT_INTENSITY, "-1"));
            for (Case c : cases) {
                AdminResult result = service().change(CONSOLE, id(c.property()), c.text(), true);
                check(helper, result.rejected() && (result.rejection() == AdminRejection.INVALID_VALUE
                                || result.rejection() == AdminRejection.OUT_OF_RANGE),
                        () -> c + " was not refused as invalid: " + result);
            }
            check(helper, BeamVisualData.get(server).overridesFor(CRUCIO).isEmpty(),
                    () -> "a refused value was stored: " + BeamVisualData.get(server).overridesFor(CRUCIO));

            for (String path : List.of("beam/crucio/length", "beam/stupefy/core_width", "beam/crucio/trail")) {
                AdminResult unknown = service().change(CONSOLE, Identifier.fromNamespaceAndPath("wizards_and_beasts", path), "1", true);
                check(helper, unknown.rejection() == AdminRejection.UNKNOWN_SETTING, () -> path + " was not unknown: " + unknown);
            }
            AdminResult withoutCapability = service().change(NO_VISUAL, id(BeamVisualProperty.SPIN), "5", true);
            check(helper, withoutCapability.rejection() == AdminRejection.UNAUTHORIZED,
                    () -> "an admin without the visual capability changed a look: " + withoutCapability);

            ServerPlayer outsider = WizardTestSupport.placeMockPlayer(helper, "VisualOutsider", GameType.SURVIVAL);
            try {
                AdminResult packet = AdminNetworkService.change(outsider, new AdminChangeSettingC2SPayload(1,
                        id(BeamVisualProperty.SPIN), "5", true));
                check(helper, packet.rejection() == AdminRejection.UNAUTHORIZED, () -> "a non-admin changed a look: " + packet);
                WizardTestSupport.drainClientboundPayloads(outsider);
                check(helper, !AdminVisualPayloads.sendList(outsider), () -> "a non-admin was sent the beam page");
                for (CustomPacketPayload payload : WizardTestSupport.drainClientboundPayloads(outsider)) {
                    check(helper, !(payload instanceof AdminVisualPayloads.ListReply), () -> "a non-admin received the beam page");
                }
            } finally {
                WizardTestSupport.retire(helper, outsider);
            }
            check(helper, BeamVisualData.get(server).overridesFor(CRUCIO).isEmpty(), () -> "an unauthorised change stuck");
            helper.succeed();
        } finally {
            restore(server);
        }
    }

    private static void presetsAndBuiltins(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try {
            BeamVisual authored = BeamVisualService.authored(CRUCIO);
            Map<String, String> look = BeamVisuals.toText(BeamVisualProperty.SPIN.with(authored, "4").orElseThrow());

            BeamVisualService.PresetResult created = BeamVisualService.create(server, "gametest Spin", look);
            check(helper, created.ok() && "gametest_spin".equals(created.presetId()), () -> "create: " + created);
            check(helper, BeamVisualService.create(server, "GAMETEST SPIN", look).outcome() == BeamVisualService.PresetOutcome.NAME_TAKEN,
                    () -> "a second preset took the same name");
            check(helper, BeamVisualService.create(server, "bad:name", look).outcome() == BeamVisualService.PresetOutcome.INVALID_NAME,
                    () -> "an invalid name was accepted");
            check(helper, BeamVisualService.create(server, "gametest partial", Map.of("spin", "4")).outcome()
                    == BeamVisualService.PresetOutcome.INVALID_VALUES, () -> "an incomplete look was stored");

            String builtin = BeamPreset.builtinId(CRUCIO);
            BeamVisualService.PresetResult copy = BeamVisualService.duplicate(server, builtin, "gametest Copy");
            check(helper, copy.ok(), () -> "duplicating a built-in: " + copy);
            BeamPreset copied = BeamVisualService.preset(server, copy.presetId());
            check(helper, copied != null && copied.values().equals(BeamVisuals.toText(authored)),
                    () -> "the duplicate is not the default look: " + copied);

            BeamVisualService.PresetResult renamed = BeamVisualService.rename(server, copy.presetId(), "gametest Renamed");
            BeamPreset afterRename = BeamVisualService.preset(server, copy.presetId());
            check(helper, renamed.ok() && afterRename != null && "gametest Renamed".equals(afterRename.name()),
                    () -> "rename kept the id and changed the name: " + afterRename);
            check(helper, BeamVisualService.overwrite(server, copy.presetId(), look).ok()
                            && look.equals(BeamVisualService.preset(server, copy.presetId()).values()),
                    () -> "save did not store the look");

            // Built-ins: never renamed, overwritten or deleted, and still there afterwards.
            for (BeamVisualService.PresetResult refused : List.of(
                    BeamVisualService.rename(server, builtin, "gametest x"),
                    BeamVisualService.overwrite(server, builtin, look),
                    BeamVisualService.delete(server, builtin))) {
                check(helper, refused.outcome() == BeamVisualService.PresetOutcome.BUILTIN, () -> "a built-in changed: " + refused);
            }
            BeamPreset stillThere = BeamVisualService.preset(server, builtin);
            check(helper, stillThere != null && stillThere.values().equals(BeamVisuals.toText(authored)),
                    () -> "the built-in default moved: " + stillThere);

            // A spell set to exactly a preset is labelled with it.
            for (Map.Entry<String, String> entry : look.entrySet()) {
                service().change(CONSOLE, BeamVisualSettingProvider.id(CRUCIO, BeamVisualProperty.byId(entry.getKey())),
                        entry.getValue(), true);
            }
            String label = BeamVisualService.presetLabel(server, CRUCIO);
            check(helper, Set.of("gametest_spin", copy.presetId()).contains(label), () -> "label for a preset's look: " + label);

            // Deleting a preset leaves a spell set from it untouched (presets are templates).
            check(helper, BeamVisualService.delete(server, "gametest_spin").ok(), () -> "delete");
            check(helper, BeamVisualService.delete(server, "gametest_spin").outcome() == BeamVisualService.PresetOutcome.NOT_FOUND,
                    () -> "a deleted preset was deleted twice");
            BeamVisual kept = BeamVisualService.effective(server, CRUCIO);
            check(helper, kept != null && kept.spin() == 4.0, () -> "deleting a preset changed a spell: " + kept);

            // The packet door refuses non-admins before anything is stored.
            ServerPlayer outsider = WizardTestSupport.placeMockPlayer(helper, "PresetOutsider", GameType.SURVIVAL);
            try {
                String outcome = AdminVisualPayloads.runPreset(outsider,
                        new AdminVisualPayloads.PresetRequest(PresetOp.CREATE, "", "gametest outsider", look));
                check(helper, "unauthorized".equals(outcome) && !BeamVisualData.get(server).presets().containsKey("gametest_outsider"),
                        () -> "a non-admin created a preset: " + outcome);
                String delete = AdminVisualPayloads.runPreset(outsider,
                        new AdminVisualPayloads.PresetRequest(PresetOp.DELETE, copy.presetId(), "", Map.of()));
                check(helper, "unauthorized".equals(delete) && BeamVisualService.preset(server, copy.presetId()) != null,
                        () -> "a non-admin deleted a preset: " + delete);
            } finally {
                WizardTestSupport.retire(helper, outsider);
            }
            helper.succeed();
        } finally {
            restore(server);
        }
    }

    private static void gameplayUnchanged(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try {
            Spell crucio = Spells.byId(CRUCIO);
            check(helper, crucio != null, () -> "no crucio");
            float damage = crucio.getBaseDamage();
            int cooldown = crucio.getBaseCooldownTicks();
            float range = crucio.getProperties() == null ? -1 : crucio.getProperties().getRange();

            check(helper, BeamVisualService.impactCount(server, CRUCIO, 8) == 8, () -> "impact scaled with no override");
            for (BeamVisualProperty property : BeamVisualProperty.values()) {
                BeamVisual authored = BeamVisualService.authored(CRUCIO);
                String other = switch (property.kind()) {
                    case BOOL -> String.valueOf(!Boolean.parseBoolean(property.text(authored)));
                    case CHOICE -> property.text(authored).equals("LASER") ? "LIGHTNING" : "LASER";
                    case COLOR -> "#123456";
                    default -> {
                        boolean integer = property.kind() == BeamVisualProperty.Kind.INT;
                        String hi = integer ? String.valueOf((int) property.max()) : String.valueOf(property.max());
                        String lo = integer ? String.valueOf((int) property.min()) : String.valueOf(property.min());
                        yield property.text(authored).equals(property.canonical(hi)) ? lo : hi;
                    }
                };
                AdminResult result = service().change(CONSOLE, id(property), other, true);
                check(helper, result.applied(), () -> property + " → " + other + " refused: " + result);
            }
            check(helper, crucio.getBaseDamage() == damage && crucio.getBaseCooldownTicks() == cooldown
                            && (crucio.getProperties() == null || crucio.getProperties().getRange() == range),
                    () -> "a look change moved a spell value");

            service().change(CONSOLE, id(BeamVisualProperty.IMPACT_INTENSITY), "0", true);
            check(helper, BeamVisualService.impactCount(server, CRUCIO, 8) == 0, () -> "intensity 0 still sends a burst");
            service().change(CONSOLE, id(BeamVisualProperty.IMPACT_INTENSITY), "2", true);
            check(helper, BeamVisualService.impactCount(server, "wizards_and_beasts:crucio", 8) == 16,
                    () -> "intensity 2 did not double the burst");
            check(helper, BeamVisualService.impactCount(server, "stupefy", 8) == 8, () -> "a non-beam spell's impact was scaled");
            helper.succeed();
        } finally {
            restore(server);
        }
    }
}
