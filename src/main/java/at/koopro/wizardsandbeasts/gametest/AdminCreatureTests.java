package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.admin.AdminRejection;
import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.admin.AdminSettingService;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.creature.CreatureAdminService;
import at.koopro.wizardsandbeasts.admin.creature.CreatureRuleSettings;
import at.koopro.wizardsandbeasts.admin.creature.CreatureTestSpawns;
import at.koopro.wizardsandbeasts.creature.rules.CreatureRules;
import at.koopro.wizardsandbeasts.creature.rules.CreatureRulesService;
import at.koopro.wizardsandbeasts.creature.variant.CreatureVariants;
import at.koopro.wizardsandbeasts.entity.creature.HippogriffEntity;
import at.koopro.wizardsandbeasts.network.admin.AdminChangeSettingC2SPayload;
import at.koopro.wizardsandbeasts.network.admin.AdminCreatureNetworkService;
import at.koopro.wizardsandbeasts.network.admin.AdminCreaturePayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminNetworkService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.GameType;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The Creatures section's server boundary: the natural-spawn rule at the real placement check, the variant roll and
 * variant requests, test spawns (authority, server-chosen position, cleanup, no survival into a later session).
 * Rule changes are restored inside the same synchronous step, through the same service.
 */
public final class AdminCreatureTests {

    private static final AdminContext CONSOLE = AdminContext.detached(null, "game-test", EnumSet.allOf(AdminCapability.class));
    /** A creature with no placement predicate of its own, so the check's default result is "yes". */
    private static final String UNGATED = "troll";

