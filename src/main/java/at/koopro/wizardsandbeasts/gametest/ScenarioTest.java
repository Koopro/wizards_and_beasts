package at.koopro.wizardsandbeasts.gametest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * A {@link GameTestInstance} that runs a {@link Consumer}.
 *
 * <p>1.21.11 has no {@code @GameTest} annotation: a test is an entry in the {@code test_instance}
 * registry. The datapack route into that registry is closed to mods — {@code GameTestInstance.DIRECT_CODEC}
 * dispatches on {@code BuiltInRegistries.TEST_INSTANCE_TYPE}, and the obvious instance type,
 * {@code FunctionGameTestInstance}, looks its body up in {@code Registries.TEST_FUNCTION}, which is a
 * {@code registerSimple} registry frozen during {@code Bootstrap}. So scenarios are registered in code
 * through {@code RegisterGameTestsEvent.registerTest}, which takes a built instance.
 *
 * <h2>Why this class still needs a real codec</h2>
 *
 * <p>{@code Registries.TEST_INSTANCE} is in {@code RegistryDataLoader.SYNCHRONIZED_REGISTRIES}. Every
 * client that joins is sent the whole registry, and {@code RegistrySynchronization.packRegistry} encodes
 * each entry with {@code DIRECT_CODEC} — which asks the instance for its {@link #codec()} and then looks
 * that codec up by name. A codec that is not a registered {@code TEST_INSTANCE_TYPE} cannot be named, so
 * the encode throws and the client's configuration phase dies:
 *
 * <pre>
 * Failed to serialize ResourceKey[minecraft:test_instance / wizards_and_beasts:...]:
 *   Unregistered holder in ResourceKey[minecraft:root / minecraft:test_instance_type]
 * </pre>
 *
 * <p>That is not a game-test-only path: {@code neoforge.enableGameTest} is set for {@code runClient} as
 * well as {@code runGameTestServer}, so the tests are registered — and therefore synced — in an ordinary
 * dev client too. Hence {@link #CODEC}, registered as {@code wizards_and_beasts:scenario} by
 * {@link WizardsAndBeastsGameTests}.
 *
 * <h2>What the client gets</h2>
 *
 * <p>A scenario's body is a Java lambda and cannot cross the wire, so a decoded instance carries the
 * description and the {@link TestData} and nothing else. Running one fails with an explanation rather
 * than silently passing: the server holds the real instances, and a client-side copy is a listing entry,
 * not a runnable test.
 */
@NullMarked
public final class ScenarioTest extends GameTestInstance {

    /**
     * The registered {@code TEST_INSTANCE_TYPE} for these scenarios.
     *
     * <p>Registered by {@link WizardsAndBeastsGameTests}; {@link #codec()} returns this exact instance,
     * which is what lets {@code DIRECT_CODEC} find its name.
     */
    public static final MapCodec<ScenarioTest> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("description").forGetter(test -> test.description),
            TestData.CODEC.forGetter((ScenarioTest test) -> test.info())
    ).apply(instance, ScenarioTest::decoded));

    private final String description;
    private final Consumer<GameTestHelper> scenario;

    ScenarioTest(String description, Consumer<GameTestHelper> scenario,
                 TestData<Holder<TestEnvironmentDefinition>> data) {
        super(data);
        this.description = description;
        this.scenario = scenario;
    }

    /** Rebuilds a synced entry. The body is gone, so the stub says so rather than passing. */
    private static ScenarioTest decoded(String description, TestData<Holder<TestEnvironmentDefinition>> data) {
        return new ScenarioTest(description,
                helper -> helper.fail(Component.literal(
                        "This scenario was received over the network and has no body; run it on the server.")),
                data);
    }

    @Override
    public void run(GameTestHelper helper) {
        scenario.accept(helper);
    }

    @Override
    public MapCodec<? extends GameTestInstance> codec() {
        return CODEC;
    }

    @Override
    protected MutableComponent typeDescription() {
        return Component.literal(description);
    }
}
