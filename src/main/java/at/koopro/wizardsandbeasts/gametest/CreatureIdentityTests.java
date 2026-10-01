package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.creature.ability.BlastPropulsion;
import at.koopro.wizardsandbeasts.creature.ability.BoggartDread;
import at.koopro.wizardsandbeasts.creature.ability.CreatureAbility;
import at.koopro.wizardsandbeasts.creature.ability.ForestKeeper;
import at.koopro.wizardsandbeasts.creature.ability.HoardGuard;
import at.koopro.wizardsandbeasts.creature.ability.Infighting;
import at.koopro.wizardsandbeasts.creature.ability.MerfolkSong;
import at.koopro.wizardsandbeasts.creature.ability.StarReading;
import at.koopro.wizardsandbeasts.creature.wildlife.SignatureRules;
import at.koopro.wizardsandbeasts.entity.beast.AugureyEntity;
import at.koopro.wizardsandbeasts.entity.beast.CornishPixieEntity;
import at.koopro.wizardsandbeasts.entity.beast.StreelerEntity;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.entity.creature.Guise;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static at.koopro.wizardsandbeasts.gametest.CreatureWildlifeTests.lookAt;
import static at.koopro.wizardsandbeasts.gametest.CreatureWildlifeTests.max;
import static at.koopro.wizardsandbeasts.gametest.CreatureWildlifeTests.min;
import static at.koopro.wizardsandbeasts.gametest.CreatureWildlifeTests.page;
import static at.koopro.wizardsandbeasts.gametest.CreatureWildlifeTests.place;
import static at.koopro.wizardsandbeasts.gametest.CreatureWildlifeTests.roundTrip;
import static at.koopro.wizardsandbeasts.gametest.CreatureWildlifeTests.stage;
import static at.koopro.wizardsandbeasts.gametest.CreatureWildlifeTests.still;
import static at.koopro.wizardsandbeasts.gametest.CreatureWildlifeTests.tier;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * Species identity, in a live server: each creature doing the one thing that makes it itself. An Augurey that cries only
 * when rain is coming, a Streeler whose trail kills plants and Horklumps, a Cornish Pixie that makes off with your wand,
 * Skrewts that blast and turn on each other, a Griffin over its gold, a Centaur that reads the sky and keeps its
 * forest, merfolk song heard only under water, and a Boggart that becomes the worst thing you have met.
 *
 * <p>Same conventions as {@link CreatureWildlifeTests}, whose stage helpers these share: players are never ticked,
 * shared world state (weather, time) is handed to the code rather than set, creatures that are not meant to move are
 * placed with no AI, and every assertion is about this scenario's own players. Heights sit between the wildlife
 * scenarios, clear of every radius read here.
 */
public final class CreatureIdentityTests {

    private static final int AUGUREY_Y = 88;
    private static final int STREELER_Y = 131;
    private static final int PIXIE_Y = 143;
    private static final int SKREWT_Y = 156;
    private static final int GRIFFIN_Y = 288;
    private static final int CENTAUR_Y = 313;
    private static final int MERFOLK_Y = 337;
    private static final int BOGGART_Y = 388;

