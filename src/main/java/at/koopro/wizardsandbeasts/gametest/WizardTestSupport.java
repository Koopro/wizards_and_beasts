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
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
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
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(
                level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.CREATIVE;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
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

    /** Takes a test's player back out of the player list so scenarios do not accumulate them. */
    public static void retire(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    // ── small conveniences ──────────────────────────────────────────────────────────────────────

    public static PlayerSpellData spellData(ServerPlayer player) {
        return player.getData(ModAttachments.SPELL_DATA.get());
    }

    public static long gameTime(GameTestHelper helper) {
        return helper.getLevel().getGameTime();
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
