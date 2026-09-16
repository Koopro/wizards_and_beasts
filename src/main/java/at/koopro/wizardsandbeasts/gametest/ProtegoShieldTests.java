package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.entity.spell.ProtegoShieldEntity;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoRules;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoTier;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoWardManager;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.spellData;

/**
 * The Shield Charm as an absorb pool: what it spends, what it ignores, and what breaking it costs.
 *
 * <p>These assert on the <em>shield</em>, not on the player's health, because a game-test player is
 * effectively unkillable (see {@code reference_gametest_1_21_11}) and its health says nothing. What
 * the integrity pool does after a hit is the behaviour under test anyway.
 *
 * <p>Each scenario stands the shield up directly through {@link ProtegoShieldEntity#raise} rather
 * than driving a wand hold: the hold-to-tier ladder is unit-tested in {@code ProtegoRulesTest}, and
 * what needs a running world is the interception, the damage ward and the breach.
 */
public final class ProtegoShieldTests {

    /**
     * Each scenario gets its own height.
     *
     * <p>{@code StructureGridSpawner} puts {@code minecraft:empty} tests a handful of blocks apart, and
     * these scenarios reach three blocks out with an attacker in tow. Stacking them vertically keeps
     * them out of each other's way and out of the next row's — the first version put a zombie close
     * enough to a neighbouring Occamy test to shove its subject out of the box it was being measured in,
     * and that test failed instead of this one.
     */
    private static Vec3 casterAt(double y) {
        return new Vec3(1.5, y, 1.5);
    }
    private static final float POOL = 20.0f;
    private static final int LIFETIME = 200;
    private static final float BLOW = 4.0f;

