package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.entity.spell.ProtegoShieldEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoTier;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoWardManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * The two holes the Shield Charm redesign left open, closed: beams that no ward could see, and
 * ordinary projectiles that flew through a dome to be paid for at the body inside.
 *
 * <p>Beam damage used to be dealt as bare {@code magic()} — no attacker, no position — so a warded
 * player took a Cruciatus beam in full. These drive the <em>real</em> channel rather than hand-rolling
 * a damage source, because the thing under test is the attribution at the cast site.
 *
 * <p>Each scenario gets its own height: they reach several blocks out and a game-test grid puts its
 * neighbours closer than that.
 */
public final class ProtegoWardBeamTests {

    private static final float POOL = 30.0f;
    private static final int LIFETIME = 400;
    /** Crucio's ramp only starts once the curse has held one victim for 30 channel ticks. */
    private static final int CRUCIO_HOLD_TICKS = 46;
    /** The jet hurts a fire-immune creature every fourth channel tick. */
    private static final int JET_TICKS = 14;
    /** What the Killing Curse's beam applies, from {@code WandBeamSpellHandlers.handleAvada}. */
    private static final float AVADA_DAMAGE = 1_000_000f;

    private ProtegoWardBeamTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("protego_absorbs_a_crucio_beam", "protego: a ward stands in front of a Cruciatus beam",
                ProtegoWardBeamTests::absorbsACrucioBeam);
        tests.add("protego_never_stops_the_killing_curse", "protego: nothing stops Avada Kedavra",
                ProtegoWardBeamTests::neverStopsTheKillingCurse);
        tests.add("protego_turns_an_arrow_at_the_wall", "protego: an arrow bounces off the ward, not off the body",
                ProtegoWardBeamTests::turnsAnArrowAtTheWall);
        tests.add("protego_aguamenti_jet_names_its_caster", "aguamenti: the jet's damage carries the caster",
                ProtegoWardBeamTests::aguamentiJetNamesItsCaster);
    }

    // ── 8b.1: a beam is something a ward can see ────────────────────────────────────────────────

    private static void absorbsACrucioBeam(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ModuleState darkArtsBefore = swapDarkArts(ModuleState.ENABLED);

        Vec3 casterAt = new Vec3(1.5, 2.0, 1.5);
        Vec3 victimAt = new Vec3(1.5, 2.0, 3.5);
        ServerPlayer caster = channeller(helper, "wandb-crucio-caster", casterAt, "crucio", "incendio");
        // crucio.json gates on incendio at MASTERED; the beam tick enforces it.
        masterPrerequisite(caster, "incendio");
        ServerPlayer victim = warded(helper, "wandb-crucio-victim", victimAt);
        ProtegoShieldEntity shield = raise(helper, victim, ProtegoTier.MAXIMA);
        aimAt(caster, victim);

        helper.startSequence()
                .thenExecute(() -> caster.gameMode.useItem(caster, level, caster.getMainHandItem(), InteractionHand.MAIN_HAND))
                // A mock player is never ticked by the server, so the hold has to be driven by hand.
                .thenExecuteFor(CRUCIO_HOLD_TICKS, () -> {
                    aimAt(caster, victim);
                    caster.doTick();
                })
                .thenExecute(() -> {
                    check(helper, shield.getIntegrity() < POOL,
                            () -> "a Cruciatus beam held for " + CRUCIO_HOLD_TICKS + " ticks cost the ward "
                                    + "nothing: " + describe(shield) + " — beam damage is invisible to a shield again");
                    check(helper, victim.getHealth() >= victim.getMaxHealth(),
                            () -> "the beam reached the victim through a ward with "
                                    + shield.getIntegrity() + " integrity left (health " + victim.getHealth() + ")");
                })
                .thenExecute(() -> {
                    caster.releaseUsingItem();
                    swapDarkArts(darkArtsBefore);
                    cleanUp(helper, victim, shield);
                    retire(helper, caster);
                })
                .thenSucceed();
    }

    /** Canon, and the one exemption the ward keeps: the Killing Curse is not stopped by anything. */
    private static void neverStopsTheKillingCurse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 at = new Vec3(1.5, 6.0, 1.5);
        ServerPlayer victim = warded(helper, "wandb-avada-victim", at);
        ServerPlayer caster = warded(helper, "wandb-avada-caster", at.add(2.0, 0.0, 0.0));
        ProtegoShieldEntity shield = raise(helper, victim, ProtegoTier.HORRIBILIS);

        helper.startSequence()
                .thenExecute(() -> {
                    victim.invulnerableTime = 0;
                    // Exactly what handleAvada applies, through the ordinary damage path the ward listens on.
                    victim.hurtServer(level, level.damageSources().playerAttack(caster), AVADA_DAMAGE);

                    check(helper, shield.getIntegrity() == POOL,
                            () -> "the ward spent integrity on the Killing Curse: " + describe(shield));
                    check(helper, victim.getHealth() <= 0.0f,
                            () -> "the Killing Curse left the warded victim at " + victim.getHealth() + " health");
                })
                .thenExecute(() -> {
                    retire(helper, caster);
                    cleanUp(helper, victim, shield);
                })
                .thenSucceed();
    }

    // ── 8b.3: ordinary projectiles are turned at the wall ───────────────────────────────────────

    private static void turnsAnArrowAtTheWall(GameTestHelper helper) {
        Vec3 at = new Vec3(1.5, 10.0, 1.5);
        ServerPlayer victim = warded(helper, "wandb-arrow-victim", at);
        ProtegoShieldEntity shield = raise(helper, victim, ProtegoTier.MAXIMA);
        double radius = shield.tier().radius();

        // Loosed from outside the dome, straight at the body inside it.
        Vec3 from = at.add(radius + 2.0, 1.0, 0.0);
        loadThePath(helper, from, at);
        Arrow arrow = helper.spawn(EntityType.ARROW, from);
        arrow.setNoGravity(true);

        helper.startSequence()
                // An entity outside a ticking chunk never moves, and a test that measures a shot that
                // was never fired passes or fails for its own reasons. Wait for the chunk first.
                .thenWaitUntil(() -> check(helper,
                        helper.getLevel().isPositionEntityTicking(arrow.blockPosition()),
                        () -> "the arrow's chunk never started ticking entities"))
                .thenExecute(() -> arrow.setDeltaMovement(new Vec3(-1.2, 0.0, 0.0)))
                .thenExecuteFor(4, () -> { })
                .thenExecute(() -> {
                    // Read everything, then put the arrow away *before* asserting: an arrow that was
                    // not turned is still flying, and a failed assertion here would otherwise leave it
                    // to sail into the next test's row and fail that one too.
                    float integrity = shield.getIntegrity();
                    double distance = arrow.position().distanceTo(shield.centre());
                    Vec3 motion = arrow.getDeltaMovement();
                    float health = victim.getHealth();
                    arrow.discard();

                    check(helper, integrity < POOL,
                            () -> "the arrow crossed the ward without costing it anything: " + describe(shield));
                    check(helper, distance >= radius - 1.0,
                            () -> "the arrow was stopped at the body (" + distance + " blocks from the ward's centre, "
                                    + "radius " + radius + ") instead of at the wall");
                    check(helper, motion.x >= 0.0,
                            () -> "the arrow kept flying inwards at " + motion + " — it was absorbed, not turned");
                    check(helper, health >= victim.getMaxHealth(),
                            () -> "the arrow reached the warded player (health " + health + ")");
                })
                .thenExecute(() -> cleanUp(helper, victim, shield))
                .thenSucceed();
    }

    // ── 8b.1, the other half: the jet names who is holding it ───────────────────────────────────

    private static void aguamentiJetNamesItsCaster(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 casterAt = new Vec3(1.5, 14.0, 1.5);
        Vec3 blazeAt = new Vec3(1.5, 14.0, 4.0);
        ServerPlayer caster = channeller(helper, "wandb-jet-caster", casterAt, "aguamenti", "lumos");

        // Fire-immune, so the jet actually hurts it; no AI, so it stays in the stream.
        Blaze blaze = helper.spawn(EntityType.BLAZE, blazeAt);
        blaze.setNoAi(true);
        blaze.setNoGravity(true);

        helper.startSequence()
                .thenExecute(() -> {
                    aimAt(caster, blaze);
                    caster.gameMode.useItem(caster, level, caster.getMainHandItem(), InteractionHand.MAIN_HAND);
                })
                .thenExecuteFor(JET_TICKS, () -> {
                    aimAt(caster, blaze);
                    caster.doTick();
                })
                .thenExecute(() -> {
                    check(helper, blaze.getHealth() < blaze.getMaxHealth(),
                            () -> "the jet never reached the blaze (health " + blaze.getHealth()
                                    + ") — this scenario proves nothing about attribution");
                    // The point of the fix: the damage names its caster, which is what lets a ward see
                    // it at all. Bare magic() left this null and the kill credited to nobody.
                    check(helper, blaze.getLastHurtByMob() == caster,
                            () -> "the jet's damage named " + blaze.getLastHurtByMob()
                                    + " instead of its caster — a Shield Charm cannot answer damage "
                                    + "with no attacker");
                })
                .thenExecute(() -> {
                    caster.releaseUsingItem();
                    blaze.discard();
                    retire(helper, caster);
                })
                .thenSucceed();
    }

    // ── shared setup ────────────────────────────────────────────────────────────────────────────

    /** A survival player with a wand, a spell learned and selected, parked and facing nothing. */
    private static ServerPlayer channeller(GameTestHelper helper, String name, Vec3 where,
                                           String spellId, String prerequisiteId) {
        ServerPlayer player = warded(helper, name, where);
        ItemStack wand = WizardTestSupport.giveBondedWand(player);
        WizardTestSupport.learnAndSelect(helper, player, spellId, prerequisiteId);
        check(helper, !wand.isEmpty(), () -> "the test wand was not given to " + name);
        return player;
    }

    /** A survival player: a creative one is invulnerable and fires no damage event at all. */
    private static ServerPlayer warded(GameTestHelper helper, String name, Vec3 where) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.makeWandkind(player);
        Vec3 at = helper.absoluteVec(where);
        player.teleportTo(at.x, at.y, at.z);
        player.setNoGravity(true);
        return player;
    }

    private static ProtegoShieldEntity raise(GameTestHelper helper, ServerPlayer caster, ProtegoTier tier) {
        ServerLevel level = helper.getLevel();
        ProtegoShieldEntity shield =
                ProtegoShieldEntity.raise(level, caster, tier, false, POOL, LIFETIME);
        level.addFreshEntity(shield);
        ProtegoWardManager.register(caster.getUUID(), shield.getId());
        caster.addEffect(new MobEffectInstance(
                at.koopro.wizardsandbeasts.effect.ModEffects.PROTEGO_SHIELD, LIFETIME, tier.index(),
                false, false, true));
        return shield;
    }

    /** Points a caster's eyes at a target, which is how every beam picks what it is aimed at. */
    private static void aimAt(ServerPlayer caster, LivingEntity target) {
        Vec3 to = target.getEyePosition().subtract(caster.getEyePosition());
        double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
        caster.setYRot((float) (Math.atan2(-to.x, to.z) * (180.0 / Math.PI)));
        caster.setXRot((float) (-Math.atan2(to.y, horizontal) * (180.0 / Math.PI)));
        caster.setYHeadRot(caster.getYRot());
    }

    /** Crucio's requirement is a proficiency one, so the prerequisite has to be practised, not just known. */
    private static void masterPrerequisite(ServerPlayer player, String spellId) {
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        data.learnSpell(spellId);
        data.setSuccessfulHits(spellId, 10_000);
    }

    /**
     * Dark magic ships disabled, and a disabled module makes the whole Crucio channel a no-op.
     *
     * <p>Written to the module <em>cache</em> rather than through {@code ModuleStateService}: the
     * service is the operator path, and it reloads every datapack so recipe conditions can be
     * re-evaluated. In a game-test run the scenarios share one server and overlap, so that reload
     * swapped the creature-ability definitions out from under an unrelated Occamy test and failed it.
     * The cache write touches nothing else and is put back when this scenario ends.
     */
    @SuppressWarnings("deprecation") // cache-only is exactly what a test wants; the service is not
    private static ModuleState swapDarkArts(ModuleState state) {
        ModuleState before = ModuleManager.state(Module.DARK_ARTS);
        ModuleManager.setState(Module.DARK_ARTS, switch (state) {
            case ENABLED -> ModuleManager.State.ENABLED;
            case PREVIEW -> ModuleManager.State.PREVIEW;
            default -> ModuleManager.State.DISABLED;
        });
        return before;
    }

    /**
     * Force-loads the chunks a scenario's flight path crosses.
     *
     * <p>A test lands at an arbitrary offset inside its chunk, and anything that travels a few blocks
     * crosses a boundary more often than not; entities outside a ticking chunk simply hang where they
     * are. The runner releases these with the test's own when the batch ends.
     */
    private static void loadThePath(GameTestHelper helper, Vec3 fromLocal, Vec3 toLocal) {
        ServerLevel level = helper.getLevel();
        ChunkPos.rangeClosed(
                        new ChunkPos(BlockPos.containing(helper.absoluteVec(fromLocal))),
                        new ChunkPos(BlockPos.containing(helper.absoluteVec(toLocal))))
                .forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, true));
    }

    private static String describe(ProtegoShieldEntity shield) {
        return "tier=" + shield.tier() + " integrity=" + shield.getIntegrity() + "/" + shield.getMaxIntegrity()
                + " collapse=" + shield.collapseCause();
    }

    private static void cleanUp(GameTestHelper helper, ServerPlayer warded, ProtegoShieldEntity shield) {
        shield.discard();
        ProtegoWardManager.remove(warded.getUUID());
        retire(helper, warded);
    }
}
