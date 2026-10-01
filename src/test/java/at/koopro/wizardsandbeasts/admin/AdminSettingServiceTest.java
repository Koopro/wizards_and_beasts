package at.koopro.wizardsandbeasts.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.access.AdminPolicy;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.AdminSettingRegistry;
import at.koopro.wizardsandbeasts.admin.config.InMemoryBinding;
import at.koopro.wizardsandbeasts.admin.config.SettingBinding;
import at.koopro.wizardsandbeasts.admin.config.SettingScope;
import at.koopro.wizardsandbeasts.admin.config.SettingTypes;
import at.koopro.wizardsandbeasts.admin.history.AdminChangeRecord;
import at.koopro.wizardsandbeasts.admin.history.InMemoryChangeHistory;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one mutation door. Every rule here is one a crafted packet would try to get around, so each is pinned
 * against an in-memory registry — no server, no {@code Config}.
 */
class AdminSettingServiceTest {

    enum Profile { LOW, MEDIUM, HIGH }

    private static final AdminContext ADMIN = AdminContext.detached(UUID.randomUUID(), "Admin",
            EnumSet.allOf(AdminCapability.class));
    private static final AdminContext OUTSIDER = AdminContext.detached(UUID.randomUUID(), "Outsider",
            EnumSet.noneOf(AdminCapability.class));
    /** May administer config, but holds no debug authority. */
    private static final AdminContext CONFIG_ONLY = AdminContext.detached(UUID.randomUUID(), "ConfigOnly",
            EnumSet.of(AdminCapability.CONFIG));

    private InMemoryBinding<Boolean> flag;
    private InMemoryBinding<Integer> count;
    private InMemoryBinding<Double> ratio;
    private InMemoryBinding<Profile> profile;
    private InMemoryBinding<Double> clientVolume;
    private InMemoryBinding<Boolean> debugSwitch;
    private InMemoryBinding<Double> low;
    private InMemoryBinding<Double> high;
    private InMemoryChangeHistory history;
    private AdminSettingService service;
    private final List<AdminResult> observed = new ArrayList<>();

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    @BeforeEach
    void setUp() {
        flag = new InMemoryBinding<>(false);
        count = new InMemoryBinding<>(5);
        ratio = new InMemoryBinding<>(1.0);
        profile = new InMemoryBinding<>(Profile.MEDIUM);
        clientVolume = new InMemoryBinding<>(0.6);
        debugSwitch = new InMemoryBinding<>(false);
        low = new InMemoryBinding<>(1.0);
        high = new InMemoryBinding<>(2.0);

        AdminSettingRegistry registry = new AdminSettingRegistry();
        registry.register(AdminSetting.builder(id("flag"), SettingTypes.bool(), flag)
                .category(AdminCategory.GAME_RULES).build());
        registry.register(AdminSetting.builder(id("count"), SettingTypes.integer(0, 10), count)
                .category(AdminCategory.GAME_RULES).build());
        registry.register(AdminSetting.builder(id("ratio"), SettingTypes.decimal(0.1, 2.0), ratio)
                .category(AdminCategory.MAGIC).dangerous(true).build());
        registry.register(AdminSetting.builder(id("profile"), SettingTypes.enumeration(Profile.class), profile)
                .category(AdminCategory.PERFORMANCE).build());
        registry.register(AdminSetting.builder(id("client_volume"), SettingTypes.decimal(0.0, 1.0), clientVolume)
                .category(AdminCategory.VISUALS).scope(SettingScope.CLIENT).build());
        registry.register(AdminSetting.builder(id("debug_switch"), SettingTypes.bool(), debugSwitch)
                .category(AdminCategory.DEBUG).build());
        // Two settings bound by a rule, the shape of the spell-power bounds: low must stay below high.
        registry.register(AdminSetting.builder(id("low"), SettingTypes.decimal(0.0, 10.0), low)
                .category(AdminCategory.MAGIC)
                .validator((candidate, reg) -> candidate < high.get() ? null : "test.conflict.low_high").build());
        registry.register(AdminSetting.builder(id("high"), SettingTypes.decimal(0.0, 10.0), high)
                .category(AdminCategory.MAGIC)
                .validator((candidate, reg) -> candidate > low.get() ? null : "test.conflict.low_high").build());
        registry.freeze();

        history = new InMemoryChangeHistory(64);
        service = new AdminSettingService(registry, history, () -> 1234L);
        observed.clear();
        service.addObserver((actor, result) -> observed.add(result));
    }

