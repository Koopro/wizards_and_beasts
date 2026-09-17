package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.stat.WandCore;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.wand.stat.WandLength;
import at.koopro.wizardsandbeasts.wand.stat.WandWood;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Everything a game test in this mod needs that is not the scenario itself.
 *
 * <p>Standing a real, usable wizard up inside a game test is most of the work of writing one, and almost
 * none of it is obvious: three separate pieces of setup exist only because a mock connection is not a
 * client, and each was found by watching a test fail in a way that had nothing to do with what it was
 * testing. They are collected here so the next test does not have to rediscover them.
 *
 * <p>The registration side is here for the same reason. 1.21.11 has no {@code @GameTest} annotation —
 * tests are entries in the {@code TEST_INSTANCE} registry, that registry is synced to every joining
 * client, and the two obvious datapack routes into it are closed to mods. {@link ScenarioTest} carries
 * the whole story and the codec the sync needs.
 */
public final class WizardTestSupport {

    /**
     * Vanilla's zero-block test structure, which ships in the client resources. Tests that do not care
     * about the world should use it and place their entities themselves; there is nothing to build and
     * nothing to keep in sync.
     */
    public static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");

    private WizardTestSupport() {}

    // ── registration ────────────────────────────────────────────────────────────────────────────

    /**
     * Hands a test class somewhere to put its scenarios without repeating the event plumbing.
     *
     * @param event       the live registration event
     * @param environment the environment every scenario in this run shares
     * @param maxTicks    timeout for each scenario
     */
    public record Registrar(RegisterGameTestsEvent event,
                            Holder<TestEnvironmentDefinition> environment,
                            int maxTicks) {

        /** Registers one scenario against {@link #EMPTY_STRUCTURE}. */
        public void add(String name, String description, Consumer<GameTestHelper> scenario) {
            add(name, description, EMPTY_STRUCTURE, scenario);
        }

        /** Registers one scenario against a named structure. */
        public void add(String name, String description, Identifier structure,
                        Consumer<GameTestHelper> scenario) {
            event.registerTest(
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, name),
                    new ScenarioTest(description, scenario,
                            new TestData<>(environment, structure, maxTicks, 0, true)));
        }
    }

    // ── a player who can actually cast ──────────────────────────────────────────────────────────

    /**
     * A real {@link ServerPlayer} in the server's player list, on a connection that behaves enough like a
     * client for the mod to work against.
     *
     * <p>{@code GameTestHelper.makeMockServerPlayerInLevel()} does almost this and cannot be used. Its
     * connection has negotiated no channels, so the first mod payload sent to the new player throws
     * {@code Payload ... may not be sent to the client} — and that happens <em>inside</em>
     * {@code placeNewPlayer}, while {@code PlayerLoggedInEvent} runs the login sync, so there is no moment
     * at which a caller could patch it up afterwards. Hence the same construction repeated here with two
     * additions:
     *
     * <ul>
     *   <li>{@code NetworkRegistry.configureMockConnection}, which NeoForge documents as "configures a mock
     *       connection for use in game tests", standing in for the channel negotiation a real client does
     *       during configuration;</li>
     *   <li>a {@code ServerboundPlayerLoadedPacket}, because {@code ServerPlayer.isInvulnerableTo} returns
     *       true for <em>every</em> damage source until the client reports that it has finished loading.
     *       Without it the test player is quietly immortal and {@code Entity.kill()} is a silent no-op —
     *       which is exactly how it presents, with no error and full health.</li>
     * </ul>
     *
     * <p>Both are properties of the harness, not of the mod: a real client negotiates its channels and
     * reports itself loaded long before any of this matters.
     */
    public static ServerPlayer placeMockPlayer(GameTestHelper helper, String name) {
        return placeMockPlayer(helper, name, GameType.CREATIVE);
    }

    /**
     * The same player in a chosen game mode.
     *
     * <p>Worth knowing before writing a scenario about damage: a creative player is invulnerable to
     * everything, and vanilla answers that in {@code isInvulnerableTo} — <em>before</em> the incoming
     * damage event — so a creative test player silently never fires one. Anything that measures what
     * a hit does needs {@link GameType#SURVIVAL}.
     */
    public static ServerPlayer placeMockPlayer(GameTestHelper helper, String name, GameType mode) {
        return placeMockPlayer(helper, name, UUID.randomUUID(), mode);
    }

    /**
     * The same player under a chosen profile id — which is what a reconnect is. The player's saved data is
     * loaded first, the way {@code PrepareSpawnTask} does it for a real client during configuration: on 1.21.11
     * {@code placeNewPlayer} loads nothing itself, so a player {@linkplain #retire retired} and placed again
     * under the same id used to come back with an empty inventory.
     */
    public static ServerPlayer placeMockPlayer(GameTestHelper helper, String name, UUID id, GameType mode) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(id, name), false);
        ServerPlayer player = new ServerPlayer(
                level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return mode;
            }
        };
        try (ProblemReporter.ScopedCollector reporter =
                     new ProblemReporter.ScopedCollector(player.problemPath(), LogUtils.getLogger())) {
            level.getServer().getPlayerList().loadPlayerData(player.nameAndId())
                    .map(tag -> TagValueInput.create(reporter, level.getServer().registryAccess(), tag))
                    .ifPresent(player::load);
        }
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        // placeNewPlayer hands out the server's default abilities, which in a game-test server are
        // creative ones — and creative abilities carry invulnerable=true whatever gameMode() says.
        player.setGameMode(mode);
        return player;
    }

    /**
     * Parks a player at the test's origin and stops it falling.
     *
     * <p>Freezing it in place rather than giving it a floor keeps the world out of tests that are not about
     * the world: with {@link #EMPTY_STRUCTURE} there is nothing to stand on, and a falling player is one
     * more thing that can go wrong between two assertions.
     */
    public static void parkAtOrigin(GameTestHelper helper, ServerPlayer player) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        player.teleportTo(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5);
        player.setNoGravity(true);
    }

    /**
     * Force-loads every chunk under a box given in test-relative block coordinates.
     *
     * <p>A test only forces its structure's own chunks — one, for {@link #EMPTY_STRUCTURE} — and tests land at
     * arbitrary offsets. An entity placed two blocks out can sit across a chunk boundary, where it is neither
     * ticked nor found: {@code ServerLevel.getEntity(UUID)} returned null for an Imperius victim standing in
     * plain sight, one run in six. Pair with {@link #checkChunksTick} as the first step of the sequence;
     * {@code GameTestRunner} unforces every forced chunk when the batch ends.
     */
    public static void forceChunks(GameTestHelper helper, BlockPos relativeMin, BlockPos relativeMax) {
        ServerLevel level = helper.getLevel();
        ChunkPos.rangeClosed(new ChunkPos(helper.absolutePos(relativeMin)), new ChunkPos(helper.absolutePos(relativeMax)))
                .forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, true));
    }

    /** Waits out {@link #forceChunks}: a forced ticket reaches entity-ticking a tick or two later. */
    public static void checkChunksTick(GameTestHelper helper, BlockPos relativeMin, BlockPos relativeMax) {
        ServerLevel level = helper.getLevel();
        BlockPos min = helper.absolutePos(relativeMin);
        boolean ticking = ChunkPos.rangeClosed(new ChunkPos(min), new ChunkPos(helper.absolutePos(relativeMax)))
                .allMatch(chunk -> level.isPositionEntityTicking(
                        new BlockPos(chunk.getMinBlockX(), min.getY(), chunk.getMinBlockZ())));
        check(helper, ticking, () -> "the chunks under the scenario never started ticking entities");
    }

    /**
     * Makes a player wandkind, so {@code SpellNetworkGuards.canUseWand} passes: {@code WIZARDKIND} declares
     * {@code canUseWand}, and the half-blood variant carries no {@code no_wand} tag.
     */
    public static void makeWandkind(ServerPlayer player) {
        PlayerHeritageData heritage = player.getData(ModAttachments.HERITAGE_DATA.get());
        heritage.setSelectedHeritage(Heritage.WIZARDKIND);
        heritage.setSelectedHeritageVariant(HeritageVariant.HALF_BLOOD);
    }

    /**
     * Puts a wand bonded to this player in their main hand.
     *
     * <p>The bond is the point. {@code WandHelper.isWandBondedTo} is checked early in the cast pipeline, and
     * an unbonded wand is refused with {@code WAND_NOT_BONDED} long before anything interesting is reached.
     */
    public static ItemStack giveBondedWand(ServerPlayer player) {
        ItemStack wand = WandItem.createWand(
                WandWood.HOLLY, WandCore.PHOENIX_FEATHER, WandLength.STANDARD, WandFlexibility.PLIANT);
        wand.set(WandComponents.WAND_MASTER.get(), Optional.of(player.getUUID()));
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        return wand;
    }

    /**
     * Gives the wand in hand its master's full bond, so a scenario about whether a cast happened is not also
     * about how the wand feels.
     *
     * <p>Originally this cancelled a 0.65% misfire the old allegiance layer added to an unbound fixture wand (a
     * misfire counts the cast but applies nothing, which failed effect assertions about one run in nine). That
     * layer no longer rolls anything; the bond now changes power and cooldown by a fixed amount, and a full bond
     * pins it.
     */
    public static void settleAllegiance(ServerPlayer player) {
        player.getMainHandItem().set(WandComponents.WAND_ALLEGIANCE_SCORE.get(), 1.0f);
    }

    /**
     * Teaches a spell and selects it in the first loadout slot.
     *
     * @param prerequisiteId also learned when it resolves, so a spell with a {@code knows} requirement works
     *                       whether or not {@code Config.enforceSpellRequirements} is on. May be {@code null}.
     * @return the spell's canonical id, which is what every keyed lookup in the cast pipeline uses
     */
    public static String learnAndSelect(GameTestHelper helper, ServerPlayer player,
                                        String spellId, String prerequisiteId) {
        Spell spell = Spells.byId(spellId);
        if (spell == null) {
            helper.fail("test spell '" + spellId + "' is not registered; this scenario cannot run without it");
        }
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        data.learnSpell(spell.getId());
        if (prerequisiteId != null) {
            Spell prerequisite = Spells.byId(prerequisiteId);
            if (prerequisite != null) {
                data.learnSpell(prerequisite.getId());
            }
        }
        data.setLoadoutSpell(0, spell.getId());
        data.setActiveSlot(0);
        return spell.getId();
    }

    /**
     * Takes a test's player back out of the player list so scenarios do not accumulate them.
     *
     * <p>Null-safe on purpose. A failed check inside a step does not stop the steps after it in the same tick, so
     * a cleanup step can run for a player the failed step never got as far as creating — and a {@code null} handed
     * to {@code PlayerList.remove} crashes the whole game-test server from inside a logout listener.
     */
    public static void retire(GameTestHelper helper, @org.jspecify.annotations.Nullable ServerPlayer player) {
        if (player != null) {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    // ── shared module switches ──────────────────────────────────────────────────────────────────

    private static final java.util.Map<at.koopro.wizardsandbeasts.module.Module, Integer> MODULE_LEASES =
            new java.util.EnumMap<>(at.koopro.wizardsandbeasts.module.Module.class);
    private static final java.util.Set<at.koopro.wizardsandbeasts.module.Module> ENABLED_BY_LEASE =
            java.util.EnumSet.noneOf(at.koopro.wizardsandbeasts.module.Module.class);

    /**
     * Holds a module on for the length of a scenario that needs it across several ticks, and returns the release.
     *
     * <p>Counted, because scenarios run side by side on one server. Two scenarios that each did "note whether it was
     * on, turn it on, turn it back off when done" raced: both noted it off, and whichever finished first turned it off
     * under the other, which then failed on a refused cast one run in a few. A module a lease turned on is turned back
     * off only when the last lease on it is released. Releasing twice is harmless.
     */
    @SuppressWarnings("deprecation") // The cache-only setter is the point: nothing is persisted or broadcast.
    public static Runnable leaseModule(at.koopro.wizardsandbeasts.module.Module module) {
        int holders = MODULE_LEASES.getOrDefault(module, 0);
        if (holders == 0 && !at.koopro.wizardsandbeasts.module.ModuleManager.isEnabled(module)) {
            at.koopro.wizardsandbeasts.module.ModuleManager.setState(module,
                    at.koopro.wizardsandbeasts.module.ModuleManager.State.ENABLED);
            ENABLED_BY_LEASE.add(module);
        }
        MODULE_LEASES.put(module, holders + 1);
        boolean[] released = {false};
        return () -> {
            if (released[0]) {
                return;
            }
            released[0] = true;
            int remaining = MODULE_LEASES.getOrDefault(module, 1) - 1;
            MODULE_LEASES.put(module, Math.max(0, remaining));
            if (remaining <= 0 && ENABLED_BY_LEASE.remove(module)) {
                at.koopro.wizardsandbeasts.module.ModuleManager.setState(module,
                        at.koopro.wizardsandbeasts.module.ModuleManager.State.DISABLED);
            }
        };
    }

    // ── small conveniences ──────────────────────────────────────────────────────────────────────

    public static PlayerSpellData spellData(ServerPlayer player) {
        return player.getData(ModAttachments.SPELL_DATA.get());
    }

    public static long gameTime(GameTestHelper helper) {
        return helper.getLevel().getGameTime();
    }

    /**
     * Everything the server has sent this player's client since the last call, as mod payloads, in send order.
     *
     * <p>A mock connection has no encoder in its pipeline, so what the server writes stays in the embedded
     * channel's outbound buffer as packet objects — which makes it the one place a game test can see what a
     * second client would have been told. Bundles are opened; vanilla packets are skipped. Draining is the
     * point: without it the buffer only grows, and an assertion about "after the release" would also see
     * everything sent before it.
     *
     * <p>Flushed first. During a server tick the game writes packets without flushing them and flushes each
     * connection once the tick is done — but only connections {@code ServerConnectionListener} owns, which a mock
     * one is not. Unflushed, a payload sent this tick sits in the pipeline where {@code readOutbound} cannot see
     * it, and appears only when some later packet happens to flush it.
     */
    public static List<CustomPacketPayload> drainClientboundPayloads(ServerPlayer player) {
        List<CustomPacketPayload> payloads = new ArrayList<>();
        if (!(player.connection.getConnection().channel() instanceof EmbeddedChannel channel)) {
            return payloads;
        }
        channel.flushOutbound();
        Object message;
        while ((message = channel.readOutbound()) != null) {
            collectPayloads(message, payloads);
        }
        return payloads;
    }

    private static void collectPayloads(Object message, List<CustomPacketPayload> into) {
        if (message instanceof ClientboundCustomPayloadPacket custom) {
            into.add(custom.payload());
        } else if (message instanceof BundlePacket<?> bundle) {
            for (Object sub : bundle.subPackets()) {
                collectPayloads(sub, into);
            }
        }
    }

    /**
     * Fails the test with a lazily-built message, so the cost of composing a diagnostic is only paid when
     * there is a failure to diagnose.
     */
    public static void check(GameTestHelper helper, boolean condition, Supplier<String> failure) {
        if (!condition) {
            helper.fail(failure.get());
        }
    }

}
