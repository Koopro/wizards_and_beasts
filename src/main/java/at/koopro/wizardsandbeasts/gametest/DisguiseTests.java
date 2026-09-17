package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.disguise.DisguiseState;
import at.koopro.wizardsandbeasts.disguise.DisguiseSystemAPI;
import at.koopro.wizardsandbeasts.disguise.DisguiseTargetResolver;
import at.koopro.wizardsandbeasts.polyjuice.PolyjuiceService;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Wearing somebody else's face, against a live player.
 *
 * <p>Game tests rather than unit tests because every claim here is about a real {@code ServerPlayer}:
 * the attachment has to exist, the sync has to be sendable to a connection, and the countdown has to
 * survive being written back to the same player it was read from. {@code DisguiseStateTest} covers the
 * record's arithmetic; what is left needs a server.
 *
 * <p>The <b>rendering</b> half — the skin swap and the nameplate — cannot be reached from here. It
 * lives on the client, behind {@code AvatarRenderer}, and needs eyes. It is on the manual smoke list.
 *
 * <p>Nothing below asks Mojang anything. The name-resolution path that goes to the network is
 * deliberately untested: a test that failed on a machine with no internet would teach nobody anything.
 * What <em>is</em> tested is the branch that decides whether to ask.
 */
public final class DisguiseTests {

