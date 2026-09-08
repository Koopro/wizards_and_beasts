package at.koopro.wizardsandbeasts.gametest;

import com.mojang.serialization.DynamicOps;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.RegistryLayer;

import java.util.Set;

/**
 * Everything this mod puts in a synchronized registry must survive being sent to a client.
 *
 * <p>This exists because it did not. Registering a game test creates an entry in
 * {@code Registries.TEST_INSTANCE}, which is in {@code RegistryDataLoader.SYNCHRONIZED_REGISTRIES}, so the
 * whole registry is encoded and sent during a joining client's configuration phase. The scenario type had
 * no registered {@code test_instance_type}, so the encode threw and single-player would not load:
 *
 * <pre>
 * Failed to handle packet ServerboundSelectKnownPacks
 * java.lang.IllegalArgumentException: Failed to serialize
 *   ResourceKey[minecraft:test_instance / wizards_and_beasts:cast_and_release_casts_exactly_once]:
 *   Unregistered holder in ResourceKey[minecraft:root / minecraft:test_instance_type]
 * </pre>
 *
 * <p>Nothing caught it because it only happens on a <em>client</em> join: {@code runGameTestServer} has no
 * client, so seven green tests said nothing about it. Running the real packing step inside a test closes
 * that gap — and covers every future synchronized registry this mod writes to, not just game tests.
 *
 * <p>The known-pack set is deliberately empty. A client that already has a pack is sent an id instead of
 * the encoded value, so a populated set would skip exactly the work this test exists to perform.
 */
public final class RegistrySyncTests {

    private RegistrySyncTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("synced_registries_encode_for_a_client",
                "registry sync: every synchronized registry encodes",
                RegistrySyncTests::packsEveryRegistry);
    }

    private static void packsEveryRegistry(GameTestHelper helper) {
        LayeredRegistryAccess<RegistryLayer> layers = helper.getLevel().getServer().registries();
        DynamicOps<Tag> ops = layers.compositeAccess().createSerializationContext(NbtOps.INSTANCE);
        try {
            // The same call SynchronizeRegistriesTask.sendRegistries makes, with the packets discarded.
            RegistrySynchronization.packRegistries(
                    ops,
                    layers.getAccessFrom(RegistryLayer.WORLDGEN),
                    Set.of(),
                    (registry, entries) -> { });
        } catch (RuntimeException ex) {
            helper.fail("a synchronized registry failed to encode, so no client could join: " + ex);
        }
        helper.succeed();
    }
}