    private CreatureIdentityTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("creature_augurey_cries_before_rain",
                "augurey: cries only when rain is coming, and whoever hears it has seen its signature; keeps its roost",
                CreatureIdentityTests::augureyCriesBeforeRain);
        tests.add("creature_streeler_trail_withers_and_kills_horklumps",
                "streeler: its trail kills plants and burns grass to dirt, not with mobGriefing off; its venom kills "
                        + "Horklumps; its colour is the hour's",
                CreatureIdentityTests::streelerTrailWithers);
        tests.add("creature_pixie_snatches_wands_and_hoists",
                "cornish pixie: snatches a wand from either hand, never from a creative player, drops it when struck; "
                        + "keeps it through a save; three pixies hoist a person, two do not",
                CreatureIdentityTests::pixieSnatchesAndHoists);
        tests.add("creature_skrewt_blasts_and_turns_on_its_kind",
                "blast-ended skrewt: a blast shoves it forward and burns what is behind, not what is in front; three "
                        + "together fight, two do not; its shell turns spells",
                CreatureIdentityTests::skrewtBlastsAndFights);
        tests.add("creature_griffin_guards_its_gold",
                "griffin: claims nearby gold, warns a stranger once then attacks, tolerates the person who feeds it, "
                        + "keeps its hoard and warnings through a save, gives up gold that is gone",
                CreatureIdentityTests::griffinGuardsGold);
        tests.add("creature_centaur_reads_the_sky_and_keeps_the_forest",
                "centaur: tells of danger abroad, warns a woodcutter then turns on them, not a creative one, will not "
                        + "carry a rider",
                CreatureIdentityTests::centaurReadsAndKeeps);
        tests.add("creature_merfolk_song_heard_only_under_water",
                "merpeople: song under water, a screech above it, nothing out of range; the song is the signature",
                CreatureIdentityTests::merfolkSongUnderWater);
        tests.add("creature_boggart_becomes_your_fear",
                "boggart: unseen alone, the worst creature you have met when you face it, a shadow if you have met none, "
                        + "confused and harmless before two, never saves a shape",
                CreatureIdentityTests::boggartBecomesYourFear);
    }

    // ── the Augurey ───────────────────────────────────────────────────────────────────────────────

    private static void augureyCriesBeforeRain(GameTestHelper helper) {
        stage(helper, AUGUREY_Y);
        ServerPlayer listener = WizardTestSupport.placeMockPlayer(helper, "wandb-identity-augurey", GameType.SURVIVAL);
        place(helper, listener, AUGUREY_Y, 3, 0);
        AugureyEntity augurey = helper.spawn(ModEntities.AUGUREY.get(), new BlockPos(3, AUGUREY_Y, 5));
        augurey.setNoAi(true);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(AUGUREY_Y), max(AUGUREY_Y)))
                .thenIdle(2)
                .thenExecute(() -> {
                    lookAt(listener, augurey);
                    check(helper, augurey.hasHome(), () -> "an augurey did not take the spot it was found as its roost");
                    check(helper, !augurey.cryIfRainComing(false), () -> "an augurey cried with no rain coming");
                    check(helper, tier(listener, page("augurey")).ordinal() < DiscoveryTier.KNOWN.ordinal(),
                            () -> "a silent augurey taught its listener its signature");
                    check(helper, augurey.cryIfRainComing(true), () -> "an augurey stayed silent with rain coming");
                    check(helper, tier(listener, page("augurey")) == DiscoveryTier.KNOWN,
                            () -> "hearing the cry before rain was not its signature: " + tier(listener, page("augurey")));
                    AugureyEntity saved = roundTrip(helper, augurey);
                    check(helper, saved != null && saved.hasHome()
                                    && saved.getHomePosition().equals(augurey.getHomePosition()),
                            () -> "an augurey's roost did not survive a save");
                })
                .thenExecute(() -> retire(helper, listener))
                .thenSucceed();
    }

    // ── the Streeler ──────────────────────────────────────────────────────────────────────────────

    private static void streelerTrailWithers(GameTestHelper helper) {
        stage(helper, STREELER_Y);
        BlockPos flowerAt = new BlockPos(2, STREELER_Y, 3);
        BlockPos grassAt = new BlockPos(5, STREELER_Y, 3);
        helper.setBlock(flowerAt.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(flowerAt, Blocks.POPPY);
        helper.setBlock(grassAt.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(grassAt, Blocks.SHORT_GRASS);
        StreelerEntity streeler = helper.spawn(ModEntities.STREELER.get(), new BlockPos(3, STREELER_Y, 6));
        streeler.setNoAi(true);
        GenericBeastEntity horklump = still(helper, "horklump", STREELER_Y, 3, 7);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(STREELER_Y), max(STREELER_Y)))
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
                    try {
                        level.getGameRules().set(GameRules.MOB_GRIEFING, false, level.getServer());
                        check(helper, streeler.witherUnder(level, helper.absolutePos(grassAt)) == 0
                                        && helper.getBlockState(grassAt).is(Blocks.SHORT_GRASS),
                                () -> "a streeler's trail withered plants with mobGriefing off");
                        level.getGameRules().set(GameRules.MOB_GRIEFING, true, level.getServer());
                        check(helper, streeler.witherUnder(level, helper.absolutePos(flowerAt)) == 2,
                                () -> "a streeler's trail did not kill the flower and burn the grass under it");
                        check(helper, helper.getBlockState(flowerAt).isAir()
                                        && helper.getBlockState(flowerAt.below()).is(Blocks.DIRT),
                                () -> "the flower lived, or the grass under it was not burned to dirt");
                        check(helper, streeler.witherUnder(level, helper.absolutePos(grassAt)) == 2
                                        && helper.getBlockState(grassAt).isAir(),
                                () -> "short grass survived a streeler's trail");
                    } finally {
                        level.getGameRules().set(GameRules.MOB_GRIEFING, griefing, level.getServer());
                    }
                    float before = horklump.getHealth();
                    streeler.teleportTo(horklump.getX(), horklump.getY(), horklump.getZ() - 0.8);
                    int bitten = 0;
                    for (int i = 0; i < 3; i++) {
                        horklump.invulnerableTime = 0;
                        bitten += streeler.poisonHorklumps(level);
                    }
                    int bites = bitten;
                    check(helper, bites > 0, () -> "a streeler did not bite the horklump on its trail");
                    check(helper, horklump.getHealth() < before || !horklump.isAlive(),
                            () -> "a streeler's venom did not harm a horklump on its trail");
                    check(helper, streeler.colour() == SignatureRules.streelerColour(level.getDayTime(),
                                    (int) (streeler.getUUID().getLeastSignificantBits() & 0x7)),
                            () -> "a streeler was not this hour's colour");
                })
                .thenSucceed();
    }

    // ── the Cornish Pixie ─────────────────────────────────────────────────────────────────────────

    private static void pixieSnatchesAndHoists(GameTestHelper helper) {
        stage(helper, PIXIE_Y);
        ServerPlayer victim = WizardTestSupport.placeMockPlayer(helper, "wandb-identity-pixie-victim", GameType.SURVIVAL);
        ServerPlayer builder = WizardTestSupport.placeMockPlayer(helper, "wandb-identity-pixie-creative", GameType.CREATIVE);
        place(helper, victim, PIXIE_Y, 3, 3);
        place(helper, builder, PIXIE_Y, 6, 7);
        CornishPixieEntity thief = pixie(helper, 3, 4);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(PIXIE_Y), max(PIXIE_Y)))
                .thenExecute(() -> {
                    ItemStack wand = new ItemStack(WandItemRegistry.WAND.get());
                    victim.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
                    victim.setItemInHand(InteractionHand.OFF_HAND, wand.copy());
                    check(helper, thief.snatchFrom(victim) && thief.carried().is(WandItemRegistry.WAND.get())
                                    && victim.getOffhandItem().isEmpty() && victim.getMainHandItem().is(Items.BREAD),
                            () -> "a pixie did not go for the wand first");
                    check(helper, tier(victim, page("cornish_pixie")) == DiscoveryTier.KNOWN,
                            () -> "losing a wand to a pixie was not its signature");
                    CornishPixieEntity saved = roundTrip(helper, thief);
                    check(helper, saved != null && saved.carried().is(WandItemRegistry.WAND.get()),
                            () -> "a pixie lost what it carried in a save");

                    thief.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(victim), 0.5f);
                    check(helper, thief.carried().isEmpty() && droppedNear(helper, WandItemRegistry.WAND.get().getDefaultInstance()),
                            () -> "a struck pixie kept the wand");

                    CornishPixieEntity other = pixie(helper, 7, 8); // well away from the victim
                    builder.setItemInHand(InteractionHand.MAIN_HAND, wand.copy());
                    check(helper, !other.snatchFrom(builder) && builder.getMainHandItem().is(WandItemRegistry.WAND.get()),
                            () -> "a pixie robbed a creative player");

                    // Two pixies pester; a third makes it a hoist.
                    CornishPixieEntity second = pixie(helper, 2, 3);
                    check(helper, !thief.tryHoist(helper.getLevel()) && !victim.hasEffect(MobEffects.LEVITATION),
                            () -> "two pixies hoisted someone");
                    pixie(helper, 4, 3);
                    check(helper, second.tryHoist(helper.getLevel()) && victim.hasEffect(MobEffects.LEVITATION)
                                    && victim.hasEffect(MobEffects.SLOW_FALLING),
                            () -> "three pixies did not hoist someone, or did not let them down slowly");
                })
                .thenExecute(() -> {
                    retire(helper, victim);
                    retire(helper, builder);
                })
                .thenSucceed();
    }

    private static CornishPixieEntity pixie(GameTestHelper helper, int x, int z) {
        CornishPixieEntity pixie = helper.spawn(ModEntities.CORNISH_PIXIE.get(), new BlockPos(x, PIXIE_Y + 1, z));
        pixie.setNoAi(true);
        pixie.setNoGravity(true);
        return pixie;
    }

    private static boolean droppedNear(GameTestHelper helper, ItemStack like) {
        Vec3 lo = Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(-2, PIXIE_Y - 2, -2)));
        Vec3 hi = Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(9, PIXIE_Y + 6, 10)));
        return !helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(lo, hi),
                e -> e.isAlive() && ItemStack.isSameItem(e.getItem(), like)).isEmpty();
    }

    // ── the Blast-Ended Skrewt ────────────────────────────────────────────────────────────────────

    private static void skrewtBlastsAndFights(GameTestHelper helper) {
        stage(helper, SKREWT_Y);
        GenericBeastEntity skrewt = still(helper, "blast_ended_skrewt", SKREWT_Y, 3, 4);
        Pig behind = helper.spawn(EntityType.PIG, new BlockPos(3, SKREWT_Y, 2));
        behind.setNoAi(true);
        Pig ahead = helper.spawn(EntityType.PIG, new BlockPos(3, SKREWT_Y, 7));
        ahead.setNoAi(true);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(SKREWT_Y), max(SKREWT_Y)))
                .thenExecute(() -> {
                    // Face the skrewt toward +Z: the pig at z=2 is behind it, the one at z=7 ahead.
                    Vec3 toAhead = ahead.position().subtract(skrewt.position());
                    skrewt.setYRot((float) Math.toDegrees(Math.atan2(-toAhead.x, toAhead.z)));
                    skrewt.setYBodyRot(skrewt.getYRot());
                    BlastPropulsion blast = skrewt.abilityOf(BlastPropulsion.class);
                    check(helper, blast != null, () -> "a skrewt has no blast");
                    check(helper, !skrewt.hasAbility(CreatureAbility.Type.RANGED_HEX),
                            () -> "a skrewt still throws hexes; it blasts");
                    check(helper, skrewt.hasAbility(CreatureAbility.Type.SPELL_RESIST),
                            () -> "a skrewt's shell does not turn spells");
                    Vec3 forward = Vec3.directionFromRotation(0.0f, skrewt.getYRot());
                    int burned = blast.blast(skrewt);
                    check(helper, skrewt.getDeltaMovement().dot(forward) > 0.3,
                            () -> "the blast did not shove the skrewt forward: " + skrewt.getDeltaMovement());
                    check(helper, burned >= 1 && behind.isOnFire(), () -> "the blast did not burn what was behind it");
                    check(helper, !ahead.isOnFire(), () -> "the blast burned what was in front of it");
                    check(helper, skrewt.getCooldown("blast") > 0, () -> "a skrewt can blast again at once");

                    Infighting infighting = skrewt.abilityOf(Infighting.class);
                    check(helper, infighting != null && infighting.rival(skrewt) == null,
                            () -> "a lone skrewt picked a fight");
                    GenericBeastEntity second = still(helper, "blast_ended_skrewt", SKREWT_Y, 5, 4);
                    check(helper, infighting.rival(skrewt) == null, () -> "a pair of skrewts turned on each other");
                    still(helper, "blast_ended_skrewt", SKREWT_Y, 1, 4);
                    check(helper, infighting.rival(skrewt) != null, () -> "three skrewts together did not fight");
                })
                .thenSucceed();
    }

    // ── the Griffin ───────────────────────────────────────────────────────────────────────────────

    private static void griffinGuardsGold(GameTestHelper helper) {
        stage(helper, GRIFFIN_Y);
        BlockPos gold = new BlockPos(3, GRIFFIN_Y, 7);
        helper.setBlock(gold, Blocks.GOLD_BLOCK);
        ServerPlayer thief = WizardTestSupport.placeMockPlayer(helper, "wandb-identity-griffin-thief", GameType.SURVIVAL);
        ServerPlayer keeper = WizardTestSupport.placeMockPlayer(helper, "wandb-identity-griffin-keeper", GameType.SURVIVAL);
        place(helper, thief, GRIFFIN_Y, -1, -1);   // outside its hoard until the check begins
        place(helper, keeper, GRIFFIN_Y, 5, 4);
        GenericBeastEntity griffin = still(helper, "griffin", GRIFFIN_Y, 3, 5);
        // Fed and trusted before it ever looks around: the keeper is never a stranger to it.
        griffin.bondState().setOwner(keeper.getUUID());
        griffin.bondState().setLevel(30, 100);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(GRIFFIN_Y), max(GRIFFIN_Y)))
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    HoardGuard hoard = griffin.abilityOf(HoardGuard.class);
                    check(helper, hoard != null, () -> "a griffin guards nothing");
                    BlockPos found = hoard.findHoard(level, griffin.blockPosition());
                    check(helper, helper.absolutePos(gold).equals(found), () -> "a griffin did not find the gold: " + found);
                    griffin.setHomeTo(found, (int) Math.ceil(SignatureRules.HOARD_RADIUS));

                    place(helper, thief, GRIFFIN_Y, 2, 4);
                    griffin.setCooldown("hoard_warned:" + thief.getUUID(), 0);
                    griffin.setTarget(null);
                    hoard.guard(griffin, level);
                    check(helper, griffin.getCooldown("hoard_warned:" + keeper.getUUID()) == 0,
                            () -> "a griffin warned off the person who feeds it");
                    check(helper, griffin.getCooldown("hoard_warned:" + thief.getUUID()) > 0 && griffin.getTarget() == null,
                            () -> "a griffin did not warn a stranger first");
                    check(helper, tier(thief, page("griffin")) == DiscoveryTier.KNOWN,
                            () -> "being warned off a griffin's gold was not its signature");

                    GenericBeastEntity saved = roundTrip(helper, griffin);
                    check(helper, saved != null && saved.hasHome() && saved.getHomePosition().equals(found)
                                    && saved.getCooldown("hoard_warned:" + thief.getUUID()) > 0,
                            () -> "a griffin's hoard or its warning did not survive a save");

                    // Still there after the warning: now it is theft.
                    griffin.setCooldown("hoard_warned:" + thief.getUUID(), SignatureRules.HOARD_WARNING_TICKS - 60);
                    hoard.guard(griffin, level);
                    check(helper, griffin.getTarget() == thief, () -> "a warned stranger who stayed was not attacked");

                    helper.setBlock(gold, Blocks.AIR);
                    griffin.setTarget(null);
                    for (int i = 0; i < 10; i++) {
                        hoard.tick(griffin);
                        griffin.tickCount++;
                    }
                    check(helper, !griffin.hasHome(), () -> "a griffin kept guarding gold that was gone");
                })
                .thenExecute(() -> {
                    retire(helper, thief);
                    retire(helper, keeper);
                })
                .thenSucceed();
    }

    // ── the Centaur ───────────────────────────────────────────────────────────────────────────────

    private static void centaurReadsAndKeeps(GameTestHelper helper) {
        stage(helper, CENTAUR_Y);
        ServerPlayer woodcutter = WizardTestSupport.placeMockPlayer(helper, "wandb-identity-centaur-woodcutter", GameType.SURVIVAL);
        ServerPlayer builder = WizardTestSupport.placeMockPlayer(helper, "wandb-identity-centaur-builder", GameType.CREATIVE);
        place(helper, woodcutter, CENTAUR_Y, 2, 1);
        place(helper, builder, CENTAUR_Y, 6, 1);
        GenericBeastEntity centaur = still(helper, "centaur", CENTAUR_Y, 3, 5);
        GenericBeastEntity werewolf = still(helper, "werewolf", CENTAUR_Y, 6, 8);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(CENTAUR_Y), max(CENTAUR_Y)))
                .thenExecute(() -> {
                    StarReading stars = centaur.abilityOf(StarReading.class);
                    check(helper, stars != null && centaur.hasAbility(CreatureAbility.Type.FOREST_KEEPER),
                            () -> "a centaur neither reads the sky nor keeps the forest");
                    check(helper, stars.read(centaur) == SignatureRules.Omen.MARS_BRIGHT,
                            () -> "a werewolf abroad did not make Mars bright");
                    stars.speak(centaur, SignatureRules.Omen.MARS_BRIGHT);
                    check(helper, tier(woodcutter, page("centaur")) == DiscoveryTier.KNOWN
                                    && centaur.getCooldown("star_reading") > 0,
                            () -> "hearing a centaur's reading was not its signature, or it will read again at once");

                    BlockPos tree = helper.absolutePos(new BlockPos(3, CENTAUR_Y, 2));
                    ServerLevel level = helper.getLevel();
                    check(helper, ForestKeeper.treeFelled(level, tree, builder) == 0,
                            () -> "centaurs turned on a creative player");
                    check(helper, ForestKeeper.treeFelled(level, tree, woodcutter) == 0 && centaur.getTarget() == null,
                            () -> "centaurs attacked a woodcutter without warning");
                    check(helper, ForestKeeper.treeFelled(level, tree, woodcutter) >= 1 && centaur.getTarget() == woodcutter,
                            () -> "centaurs let a warned woodcutter fell a second tree");
                    GenericBeastEntity saved = roundTrip(helper, centaur);
                    check(helper, saved != null && saved.getCooldown("forest_warned:" + woodcutter.getUUID()) > 0,
                            () -> "a centaur forgot its grudge in a save");

                    centaur.setTarget(null);
                    woodcutter.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    InteractionResult asked = centaur.interact(woodcutter, InteractionHand.MAIN_HAND);
                    check(helper, asked.consumesAction() && !woodcutter.isPassenger(),
                            () -> "a centaur let itself be mounted, or ignored being asked");
                })
                .thenExecute(() -> {
                    retire(helper, woodcutter);
                    retire(helper, builder);
                })
                .thenSucceed();
    }

    // ── merfolk song ──────────────────────────────────────────────────────────────────────────────

    private static void merfolkSongUnderWater(GameTestHelper helper) {
        stage(helper, MERFOLK_Y);
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                for (int dy = 0; dy <= 2; dy++) {
                    helper.setBlock(new BlockPos(x, MERFOLK_Y + dy, z), Blocks.WATER);
                }
            }
        }
        ServerPlayer diver = WizardTestSupport.placeMockPlayer(helper, "wandb-identity-merfolk-diver", GameType.SURVIVAL);
        ServerPlayer bank = WizardTestSupport.placeMockPlayer(helper, "wandb-identity-merfolk-bank", GameType.SURVIVAL);
        place(helper, diver, MERFOLK_Y, 1, 1);
        place(helper, bank, MERFOLK_Y, 6, 7);
        GenericBeastEntity merperson = still(helper, "merperson", MERFOLK_Y, 1, 2);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(MERFOLK_Y), max(MERFOLK_Y)))
                .thenExecute(() -> {
                    MerfolkSong song = merperson.abilityOf(MerfolkSong.class);
                    check(helper, song != null, () -> "merpeople do not sing");
                    check(helper, MerfolkSong.hears(merperson, diver) == SignatureRules.MerfolkSong.SONG,
                            () -> "a diver with their head under water did not hear the song");
                    check(helper, MerfolkSong.hears(merperson, bank) == SignatureRules.MerfolkSong.SCREECH,
                            () -> "someone on the bank heard more than a screech");
                    song.sing(merperson);
                    check(helper, tier(diver, page("merperson")) == DiscoveryTier.KNOWN,
                            () -> "understanding merfolk song was not its signature");
                    check(helper, tier(bank, page("merperson")).ordinal() < DiscoveryTier.KNOWN.ordinal(),
                            () -> "a screech taught someone the song");
                })
                .thenExecute(() -> {
                    retire(helper, diver);
                    retire(helper, bank);
                })
                .thenSucceed();
    }

    // ── the Boggart ───────────────────────────────────────────────────────────────────────────────

    private static void boggartBecomesYourFear(GameTestHelper helper) {
        stage(helper, BOGGART_Y);
        ServerPlayer ron = WizardTestSupport.placeMockPlayer(helper, "wandb-identity-boggart-ron", GameType.SURVIVAL);
        ServerPlayer neville = WizardTestSupport.placeMockPlayer(helper, "wandb-identity-boggart-neville", GameType.SURVIVAL);
        place(helper, ron, BOGGART_Y, 2, 1);
        place(helper, neville, BOGGART_Y, 5, 1);
        GenericBeastEntity boggart = still(helper, "boggart", BOGGART_Y, 3, 4);
        GenericBeastEntity spider = still(helper, "acromantula", BOGGART_Y, 0, 8);
        GenericBeastEntity ghoul = still(helper, "ghoul", BOGGART_Y, 6, 8);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(BOGGART_Y), max(BOGGART_Y)))
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    BoggartDread dread = boggart.abilityOf(BoggartDread.class);
                    check(helper, dread != null, () -> "a boggart has no fear to give");

                    // Nobody looking: unseen, its own shape.
                    check(helper, dread.confront(boggart, level, List.of()).isEmpty()
                                    && boggart.hasEffect(MobEffects.INVISIBILITY),
                            () -> "an unwatched boggart showed itself");

                    // Ron has met nothing yet: a shapeless shadow, still frightening.
                    check(helper, dread.confront(boggart, level, List.of(ron)).isEmpty()
                                    && ron.hasEffect(MobEffects.DARKNESS) && !boggart.hasEffect(MobEffects.INVISIBILITY),
                            () -> "a boggart facing someone who has met nothing did not frighten them as a shadow");

                    // Ron has met a ghoul and an acromantula; the spider is the worse.
                    BestiaryDiscoveryHandler.encountered(ron, ghoul);
                    BestiaryDiscoveryHandler.encountered(ron, spider);
                    String shape = dread.confront(boggart, level, List.of(ron));
                    Guise guise = Guise.decode(shape);
                    check(helper, guise != null && "acromantula".equals(guise.form()) && "acromantula".equals(boggart.getGuise()),
                            () -> "a boggart did not become the worst thing Ron had met: " + shape);
                    check(helper, tier(ron, page("boggart")) == DiscoveryTier.KNOWN,
                            () -> "seeing a boggart become your fear was not its signature");

                    // Two of them: it cannot decide, and frightens nobody.
                    ron.removeAllEffects();
                    neville.removeAllEffects();
                    dread.confront(boggart, level, List.of(ron, neville));
                    check(helper, !ron.hasEffect(MobEffects.DARKNESS) && !neville.hasEffect(MobEffects.DARKNESS),
                            () -> "a boggart facing two people still frightened them");

                    // The shape is decided by whoever is looking, never saved.
                    boggart.setGuise("acromantula");
                    GenericBeastEntity saved = roundTrip(helper, boggart);
                    check(helper, saved != null && saved.getGuise().isEmpty(), () -> "a boggart saved a shape");
                })
                .thenExecute(() -> {
                    retire(helper, ron);
                    retire(helper, neville);
                })
                .thenSucceed();
    }
}