    // ── authorisation ──

    @Test
    void aNonAdministratorIsRefusedAndNothingChanges() {
        AdminResult result = service.change(OUTSIDER, id("count"), "7");
        assertEquals(AdminRejection.UNAUTHORIZED, result.rejection());
        assertEquals(5, count.get());
        assertEquals("", result.value(), "a refusal to an outsider must not disclose the value");
        assertTrue(observed.isEmpty());
        AdminChangeRecord record = history.recent(1).get(0);
        assertFalse(record.applied());
        assertEquals(AdminRejection.UNAUTHORIZED, record.rejection());
    }

    @Test
    void anOutsiderCannotTellKnownIdsFromUnknownOnes() {
        assertEquals(AdminRejection.UNAUTHORIZED, service.change(OUTSIDER, id("no_such_thing"), "1").rejection());
        assertEquals(AdminRejection.UNAUTHORIZED, service.reset(OUTSIDER, id("no_such_thing")).rejection());
    }

    @Test
    void aSettingNeedsItsOwnCapability() {
        assertEquals(AdminRejection.UNAUTHORIZED, service.change(CONFIG_ONLY, id("debug_switch"), "true").rejection());
        assertFalse(debugSwitch.get());
        assertTrue(service.change(CONFIG_ONLY, id("count"), "3").applied());
    }

    @Test
    void anOutsiderCannotResetOrUndo() {
        service.change(ADMIN, id("count"), "9");
        assertEquals(AdminRejection.UNAUTHORIZED, service.undoLast(OUTSIDER).rejection());
        assertEquals(1, service.resetAll(OUTSIDER).size());
        assertEquals(AdminRejection.UNAUTHORIZED, service.resetAll(OUTSIDER).get(0).rejection());
        assertEquals(9, count.get());
    }

    @Test
    void policyIsAllOrNothingInPhaseOne() {
        assertTrue(AdminPolicy.forVerdict(false).isEmpty());
        assertEquals(EnumSet.allOf(AdminCapability.class), AdminPolicy.forVerdict(true));
        assertFalse(OUTSIDER.canRead());
        assertTrue(ADMIN.canRead());
    }

    // ── the happy path ──

    @Test
    void anAdministratorsChangeIsAppliedRecordedAndAnnounced() {
        AdminResult result = service.change(ADMIN, id("count"), "7");
        assertTrue(result.applied());
        assertEquals("5", result.previousValue());
        assertEquals("7", result.value());
        assertEquals(7, count.get());
        assertEquals(List.of(result), observed);
        AdminChangeRecord record = history.recent(1).get(0);
        assertTrue(record.applied());
        assertEquals("5", record.oldValue());
        assertEquals("7", record.newValue());
        assertEquals(ADMIN.actorId(), record.actorId());
        assertEquals(1234L, record.timestampMillis());
    }

    @Test
    void theResultCarriesWhatTheStoreHoldsNotWhatWasSent() {
        // A store that normalises (as a real one may) — the result must report the stored value.
        SettingBinding<Double> rounding = new SettingBinding<>() {
            private double stored = 0.5;

            @Override
            public Double get() {
                return stored;
            }

            @Override
            public void set(Double value) {
                stored = Math.round(value * 100.0) / 100.0;
            }

            @Override
            public Double defaultValue() {
                return 0.5;
            }
        };
        AdminSettingRegistry registry = new AdminSettingRegistry();
        registry.register(AdminSetting.builder(id("rounded"), SettingTypes.decimal(0.0, 1.0), rounding)
                .category(AdminCategory.MAGIC).build());
        AdminSettingService local = new AdminSettingService(registry, new InMemoryChangeHistory(8), () -> 0L);
        assertEquals("0.12", local.change(ADMIN, id("rounded"), "0.123").value());
    }

    @Test
    void settingTheCurrentValueIsANoOp() {
        AdminResult result = service.change(ADMIN, id("count"), "5");
        assertEquals(AdminResult.Status.UNCHANGED, result.status());
        assertTrue(history.recent(10).isEmpty());
        assertTrue(observed.isEmpty());
    }

    @Test
    void everyKindParsesItsCanonicalText() {
        assertTrue(service.change(ADMIN, id("flag"), "TRUE").applied());
        assertTrue(flag.get());
        assertTrue(service.change(ADMIN, id("ratio"), "0.25", true).applied(), "ratio is dangerous: confirmed");
        assertEquals(0.25, ratio.get());
        assertTrue(service.change(ADMIN, id("profile"), "high").applied());
        assertEquals(Profile.HIGH, profile.get());
    }

