package at.koopro.wizardsandbeasts.gametest;

import com.mojang.serialization.DynamicOps;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.RegistryLayer;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;

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
        tests.add("every_registered_mob_can_be_stood_up",
                "registry: every mod mob instantiates, takes NoAI and can be discarded again",
                RegistrySyncTests::everyRegisteredMobCanBeStoodUp);
    }

    /**
     * Every mob this mod registers, created, frozen and thrown away again.
     *
     * <p>This is what {@code /wandb beast creature lineup} does, asserted: a type whose constructor throws, or
     * whose attributes were never bound, is a mob that crashes the first time anyone summons it — and until now
     * nothing checked that outside the handful of species with scenarios of their own.
     *
     * <p>Spawned at spacing 1 with {@code NoAI} and discarded in the same tick, so the crowd never ticks and
     * cannot wander into a neighbouring scenario.
     */
    private static void everyRegisteredMobCanBeStoodUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 0, 0));
        List<Entity> spawned = new ArrayList<>();
        int mobs = 0;
        try {
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (!WizardsAndBeastsMod.MODID.equals(id.getNamespace())) {
                    continue;
                }
                Entity entity;
                try {
                    entity = type.create(level, EntitySpawnReason.COMMAND);
                } catch (RuntimeException ex) {
                    helper.fail("entity type '" + id + "' threw while being created: " + ex);
                    return;
                }
                if (!(entity instanceof Mob mob)) {
                    continue;   // brooms, bolts, wards: not part of a lineup
                }
                mobs++;
                mob.setNoAi(true);
                mob.snapTo(origin.getX() + (mobs % 8), origin.getY() + 1, origin.getZ() + (mobs / 8),
                        0.0f, 0.0f);
                if (!level.addFreshEntity(mob)) {
                    helper.fail("mob '" + id + "' could not be added to the world");
                    return;
                }
                spawned.add(mob);
                if (!mob.isNoAi()) {
                    helper.fail("mob '" + id + "' would not hold NoAI, so a lineup of it would wander off");
                    return;
                }
            }

            // The roster is 107 generic beasts plus ten bespoke ones plus a few named NPCs; if this figure
            // collapses, entity registration has quietly stopped happening.
            int finalMobs = mobs;
            WizardTestSupport.check(helper, mobs >= 100,
                    () -> "only " + finalMobs + " mod mobs are registered, which means registration broke");
            helper.succeed();
        } finally {
            for (Entity entity : spawned) {
                entity.discard();
            }
        }
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