    private ProtegoShieldTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("protego_dome_absorbs_a_blow", "protego: a dome spends integrity instead of health",
                ProtegoShieldTests::domeAbsorbsABlow);
        tests.add("protego_ignores_the_weather", "protego: a shield is not a life-support bubble",
                ProtegoShieldTests::ignoresEnvironmentalDamage);
        tests.add("protego_disc_only_covers_the_front", "protego: the T0 disc has a back",
                ProtegoShieldTests::discOnlyCoversTheFront);
        tests.add("protego_breach_costs_the_caster", "protego: breaking a ward locks the recast",
                ProtegoShieldTests::breachCostsTheCaster);
        tests.add("protego_planted_dome_holds_its_ground", "protego: a planted dome stays put and covers who is in it",
                ProtegoShieldTests::plantedDomeHoldsItsGround);
        tests.add("protego_mobile_dome_follows_the_caster", "protego: an unplanted ward walks with its caster",
                ProtegoShieldTests::mobileWardFollowsTheCaster);
        tests.add("protego_horribilis_shrugs_off_dark", "protego: Horribilis pays least for Dark magic",
                ProtegoShieldTests::horribilisShrugsOffDark);
    }

    // ── A: a blow spends the pool, and the victim keeps their health ────────────────────────────

    private static void domeAbsorbsABlow(GameTestHelper helper) {
        Vec3 at = casterAt(2.0);
        ServerPlayer caster = readyCaster(helper, "wandb-protego-dome", at);
        ProtegoShieldEntity shield = raise(helper, caster, ProtegoTier.MAXIMA, false, POOL);
        Zombie attacker = parkedZombie(helper, at.add(3.0, 0.0, 0.0));

        helper.startSequence()
                .thenExecute(() -> {
                    float health = caster.getHealth();
                    boolean landed = hit(helper, caster, attacker, BLOW);

                    check(helper, shield.getIntegrity() <= POOL - BLOW,
                            () -> "the ward did not spend integrity for a blow it should have taken: "
                                    + describe(shield) + " landed=" + landed + diagnose(caster));
                    check(helper, caster.getHealth() >= health,
                            () -> "the blow reached the caster through a shield with "
                                    + shield.getIntegrity() + " integrity left");
                })
                .thenExecute(() -> cleanUp(helper, caster, shield, attacker))
                .thenSucceed();
    }

    // ── B: falling, drowning and starving are not somebody attacking you ────────────────────────

    private static void ignoresEnvironmentalDamage(GameTestHelper helper) {
        ServerPlayer caster = readyCaster(helper, "wandb-protego-weather", casterAt(6.0));
        ProtegoShieldEntity shield = raise(helper, caster, ProtegoTier.MAXIMA, false, POOL);

        helper.startSequence()
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    caster.hurtServer(level, level.damageSources().fall(), BLOW);
                    caster.hurtServer(level, level.damageSources().starve(), BLOW);
                    caster.hurtServer(level, level.damageSources().magic(), BLOW); // a poison tick

                    check(helper, shield.getIntegrity() == POOL,
                            () -> "the ward paid for damage that came from nowhere — a shield charm is "
                                    + "not a life-support bubble: " + describe(shield));
                })
                .thenExecute(() -> cleanUp(helper, caster, shield, null))
                .thenSucceed();
    }

    // ── C: the quick parry is a disc, and a disc has a back ─────────────────────────────────────

    private static void discOnlyCoversTheFront(GameTestHelper helper) {
        Vec3 at = casterAt(10.0);
        ServerPlayer caster = readyCaster(helper, "wandb-protego-disc", at);
        caster.setYRot(0.0f); // facing +Z
        ProtegoShieldEntity shield = raise(helper, caster, ProtegoTier.PROTEGO, false, POOL);
        Zombie behind = parkedZombie(helper, at.add(0.0, 0.0, -3.0));
        Zombie ahead = parkedZombie(helper, at.add(0.0, 0.0, 3.0));

        helper.startSequence()
                // The shield reads its aim from the caster, and it only follows them on a tick.
                .thenExecuteFor(2, () -> { })
                .thenExecute(() -> {
                    hit(helper, caster, behind, BLOW);
                    check(helper, shield.getIntegrity() == POOL,
                            () -> "the disc blocked a hit from directly behind the caster: " + describe(shield));

                    hit(helper, caster, ahead, BLOW);
                    check(helper, shield.getIntegrity() <= POOL - BLOW,
                            () -> "the disc let a hit from straight ahead through: " + describe(shield));
                })
                .thenExecute(() -> {
                    behind.discard();
                    cleanUp(helper, caster, shield, ahead);
                })
                .thenSucceed();
    }

    // ── D: breaking a ward is the ending that costs something ───────────────────────────────────

    private static void breachCostsTheCaster(GameTestHelper helper) {
        Vec3 at = casterAt(14.0);
        ServerPlayer caster = readyCaster(helper, "wandb-protego-breach", at);
        ProtegoShieldEntity shield = raise(helper, caster, ProtegoTier.MAXIMA, false, 3.0f);
        Zombie attacker = parkedZombie(helper, at.add(3.0, 0.0, 0.0));

        helper.startSequence()
                .thenExecute(() -> {
                    hit(helper, caster, attacker, 12.0f);

                    check(helper, shield.collapseCause() == ProtegoShieldEntity.Collapse.BREACHED,
                            () -> "a blow bigger than the whole pool did not breach the ward: " + describe(shield));
                    check(helper, caster.getEffect(ModEffects.PROTEGO_SHIELD) == null,
                            () -> "the breached ward left its effect on the caster: " + describe(shield));
                    check(helper, ProtegoWardManager.shieldOf(caster) == null,
                            () -> "the breached ward is still registered as the caster's shield");

                    long expiry = spellData(caster).getCooldownExpiry(Spells.PROTEGO.getId());
                    long now = helper.getLevel().getGameTime();
                    check(helper, expiry > now,
                            () -> "breaking a ward left Protego ready to recast at once (cooldown expiry "
                                    + expiry + ", now " + now + ") — the breach is supposed to cost the caster");
                })
                .thenExecute(() -> cleanUp(helper, caster, shield, attacker))
                .thenSucceed();
    }

    // ── E: planted domes are places, not leashes ────────────────────────────────────────────────

    private static void plantedDomeHoldsItsGround(GameTestHelper helper) {
        Vec3 at = casterAt(18.0);
        ServerPlayer caster = readyCaster(helper, "wandb-protego-planter", at);
        ProtegoShieldEntity shield = raise(helper, caster, ProtegoTier.MAXIMA, true, POOL);
        Vec3 anchor = shield.position();

        // An ally sheltering under the dome, and the caster walking out of their own ward.
        ServerPlayer ally = readyCaster(helper, "wandb-protego-ally", at.add(2.0, 0.0, 0.0));
        Zombie attacker = parkedZombie(helper, at.add(8.0, 0.0, 0.0));

        helper.startSequence()
                .thenExecute(() -> {
                    Vec3 away = helper.absoluteVec(at.add(9.0, 0.0, 0.0));
                    caster.teleportTo(away.x, away.y, away.z);
                })
                .thenExecuteFor(3, () -> { })
                .thenExecute(() -> {
                    check(helper, shield.position().distanceToSqr(anchor) < 1.0e-6,
                            () -> "the planted dome followed its caster to " + shield.position()
                                    + " instead of holding " + anchor);
                    check(helper, !shield.covers(caster),
                            () -> "the caster walked nine blocks and is somehow still inside their own dome");

                    float before = shield.getIntegrity();
                    hit(helper, ally, attacker, BLOW);
                    check(helper, shield.getIntegrity() <= before - BLOW,
                            () -> "the planted dome did not cover the ally standing in it: " + describe(shield));

                    // And it is still only covering what is inside it.
                    float afterAlly = shield.getIntegrity();
                    hit(helper, caster, attacker, BLOW);
                    check(helper, shield.getIntegrity() == afterAlly,
                            () -> "the planted dome paid for a blow on a caster standing outside it: "
                                    + describe(shield));
                })
                .thenExecute(() -> {
                    retire(helper, ally);
                    cleanUp(helper, caster, shield, attacker);
                })
                .thenSucceed();
    }

    // ── F: everything not planted walks with its caster ─────────────────────────────────────────

    private static void mobileWardFollowsTheCaster(GameTestHelper helper) {
        Vec3 at = casterAt(22.0);
        ServerPlayer caster = readyCaster(helper, "wandb-protego-walker", at);
        // Totalum cannot be planted at all, whatever the caster is doing with their sneak key.
        check(helper, !ProtegoRules.canPlant(ProtegoTier.TOTALUM, true),
                () -> "Totalum reported itself plantable");
        ProtegoShieldEntity shield = raise(helper, caster, ProtegoTier.TOTALUM, false, POOL);

        helper.startSequence()
                .thenExecute(() -> {
                    Vec3 away = helper.absoluteVec(at.add(4.0, 0.0, 0.0));
                    caster.teleportTo(away.x, away.y, away.z);
                })
                .thenExecuteFor(3, () -> { })
                .thenExecute(() -> check(helper, shield.covers(caster),
                        () -> "the mobile ward stayed at " + shield.position() + " while its caster walked to "
                                + caster.position()))
                .thenExecute(() -> cleanUp(helper, caster, shield, null))
                .thenSucceed();
    }

    // ── G: the top tier's whole reason to exist ─────────────────────────────────────────────────

    private static void horribilisShrugsOffDark(GameTestHelper helper) {
        Vec3 at = casterAt(26.0);
        ServerPlayer wardedByTop = readyCaster(helper, "wandb-protego-horribilis", at);
        ServerPlayer wardedByDome = readyCaster(helper, "wandb-protego-maxima", at.add(0.0, 0.0, 12.0));
        ProtegoShieldEntity horribilis = raise(helper, wardedByTop, ProtegoTier.HORRIBILIS, false, POOL);
        ProtegoShieldEntity maxima = raise(helper, wardedByDome, ProtegoTier.MAXIMA, false, POOL);
        // A zombie is in the mod's dark_creatures tag; its blow is Dark magic as far as a ward cares.
        Zombie dark = parkedZombie(helper, at.add(3.0, 0.0, 0.0));

        helper.startSequence()
                .thenExecute(() -> {
                    hit(helper, wardedByTop, dark, BLOW);
                    hit(helper, wardedByDome, dark, BLOW);

                    float topSpent = POOL - horribilis.getIntegrity();
                    float domeSpent = POOL - maxima.getIntegrity();
                    check(helper, topSpent > 0.0f && domeSpent > 0.0f,
                            () -> "neither ward answered the blow at all: " + describe(horribilis)
                                    + " / " + describe(maxima));
                    check(helper, topSpent < domeSpent,
                            () -> "Horribilis spent " + topSpent + " on Dark magic where Maxima spent "
                                    + domeSpent + " — the top tier's whole clause is that it pays less");
                })
                .thenExecute(() -> {
                    maxima.discard();
                    ProtegoWardManager.remove(wardedByDome.getUUID());
                    retire(helper, wardedByDome);
                    cleanUp(helper, wardedByTop, horribilis, dark);
                })
                .thenSucceed();
    }

    // ── shared setup ────────────────────────────────────────────────────────────────────────────

    private static ServerPlayer readyCaster(GameTestHelper helper, String name, Vec3 where) {
        // Survival, not the usual creative mock: a creative player is invulnerable to everything and
        // vanilla short-circuits before the incoming-damage event, so no ward would ever be consulted.
        ServerPlayer caster = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.makeWandkind(caster);
        WizardTestSupport.giveBondedWand(caster);
        WizardTestSupport.learnAndSelect(helper, caster, "protego", "expelliarmus");
        Vec3 at = helper.absoluteVec(where);
        caster.teleportTo(at.x, at.y, at.z);
        caster.setNoGravity(true);
        return caster;
    }

    /**
     * An attacker that stays put: these scenarios need a source for a blow, not a mob with opinions.
     * A walking zombie leaves its own test and interferes with the next one.
     */
    private static Zombie parkedZombie(GameTestHelper helper, Vec3 where) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, where);
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.setPersistenceRequired();
        return zombie;
    }

    /** Stands a shield up exactly as {@code Protego.executeCast} does, minus the wand hold. */
    private static ProtegoShieldEntity raise(GameTestHelper helper, ServerPlayer caster,
                                             ProtegoTier tier, boolean planted, float integrity) {
        ServerLevel level = helper.getLevel();
        ProtegoShieldEntity shield =
                ProtegoShieldEntity.raise(level, caster, tier, planted, integrity, LIFETIME);
        level.addFreshEntity(shield);
        ProtegoWardManager.register(caster.getUUID(), shield.getId());
        caster.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                ModEffects.PROTEGO_SHIELD, LIFETIME, tier.index(), false, false, true));
        return shield;
    }

    /** A blow that carries an attacker and a position — the shape of damage a ward answers. */
    private static boolean hit(GameTestHelper helper, ServerPlayer victim, Zombie attacker, float amount) {
        ServerLevel level = helper.getLevel();
        // Invulnerability frames would silently swallow the second hit of a two-hit scenario.
        victim.invulnerableTime = 0;
        return victim.hurtServer(level, level.damageSources().mobAttack(attacker), amount);
    }

    private static String diagnose(ServerPlayer caster) {
        return " [hurtLanded? abilities.invulnerable=" + caster.getAbilities().invulnerable
                + " entity.invulnerable=" + caster.isInvulnerable()
                + " clientLoaded=" + caster.connection.hasClientLoaded()
                + " pvp=" + caster.level().isPvpAllowed()
                + " health=" + caster.getHealth() + "]";
    }

    private static String describe(ProtegoShieldEntity shield) {
        return "tier=" + shield.tier() + " integrity=" + shield.getIntegrity() + "/"
                + shield.getMaxIntegrity() + " planted=" + shield.isPlanted()
                + " collapse=" + shield.collapseCause();
    }

    private static void cleanUp(GameTestHelper helper, ServerPlayer caster,
                                ProtegoShieldEntity shield, Zombie attacker) {
        if (attacker != null) {
            attacker.discard();
        }
        shield.discard();
        ProtegoWardManager.remove(caster.getUUID());
        retire(helper, caster);
    }
}