    // ── what must be refused ──

    @Test
    void outOfRangeValuesAreRefusedAndTheRealValueReturned() {
        AdminResult tooBig = service.change(ADMIN, id("count"), "11");
        assertEquals(AdminRejection.OUT_OF_RANGE, tooBig.rejection());
        assertEquals("5", tooBig.value(), "a refusal must carry the value the UI snaps back to");
        assertEquals(AdminRejection.OUT_OF_RANGE, service.change(ADMIN, id("count"), "-1").rejection());
        assertEquals(AdminRejection.OUT_OF_RANGE, service.change(ADMIN, id("ratio"), "2.0001").rejection());
        assertEquals(AdminRejection.OUT_OF_RANGE, service.change(ADMIN, id("ratio"), "0.05").rejection());
        assertEquals(5, count.get());
        assertEquals(1.0, ratio.get());
    }

    @Test
    void malformedTextIsRefused() {
        for (String junk : List.of("", "abc", "1.5", "99999999999", "0x10", "7 8")) {
            assertEquals(AdminRejection.INVALID_VALUE, service.change(ADMIN, id("count"), junk).rejection(), junk);
        }
        for (String junk : List.of("NaN", "Infinity", "-Infinity", "0x1p3", "1.5d", "1,5", "")) {
            assertEquals(AdminRejection.INVALID_VALUE, service.change(ADMIN, id("ratio"), junk).rejection(), junk);
        }
        assertEquals(AdminRejection.INVALID_VALUE, service.change(ADMIN, id("flag"), "yes").rejection());
        assertEquals(AdminRejection.INVALID_VALUE, service.change(ADMIN, id("profile"), "ULTRA").rejection());
        assertEquals(5, count.get());
    }

    @Test
    void unknownIdsAreRefused() {
        assertEquals(AdminRejection.UNKNOWN_SETTING, service.change(ADMIN, id("no_such_thing"), "1").rejection());
        assertEquals(AdminRejection.UNKNOWN_SETTING, service.reset(ADMIN, id("no_such_thing")).rejection());
    }

    @Test
    void clientScopedSettingsAreNeverWrittenByTheServer() {
        AdminResult result = service.change(ADMIN, id("client_volume"), "0.1");
        assertEquals(AdminRejection.CLIENT_ONLY, result.rejection());
        assertEquals(0.6, clientVolume.get());
    }

    @Test
    void aCrossSettingRuleIsEnforcedWithItsReason() {
        AdminResult result = service.change(ADMIN, id("low"), "3.0");
        assertEquals(AdminRejection.CONFLICT, result.rejection());
        assertEquals("test.conflict.low_high", result.detailKey());
        assertEquals(1.0, low.get());
        assertTrue(service.change(ADMIN, id("low"), "1.5").applied());
    }

    @Test
    void anUnavailableStoreIsRefused() {
        SettingBinding<Integer> offline = new SettingBinding<>() {
            @Override
            public Integer get() {
                throw new IllegalStateException("config not loaded");
            }

            @Override
            public void set(Integer value) {
                throw new IllegalStateException("config not loaded");
            }

            @Override
            public Integer defaultValue() {
                return 1;
            }

            @Override
            public boolean available() {
                return false;
            }
        };
        AdminSettingRegistry registry = new AdminSettingRegistry();
        registry.register(AdminSetting.builder(id("offline"), SettingTypes.integer(0, 5), offline)
                .category(AdminCategory.ADVANCED).build());
        AdminSettingService local = new AdminSettingService(registry, new InMemoryChangeHistory(8), () -> 0L);
        assertEquals(AdminRejection.UNAVAILABLE, local.change(ADMIN, id("offline"), "2").rejection());
    }

    @Test
    void aFailingListenerDoesNotTurnAStoredChangeIntoAFailure() {
        InMemoryBinding<Integer> watched = new InMemoryBinding<>(0);
        AdminSettingRegistry registry = new AdminSettingRegistry();
        registry.register(AdminSetting.builder(id("watched"), SettingTypes.integer(0, 5), watched)
                .category(AdminCategory.ADVANCED)
                .onChange((before, now, actor) -> {
                    throw new IllegalStateException("listener bug");
                }).build());
        AdminSettingService local = new AdminSettingService(registry, new InMemoryChangeHistory(8), () -> 0L);
        assertTrue(local.change(ADMIN, id("watched"), "3").applied());
        assertEquals(3, watched.get());
    }