    private AdminCreatureTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("admin_creature_natural_spawn_rule",
                "admin creatures: switching a creature's natural spawning off fails its natural placement check and nothing else",
                AdminCreatureTests::naturalSpawnRule);
        tests.add("admin_creature_variant_rules",
                "admin creatures: disabled variants are never rolled, the last one cannot be disabled, weights apply",
                AdminCreatureTests::variantRules);
        tests.add("admin_creature_unauthorised_spawn_rejected",
                "admin creatures: a non-admin can neither read the roster nor spawn or clean up test creatures",
                AdminCreatureTests::unauthorisedSpawnRejected);
        tests.add("admin_creature_test_spawn_is_server_placed",
                "admin creatures: a test spawn lands where the server chooses, validates its variant, and is cleaned up",
                AdminCreatureTests::testSpawnIsServerPlaced);
        tests.add("admin_creature_stale_test_creature_discarded",
                "admin creatures: a test creature from another session never re-enters a level",
                AdminCreatureTests::staleTestCreatureDiscarded);
        tests.add("admin_creature_detail_reads_live_data",
                "admin creatures: the detail page reads definition stats, biome-modifier spawns and variants",
                AdminCreatureTests::detailReadsLiveData);
    }

    private static AdminSettingService service() {
        return AdminSettings.service();
    }

    // ── natural spawning ──

    private static void naturalSpawnRule(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EntityType<?> troll = CreatureAdminService.typeOf(UNGATED);
        check(helper, troll != null, () -> "no troll entity type");
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        RandomSource random = RandomSource.create(1);
        try {
            check(helper, SpawnPlacements.checkSpawnRules(troll, level, EntitySpawnReason.NATURAL, pos, random),
                    () -> "the ungated creature failed its natural placement check with the rule on");
            AdminResult off = service().change(CONSOLE, CreatureRuleSettings.naturalSpawnId(UNGATED), "false");
            check(helper, off.applied() && !CreatureRules.naturalSpawn(UNGATED), () -> "switching natural spawning off did not apply: " + off);
            check(helper, !SpawnPlacements.checkSpawnRules(troll, level, EntitySpawnReason.NATURAL, pos, random),
                    () -> "a creature whose natural spawning is off passed the natural placement check");
            check(helper, !SpawnPlacements.checkSpawnRules(troll, level, EntitySpawnReason.CHUNK_GENERATION, pos, random),
                    () -> "a creature whose natural spawning is off passed the world-generation placement check");
            check(helper, !CreatureRulesService.refuses(troll, EntitySpawnReason.SPAWN_ITEM_USE)
                            && !CreatureRulesService.refuses(troll, EntitySpawnReason.COMMAND)
                            && !CreatureRulesService.refuses(troll, EntitySpawnReason.BREEDING),
                    () -> "the natural-spawn rule refused an egg, command or breeding spawn");
            check(helper, !CreatureRulesService.refuses(EntityType.ZOMBIE, EntitySpawnReason.NATURAL),
                    () -> "the natural-spawn rule touched a vanilla mob");
            helper.succeed();
        } finally {
            service().reset(CONSOLE, CreatureRuleSettings.naturalSpawnId(UNGATED), false);
        }
    }

    // ── variants ──

    private static void variantRules(GameTestHelper helper) {
        String id = "hippogriff";
        HippogriffEntity.Coat[] coats = HippogriffEntity.Coat.values();
        try {
            for (int i = 1; i < coats.length; i++) {
                AdminResult off = service().change(CONSOLE,
                        CreatureRuleSettings.variantId(id, coats[i].variantId(), CreatureRuleSettings.ENABLED), "false");
                check(helper, off.applied(), () -> "disabling a coat was refused: " + off);
            }
            AdminResult last = service().change(CONSOLE,
                    CreatureRuleSettings.variantId(id, coats[0].variantId(), CreatureRuleSettings.ENABLED), "false");
            check(helper, last.rejection() == AdminRejection.CONFLICT, () -> "the last enabled coat was disabled: " + last);
            RandomSource random = RandomSource.create(7);
            for (int i = 0; i < 200; i++) {
                HippogriffEntity.Coat rolled = CreatureVariants.roll(id, coats, random);
                check(helper, rolled == coats[0], () -> "a disabled coat was rolled: " + rolled);
            }
            AdminResult weightOutOfRange = service().change(CONSOLE,
                    CreatureRuleSettings.variantId(id, coats[0].variantId(), CreatureRuleSettings.WEIGHT), "0");
            check(helper, weightOutOfRange.rejection() == AdminRejection.OUT_OF_RANGE,
                    () -> "a zero weight was accepted: " + weightOutOfRange);
            helper.succeed();
        } finally {
            for (HippogriffEntity.Coat coat : coats) {
                service().reset(CONSOLE, CreatureRuleSettings.variantId(id, coat.variantId(), CreatureRuleSettings.ENABLED), false);
                service().reset(CONSOLE, CreatureRuleSettings.variantId(id, coat.variantId(), CreatureRuleSettings.WEIGHT), false);
            }
        }
    }

    // ── authority ──

    private static void unauthorisedSpawnRejected(GameTestHelper helper) {
        ServerPlayer outsider = WizardTestSupport.placeMockPlayer(helper, "CreatureOutsider", GameType.SURVIVAL);
        try {
            WizardTestSupport.parkAtOrigin(helper, outsider);
            CreatureTestSpawns.Outcome spawn = AdminCreatureNetworkService.spawn(outsider,
                    new AdminCreaturePayloads.SpawnRequest("unicorn", "", false));
            check(helper, !spawn.success() && spawn.messageKey().endsWith("unauthorized"),
                    () -> "a non-admin spawned a test creature: " + spawn);
            check(helper, CreatureTestSpawns.owned(helper.getLevel().getServer(), outsider.getUUID()).isEmpty(),
                    () -> "a test creature exists for a non-admin");
            check(helper, !AdminCreatureNetworkService.sendList(outsider) && !AdminCreatureNetworkService.sendDetail(outsider, "unicorn"),
                    () -> "a non-admin was sent the creature roster");
            AdminResult rule = AdminNetworkService.change(outsider, new AdminChangeSettingC2SPayload(1,
                    CreatureRuleSettings.naturalSpawnId("unicorn"), "false", true));
            check(helper, rule.rejection() == AdminRejection.UNAUTHORIZED && CreatureRules.naturalSpawn("unicorn"),
                    () -> "a non-admin changed a creature rule: " + rule);
            for (CustomPacketPayload payload : WizardTestSupport.drainClientboundPayloads(outsider)) {
                check(helper, !(payload instanceof AdminCreaturePayloads.ListReply)
                                && !(payload instanceof AdminCreaturePayloads.DetailReply),
                        () -> "a non-admin received " + payload.type().id());
            }
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, outsider);
        }
    }

    private static ServerPlayer admin(GameTestHelper helper, String name, boolean[] opped) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID listed = AdminFrameworkTests.firstAllowListedUuid();
        ServerPlayer admin = listed == null
                ? WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL)
                : WizardTestSupport.placeMockPlayer(helper, name, listed, GameType.SURVIVAL);
        if (listed == null) {
            server.getPlayerList().op(admin.nameAndId());
            opped[0] = true;
        }
        return admin;
    }

    private static void testSpawnIsServerPlaced(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        boolean[] opped = {false};
        ServerPlayer admin = admin(helper, "CreatureAdmin", opped);
        try {
            WizardTestSupport.parkAtOrigin(helper, admin);
            admin.setNoGravity(false);
            // Solid ground ahead of the admin and open air above it: the test area's own enclosure would otherwise
            // be the "wall" the server correctly refuses to spawn behind.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = 1; dz <= 7; dz++) {
                    helper.setBlock(new BlockPos(dx, 0, dz), net.minecraft.world.level.block.Blocks.STONE);
                    for (int dy = 1; dy <= 3; dy++) {
                        helper.setBlock(new BlockPos(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR);
                    }
                }
            }
            admin.setYRot(0.0f);
            admin.setXRot(0.0f);

            CreatureTestSpawns.Outcome unknown = CreatureTestSpawns.spawn(admin, "no_such_beast", null, false);
            check(helper, !unknown.success() && unknown.messageKey().endsWith("unknown_creature"),
                    () -> "an unknown creature id was spawned: " + unknown);
            CreatureTestSpawns.Outcome badVariant = CreatureTestSpawns.spawn(admin, "hippogriff", "tartan", true);
            check(helper, !badVariant.success() && badVariant.messageKey().endsWith("unknown_variant"),
                    () -> "an unknown variant was accepted: " + badVariant);
            CreatureTestSpawns.Outcome foreignVariant = CreatureTestSpawns.spawn(admin, "unicorn", "bronze", true);
            check(helper, !foreignVariant.success(), () -> "a variant of another creature was accepted: " + foreignVariant);

            CreatureTestSpawns.Outcome spawned = CreatureTestSpawns.spawn(admin, "hippogriff", "bronze", true);
            check(helper, spawned.success(), () -> "a valid test spawn failed: " + spawned);
            List<Entity> mine = CreatureTestSpawns.owned(server, admin.getUUID());
            check(helper, mine.size() == 1, () -> "expected one test creature, found " + mine.size());
            Entity creature = mine.get(0);
            check(helper, creature instanceof HippogriffEntity hippogriff && hippogriff.coat() == HippogriffEntity.Coat.BRONZE,
                    () -> "the requested variant was not applied");
            check(helper, creature.distanceTo(admin) <= 7.5 && creature.level() == admin.level(),
                    () -> "the test creature landed " + creature.distanceTo(admin) + " blocks away");
            check(helper, creature.getTags().contains(CreatureTestSpawns.TAG), () -> "the test creature was not tagged");

            // A wall right in front: nothing may be placed on the far side of it.
            CreatureTestSpawns.cleanup(admin);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 1; dy <= 3; dy++) {
                    helper.setBlock(new BlockPos(dx, dy, 2), net.minecraft.world.level.block.Blocks.STONE);
                }
            }
            CreatureTestSpawns.Outcome walled = CreatureTestSpawns.spawn(admin, "hippogriff", null, true);
            check(helper, !walled.success() && walled.messageKey().endsWith("no_space"),
                    () -> "a test creature was placed behind a wall: " + walled);

            CreatureTestSpawns.Outcome cleaned = CreatureTestSpawns.cleanup(admin);
            check(helper, cleaned.success() && CreatureTestSpawns.owned(server, admin.getUUID()).isEmpty() && creature.isRemoved(),
                    () -> "cleanup left test creatures behind: " + cleaned);
            helper.succeed();
        } finally {
            CreatureTestSpawns.removeOwned(server, admin.getUUID());
            if (opped[0]) {
                server.getPlayerList().deop(admin.nameAndId());
            }
            WizardTestSupport.retire(helper, admin);
        }
    }

    private static void staleTestCreatureDiscarded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EntityType<?> type = CreatureAdminService.typeOf("unicorn");
        check(helper, type != null, () -> "no unicorn type");
        Entity stale = type.create(level, EntitySpawnReason.COMMAND);
        check(helper, stale != null, () -> "could not create a unicorn");
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        stale.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
        stale.addTag(CreatureTestSpawns.TAG);
        stale.addTag("wb_admin_test_session:an-earlier-session");
        boolean added = level.addFreshEntity(stale);
        check(helper, !added && level.getEntity(stale.getUUID()) == null,
                () -> "a test creature from another session entered the level");
        helper.succeed();
    }

    // ── detail ──

    private static void detailReadsLiveData(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CreatureAdminService.Detail acromantula = CreatureAdminService.detail(server, "acromantula");
        check(helper, acromantula != null, () -> "no detail for the acromantula");
        check(helper, acromantula.spawns().stream().anyMatch(e -> e.biomes().contains("dark_forest") && e.weight() == 2
                        && e.minCount() == 1 && e.maxCount() == 2),
                () -> "the acromantula's biome-modifier spawn was not read: " + acromantula.spawns());
        check(helper, acromantula.conditions().contains("colony_cap"),
                () -> "the acromantula's placement conditions were not recorded: " + acromantula.conditions());
        var def = CreatureAdminService.definitionOf("acromantula");
        check(helper, def != null && acromantula.attributes().stream().anyMatch(f -> f.labelKey().endsWith("max_health")
                        && Double.parseDouble(f.value()) == def.maxHealth()),
                () -> "max health did not come from the creature definition");
        CreatureAdminService.Detail niffler = CreatureAdminService.detail(server, "niffler");
        check(helper, niffler != null && niffler.variants().size() == 4
                        && niffler.variants().stream().anyMatch(v -> v.id().equals("classic") && v.authoredWeight() == 70),
                () -> "the niffler's coats and authored weights were not read");
        check(helper, CreatureAdminService.detail(server, "not_a_creature") == null, () -> "an unknown id had a detail page");
        helper.succeed();
    }
}
