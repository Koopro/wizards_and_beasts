package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The one place this mod's game tests are registered.
 *
 * <p>Wired from the mod constructor. NeoForge fires {@link RegisterGameTestsEvent} only when
 * {@code GameTestHooks.isGametestEnabled()} — a dev launch, or {@code runGameTestServer} — so none of this
 * costs a shipped game anything.
 *
 * <p>Each test class contributes its scenarios through a {@link WizardTestSupport.Registrar} rather than
 * touching the event, so adding a class here is one line and adding a scenario is one line inside it.
 */
public final class WizardsAndBeastsGameTests {

    /**
     * Generous enough for the longest scenario — a 160-tick Pepperup brew — with room to spare, and short
     * enough that a hang is still reported as a failure rather than stalling the run.
     */
    private static final int MAX_TICKS = 400;

    private WizardsAndBeastsGameTests() {}

    /**
     * The {@code test_instance_type} this mod's scenarios are named by.
     *
     * <p>Not optional decoration: {@code Registries.TEST_INSTANCE} is a synchronized registry, so every
     * joining client is sent every registered test and each one is encoded through its type. Without a
     * registered type the encode throws and the client never finishes configuration. See
     * {@link ScenarioTest} for the failure this fixes.
     */
    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_INSTANCE_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, WizardsAndBeastsMod.MODID);

    @SuppressWarnings("unused") // Registered for its side effect; nothing reads the holder.
    private static final DeferredHolder<MapCodec<? extends GameTestInstance>, MapCodec<ScenarioTest>> SCENARIO =
            TEST_INSTANCE_TYPES.register("scenario", () -> ScenarioTest.CODEC);

    /** Wired from the mod constructor, before any world loads. */
    public static void registerTypes(net.neoforged.bus.api.IEventBus modEventBus) {
        TEST_INSTANCE_TYPES.register(modEventBus);
    }

    public static void register(RegisterGameTestsEvent event) {
        // One environment for every scenario, with no definitions in it: no game-rule changes, no weather,
        // no time of day. Anything set here would be a variable a result could quietly depend on.
        Holder<TestEnvironmentDefinition> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "wizarding"),
                new TestEnvironmentDefinition.AllOf());

        WizardTestSupport.Registrar registrar =
                new WizardTestSupport.Registrar(event, environment, MAX_TICKS);

        WandCastLifecycleTests.contribute(registrar);
        CauldronBrewingTests.contribute(registrar);
        RegistrySyncTests.contribute(registrar);
        HeritageCommitTests.contribute(registrar);
        VampireBloodTests.contribute(registrar);
        ChoranaptyxisTests.contribute(registrar);
    }
}