    private DisguiseTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("disguise_applies_and_clears",
                "disguise: applying puts a face on and clearing takes it off",
                DisguiseTests::appliesAndClears);
        tests.add("disguise_indefinite_never_expires",
                "disguise: an admin disguise has no clock and survives being ticked",
                DisguiseTests::indefiniteNeverExpires);
        tests.add("disguise_timed_expires_on_its_own",
                "disguise: a timed disguise counts down and reverts itself",
                DisguiseTests::timedExpiresOnItsOwn);
        tests.add("disguise_apply_overwrites_without_refusing",
                "disguise: a second apply replaces the first, so an operator can fix a stuck face",
                DisguiseTests::applyOverwrites);
        tests.add("disguise_polyjuice_refuses_to_stack",
                "disguise: a dose is refused over any existing disguise, including an admin one",
                DisguiseTests::polyjuiceRefusesToStack);
        tests.add("disguise_resolver_answers_an_online_name_inline",
                "disguise: an online name resolves on the server thread with the account's own spelling",
                DisguiseTests::resolverAnswersOnlineNameInline);
        tests.add("disguise_resolver_refuses_an_impossible_name",
                "disguise: a name no account could have is refused rather than sent to Mojang",
                DisguiseTests::resolverRefusesImpossibleName);
    }

    // -- scenarios ---------------------------------------------------------------------------------

    private static void appliesAndClears(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "FaceWearer");
        try {
            UUID target = UUID.randomUUID();
            WizardTestSupport.check(helper, !DisguiseSystemAPI.isDisguised(player),
                    () -> "a fresh player was already wearing somebody else's face");

            DisguiseSystemAPI.applyIndefinite(player, target, "Hermione");

            DisguiseState state = DisguiseSystemAPI.get(player);
            WizardTestSupport.check(helper, state.isDisguised(),
                    () -> "applying a disguise did not produce one");
            WizardTestSupport.check(helper, state.targetId().equals(Optional.of(target)),
                    () -> "the disguise carries the wrong target uuid: " + state.targetId());
            WizardTestSupport.check(helper, "Hermione".equals(state.targetName()),
                    () -> "the disguise carries the wrong name: " + state.targetName());
            // The identity is untouched. This is the security property, and it is worth asserting on a
            // live player rather than only in the record's shape.
            WizardTestSupport.check(helper, !player.getUUID().equals(target),
                    () -> "a disguise changed the player's own uuid");
            WizardTestSupport.check(helper, "FaceWearer".equals(player.getGameProfile().name()),
                    () -> "a disguise changed the player's own profile name");

            WizardTestSupport.check(helper, DisguiseSystemAPI.clear(player, false),
                    () -> "clearing an active disguise reported that there was none");
            WizardTestSupport.check(helper, !DisguiseSystemAPI.isDisguised(player),
                    () -> "clearing left the disguise on");
            WizardTestSupport.check(helper, !DisguiseSystemAPI.clear(player, false),
                    () -> "clearing an absent disguise reported that it removed one");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void indefiniteNeverExpires(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "PermanentFace");
        try {
            DisguiseSystemAPI.applyIndefinite(player, UUID.randomUUID(), "Notch");
            WizardTestSupport.check(helper, DisguiseSystemAPI.get(player).isIndefinite(),
                    () -> "an admin disguise was given a clock");

            for (int i = 0; i < 40; i++) {
                DisguiseSystemAPI.tick(player);
            }

            DisguiseState state = DisguiseSystemAPI.get(player);
            WizardTestSupport.check(helper, state.isDisguised() && state.isIndefinite(),
                    () -> "forty ticks ended an indefinite disguise: " + state.ticksRemaining());
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void timedExpiresOnItsOwn(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "TimedFace");
        try {
            DisguiseSystemAPI.apply(player, UUID.randomUUID(), "Draco", 3, false);
            WizardTestSupport.check(helper, DisguiseSystemAPI.isDisguised(player),
                    () -> "a three-tick disguise did not start");

            DisguiseSystemAPI.tick(player);
            DisguiseSystemAPI.tick(player);
            WizardTestSupport.check(helper, DisguiseSystemAPI.isDisguised(player),
                    () -> "a three-tick disguise ended after two ticks");

            DisguiseSystemAPI.tick(player);
            WizardTestSupport.check(helper, !DisguiseSystemAPI.isDisguised(player),
                    () -> "a three-tick disguise outlived its third tick");

            // The sentinel trap: ticking past zero must not produce -1, which is INDEFINITE. Getting
            // this wrong turns every Polyjuice that runs out into a permanent disguise.
            DisguiseSystemAPI.tick(player);
            DisguiseSystemAPI.tick(player);
            WizardTestSupport.check(helper, !DisguiseSystemAPI.get(player).isIndefinite(),
                    () -> "an expired disguise ticked into the indefinite sentinel");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void applyOverwrites(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "TwoFaced");
        try {
            DisguiseSystemAPI.applyIndefinite(player, UUID.randomUUID(), "First");
            UUID second = UUID.randomUUID();
            DisguiseSystemAPI.applyIndefinite(player, second, "Second");

            DisguiseState state = DisguiseSystemAPI.get(player);
            WizardTestSupport.check(helper, "Second".equals(state.targetName())
                            && state.targetId().equals(Optional.of(second)),
                    () -> "the second apply did not replace the first: " + state.targetName());
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void polyjuiceRefusesToStack(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "DoubleDoser");
        try {
            DisguiseSystemAPI.applyIndefinite(player, UUID.randomUUID(), "AdminFace");

            PolyjuiceService.Result result = PolyjuiceService.drink(player,
                    Optional.of(UUID.randomUUID()), "PotionFace",
                    PolyjuiceService.DEFAULT_DURATION_TICKS);

            WizardTestSupport.check(helper, result == PolyjuiceService.Result.ALREADY_DISGUISED,
                    () -> "a dose taken over an admin disguise returned " + result);
            // The refusal has to leave the original alone. A dose that was refused but still wrote a
            // clock would silently put five minutes on a face that was meant not to have any.
            DisguiseState state = DisguiseSystemAPI.get(player);
            WizardTestSupport.check(helper, "AdminFace".equals(state.targetName())
                            && state.isIndefinite(),
                    () -> "a refused dose altered the disguise underneath it: "
                            + state.targetName() + "/" + state.ticksRemaining());
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void resolverAnswersOnlineNameInline(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "ResolveMe");
        try {
            AtomicReference<DisguiseTargetResolver.Target> answer = new AtomicReference<>();
            // Lower case on purpose: getPlayerByName is case-insensitive, and the resolver must hand
            // back the account's own spelling rather than what was typed, or a nameplate is wrong.
            DisguiseTargetResolver.resolve(helper.getLevel().getServer(), "resolveme", answer::set);

            DisguiseTargetResolver.Target target = answer.get();
            WizardTestSupport.check(helper, target != null,
                    () -> "an online name did not resolve on the calling thread, so it went to the network");
            WizardTestSupport.check(helper, target != null && target.id().equals(player.getUUID()),
                    () -> "an online name resolved to the wrong uuid");
            WizardTestSupport.check(helper, target != null && "ResolveMe".equals(target.name()),
                    () -> "an online name resolved with the typed spelling, not the account's: "
                            + (target == null ? "null" : target.name()));
            WizardTestSupport.check(helper, target != null && target.authentic(),
                    () -> "an online player did not resolve as authentic");
            // And the offline derivation is genuinely a different UUID, or the fallback would be
            // indistinguishable from a hit and nothing above would prove anything.
            WizardTestSupport.check(helper,
                    !UUIDUtil.createOfflinePlayerUUID("ResolveMe").equals(player.getUUID()),
                    () -> "the mock player happens to carry its own offline uuid; this test is blind");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void resolverRefusesImpossibleName(GameTestHelper helper) {
        // Seventeen characters, and a space. Neither can name any account, and asking Mojang about
        // either would be a network round trip spent proving something already known.
        WizardTestSupport.check(helper, !DisguiseTargetResolver.isUsableName("SeventeenLetters!"),
                () -> "a seventeen-character name was accepted");
        WizardTestSupport.check(helper, !DisguiseTargetResolver.isUsableName("has a space"),
                () -> "a name with a space was accepted");
        WizardTestSupport.check(helper, !DisguiseTargetResolver.isUsableName(""),
                () -> "an empty name was accepted");
        WizardTestSupport.check(helper, DisguiseTargetResolver.isUsableName("Notch"),
                () -> "an ordinary name was refused");

        AtomicReference<DisguiseTargetResolver.Target> answer = new AtomicReference<>();
        DisguiseTargetResolver.resolve(helper.getLevel().getServer(), "has a space", answer::set);
        WizardTestSupport.check(helper, answer.get() == null,
                () -> "an impossible name was dispatched to a profile lookup anyway");
        helper.succeed();
    }

    // -- fixtures ----------------------------------------------------------------------------------

    private static ServerPlayer newPlayer(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name);
        WizardTestSupport.parkAtOrigin(helper, player);
        return player;
    }
}