    // ── reset and undo ──

    @Test
    void resetRestoresTheDefault() {
        service.change(ADMIN, id("count"), "9");
        AdminResult result = service.reset(ADMIN, id("count"));
        assertTrue(result.applied());
        assertEquals("5", result.value());
        assertEquals(5, count.get());
        assertEquals(AdminChangeRecord.Kind.RESET, history.recent(1).get(0).kind());
    }

    @Test
    void resetSectionTouchesOnlyChangedWritableSettingsInThatSection() {
        service.change(ADMIN, id("count"), "9");
        service.change(ADMIN, id("ratio"), "1.5", true);
        List<AdminResult> results = service.resetSection(ADMIN, AdminCategory.GAME_RULES);
        assertEquals(1, results.size(), "flag was already default; only count needed a reset");
        assertEquals(5, count.get());
        assertEquals(1.5, ratio.get(), "a reset of Game Rules must leave Magic alone");
    }

    @Test
    void resetAllSkipsClientSettings() {
        service.change(ADMIN, id("count"), "9");
        service.change(ADMIN, id("profile"), "LOW");
        clientVolume.set(0.2); // a player's own copy, as far as the server is concerned
        List<AdminResult> results = service.resetAll(ADMIN);
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(AdminResult::applied));
        assertEquals(0.2, clientVolume.get());
    }

    @Test
    void undoRevertsTheLastChangeOnceAndOnlyOnce() {
        service.change(ADMIN, id("count"), "8");
        service.change(ADMIN, id("flag"), "true");
        AdminResult undone = service.undoLast(ADMIN);
        assertTrue(undone.applied());
        assertEquals(id("flag"), undone.settingId());
        assertFalse(flag.get());
        AdminResult second = service.undoLast(ADMIN);
        assertEquals(id("count"), second.settingId(), "the next undo reaches the change before");
        assertEquals(5, count.get());
        assertEquals(AdminRejection.NOTHING_TO_UNDO, service.undoLast(ADMIN).rejection(),
                "an undo is not itself undoable: there is no redo");
    }

    @Test
    void undoRefusesToDiscardANewerEdit() {
        service.change(ADMIN, id("count"), "8");
        count.set(3); // edited behind the service's back — the config file, say
        AdminResult result = service.undoLast(ADMIN);
        assertEquals(AdminRejection.CONFLICT, result.rejection());
        assertEquals(3, count.get());
    }

    @Test
    void rejectedAttemptsAreNeverUndone() {
        service.change(ADMIN, id("count"), "11");
        assertEquals(AdminRejection.NOTHING_TO_UNDO, service.undoLast(ADMIN).rejection());
    }

    @Test
    void historyIsBounded() {
        InMemoryChangeHistory small = new InMemoryChangeHistory(3);
        for (int i = 0; i < 10; i++) {
            small.record(id("count"), AdminChangeRecord.Kind.CHANGE, "a", "b", null, "x", i, false,
                    AdminRejection.UNAUTHORIZED);
        }
        assertEquals(3, small.recent(100).size());
        assertEquals(9L, small.recent(1).get(0).timestampMillis(), "newest first");
    }

    @Test
    void aHostileRequestIsNotKeptWhole() {
        String huge = "9".repeat(2000);
        service.change(ADMIN, id("count"), huge);
        assertTrue(history.recent(1).get(0).newValue().length() < 100);
    }

    // ── registry ──

    @Test
    void theRegistryResolvesBarePathsToThisModsNamespace() {
        AdminSettingRegistry registry = service.registry();
        assertEquals(id("count"), registry.find("count").id());
        assertEquals(id("count"), registry.find(WizardsAndBeastsMod.MODID + ":count").id());
        assertNull(registry.find("Not A Valid Id"));
    }

    @Test
    void aFrozenRegistryRefusesNewSettings() {
        AdminSettingRegistry registry = service.registry();
        try {
            registry.register(AdminSetting.builder(id("late"), SettingTypes.bool(), new InMemoryBinding<>(false))
                    .category(AdminCategory.ADVANCED).build());
            throw new AssertionError("a frozen registry accepted a setting");
        } catch (IllegalStateException expected) {
            // the ids a client has cached must never change under it
        }
    }
}
