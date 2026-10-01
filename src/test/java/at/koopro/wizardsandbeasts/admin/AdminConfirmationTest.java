package at.koopro.wizardsandbeasts.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.AdminSettingRegistry;
import at.koopro.wizardsandbeasts.admin.config.InMemoryBinding;
import at.koopro.wizardsandbeasts.admin.config.SettingTypes;
import at.koopro.wizardsandbeasts.admin.history.InMemoryChangeHistory;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dangerous changes and provider-resolved settings. The server — not the panel — decides what needs
 * confirmation, so a crafted change packet without the confirmed flag cannot apply one.
 */
class AdminConfirmationTest {

    private static final AdminContext ADMIN = AdminContext.detached(UUID.randomUUID(), "Admin",
            EnumSet.allOf(AdminCapability.class));
    private static final AdminContext OUTSIDER = AdminContext.detached(UUID.randomUUID(), "Outsider",
            EnumSet.noneOf(AdminCapability.class));

    private InMemoryBinding<Boolean> curse;
    private InMemoryBinding<Double> power;
    private InMemoryBinding<Integer> dynamic;
    private InMemoryChangeHistory history;
    private AdminSettingService service;

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    @BeforeEach
    void setUp() {
        curse = new InMemoryBinding<>(false);
        power = new InMemoryBinding<>(1.0);
        dynamic = new InMemoryBinding<>(10);
        AdminSettingRegistry registry = new AdminSettingRegistry();
        // Dangerous only when switched on — like allowing the Unforgivables.
        registry.register(AdminSetting.builder(id("curse"), SettingTypes.bool(), curse)
                .category(AdminCategory.DARK_ARTS)
                .dangerRule((previous, candidate, authored, setting) -> candidate ? "test.warning.curse" : null)
                .build());
        // Dangerous only above twice the default — like "massively increasing damage".
        registry.register(AdminSetting.builder(id("power"), SettingTypes.decimal(0.0, 10.0), power)
                .category(AdminCategory.MAGIC)
                .dangerRule((previous, candidate, authored, setting) -> candidate > 2 * authored ? setting.warningKey() : null)
                .build());
        // A family resolved on demand, like one setting per spell.
        registry.addProvider(requested -> requested.getPath().equals("family/member")
                ? AdminSetting.builder(requested, SettingTypes.integer(0, 20), dynamic).category(AdminCategory.MAGIC).build()
                : null);
        registry.freeze();
        history = new InMemoryChangeHistory(32);
        service = new AdminSettingService(registry, history, () -> 0L);
    }

    @Test
    void anUnconfirmedDangerousChangeIsHeldNotApplied() {
        AdminResult held = service.change(ADMIN, id("curse"), "true");
        assertTrue(held.needsConfirmation());
        assertEquals("test.warning.curse", held.detailKey());
        assertEquals("false", held.value(), "the held result carries the real value");
        assertFalse(curse.get());
        assertTrue(history.recent(10).isEmpty(), "a question is not a change and is not recorded");
    }

    @Test
    void theConfirmedChangeIsApplied() {
        assertTrue(service.change(ADMIN, id("curse"), "true", true).applied());
        assertTrue(curse.get());
    }

    @Test
    void onlyTheDangerousDirectionAsks() {
        curse.set(true);
        assertTrue(service.change(ADMIN, id("curse"), "false").applied(), "switching it off is harmless");
        assertTrue(service.change(ADMIN, id("power"), "1.9").applied(), "below double the default is harmless");
        assertTrue(service.change(ADMIN, id("power"), "2.5").needsConfirmation(), "above double asks");
        assertEquals(1.9, power.get());
    }

    @Test
    void validationComesBeforeTheQuestion() {
        assertEquals(AdminRejection.OUT_OF_RANGE, service.change(ADMIN, id("power"), "50").rejection(),
                "an invalid value is refused outright, not offered for confirmation");
    }

    @Test
    void confirmationNeverBypassesAuthority() {
        assertEquals(AdminRejection.UNAUTHORIZED, service.change(OUTSIDER, id("curse"), "true", true).rejection());
        assertFalse(curse.get());
    }

    @Test
    void aResetCanBeDangerousToo() {
        curse = new InMemoryBinding<>(true);
        AdminSettingRegistry registry = new AdminSettingRegistry();
        registry.register(AdminSetting.builder(id("curse"), SettingTypes.bool(), curse).category(AdminCategory.DARK_ARTS)
                .dangerRule((previous, candidate, authored, setting) -> candidate ? "test.warning.curse" : null).build());
        AdminSettingService local = new AdminSettingService(registry, new InMemoryChangeHistory(8), () -> 0L);
        local.change(ADMIN, id("curse"), "false");
        assertTrue(local.reset(ADMIN, id("curse")).needsConfirmation(), "resetting re-arms it, so it asks");
        assertTrue(local.reset(ADMIN, id("curse"), true).applied());
        assertTrue(curse.get());
    }

    @Test
    void confirmationsAreHeldPerActorAndReplayedOnce() {
        AdminConfirmations.clear();
        AdminConfirmations.hold(ADMIN, List.of(new AdminConfirmations.Action(id("curse"), "true")), 1000L);
        assertTrue(AdminConfirmations.confirm(OUTSIDER, service, 1000L).isEmpty(), "another actor cannot confirm it");
        List<AdminResult> results = AdminConfirmations.confirm(ADMIN, service, 1000L);
        assertEquals(1, results.size());
        assertTrue(results.get(0).applied());
        assertTrue(AdminConfirmations.confirm(ADMIN, service, 1000L).isEmpty(), "replayed once only");
    }

    @Test
    void anExpiredConfirmationDoesNothing() {
        AdminConfirmations.clear();
        AdminConfirmations.hold(ADMIN, List.of(new AdminConfirmations.Action(id("curse"), "true")), 0L);
        assertTrue(AdminConfirmations.confirm(ADMIN, service, AdminConfirmations.WINDOW_MILLIS + 1).isEmpty());
        assertFalse(curse.get());
    }

    // ── providers ──

    @Test
    void providerSettingsGoThroughTheSameService() {
        assertTrue(service.change(ADMIN, id("family/member"), "15").applied());
        assertEquals(15, dynamic.get());
        assertEquals(AdminRejection.OUT_OF_RANGE, service.change(ADMIN, id("family/member"), "99").rejection());
        assertEquals(AdminRejection.UNKNOWN_SETTING, service.change(ADMIN, id("family/stranger"), "1").rejection());
        assertEquals(AdminRejection.UNAUTHORIZED, service.change(OUTSIDER, id("family/member"), "1").rejection());
        assertTrue(service.undoLast(ADMIN).applied(), "history and undo cover provider settings too");
        assertEquals(10, dynamic.get());
    }

    @Test
    void providerSettingsAreNotListedWithTheStaticOnes() {
        assertEquals(2, service.registry().all().size());
        assertNull(service.registry().find("family/unknown"));
    }
}
