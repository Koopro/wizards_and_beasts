package at.koopro.wizardsandbeasts.creature.companion;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.brew.silver.SilveredWeapons;
import at.koopro.wizardsandbeasts.creature.bond.BondProfile;
import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.creature.companion.CompanionAbility.Reveal;
import at.koopro.wizardsandbeasts.creature.companion.CompanionRules.Refusal;
import at.koopro.wizardsandbeasts.creature.companion.CompanionRules.Relation;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.companion.CompanionRosterS2CPayload;
import at.koopro.wizardsandbeasts.owl.career.track.ActivityType;
import at.koopro.wizardsandbeasts.owl.career.track.CareerProgression;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.render.outline.OutlineStyle;
import at.koopro.wizardsandbeasts.render.outline.SpellOutlines;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The companion layer's server side: everything that reads a creature or the world and decides. Built on
 * {@link BondableBeast} — the bond, owner, feeding, gifts and breeding stay where they were; this adds
 * personality, stages, commands, abilities, trust and the owner's roster on top.
 *
 * <p>Every method is a no-op for a creature without a {@link CompanionProfile}, so the layer reaches a species
 * by data alone, and for anything on a client level.
 */
@NullMarked
public final class Companions {

    /** Blocks within which the owner can give an order (RETURN reaches anywhere the creature is loaded). */
    public static final double COMMAND_RANGE = 64.0;
    /** Beyond this, RETURN brings it by magic rather than on foot. */
    private static final double RETURN_WALK_RANGE = 16.0;
    private static final double ASSIST_RANGE = 16.0;

    private static final TagKey<Block> SHINY = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "niffler_shiny_blocks"));
    private static final TagKey<Block> WANDWOOD = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "companion_wandwood"));

    private static final Set<UUID> ROSTER_DIRTY = new HashSet<>();

    private Companions() {}

    // -- lookups -----------------------------------------------------------------------------------------

    public static @Nullable CompanionProfile profile(BondableBeast beast) {
        return beast.bondProfile() == null ? null : CompanionData.profile(beast.bondMob().getType());
    }

    public static boolean isCompanion(BondableBeast beast) {
        return profile(beast) != null;
    }

    public static CompanionMemory memory(BondableBeast beast) {
        return beast.bondState().companion();
    }

    static void setMemory(BondableBeast beast, CompanionMemory memory) {
        beast.bondState().setCompanion(memory);
    }

    public static List<PersonalityTrait> traits(CompanionMemory memory) {
        List<PersonalityTrait> out = new ArrayList<>();
        for (String id : memory.traits()) {
            PersonalityTrait trait = CompanionData.trait(id);
            if (trait != null) {
                out.add(trait);
            }
        }
        return out;
    }

    public static long day(BondableBeast beast) {
        return beast.bondMob().level().getGameTime() / 24000L;
    }

    /** The bond a mastered companion must hold: the bond profile's mastery mark, else its ceiling. */
    static int masteryBond(BondProfile bond) {
        return bond.masteryBond().orElse(bond.maxBond());
    }

    public static CompanionStage stage(BondableBeast beast) {
        CompanionProfile profile = profile(beast);
        BondProfile bond = beast.bondProfile();
        if (profile == null || bond == null) {
            return CompanionStage.WILD;
        }
        return CompanionRules.stage(profile, beast.bondOwner() != null, beast.bondLevel(), bond.followThreshold(),
                masteryBond(bond), memory(beast).training());
    }

    /** May fight: the species says so and the creature has an attack to fight with. */
    public static boolean ableToFight(BondableBeast beast) {
        CompanionProfile profile = profile(beast);
        return profile != null && profile.canFight()
                && beast.bondMob().getAttribute(Attributes.ATTACK_DAMAGE) != null;
    }

    public static Relation relation(BondableBeast beast, Player player) {
        UUID owner = beast.bondOwner();
        if (owner != null && owner.equals(player.getUUID())) {
            return Relation.OWNER;
        }
        CompanionMemory memory = memory(beast);
        if (memory.trusts(player.getUUID())) {
            return Relation.TRUSTED;
        }
        if (memory.hates(player.getUUID())) {
            return Relation.HOSTILE;
        }
        return Relation.STRANGER;
    }

    /** Whether a player may hurt this creature (owner, a marked enemy, or a stranger under PvP rules). */
    public static boolean mayHarm(BondableBeast beast, Player attacker) {
        if (beast.bondOwner() == null) {
            return true;
        }
        boolean pvp = attacker.level() instanceof ServerLevel level && level.isPvpAllowed();
        return CompanionRules.mayHarm(relation(beast, attacker), Config.companionProtection, pvp);
    }

    /** Whether a player may feed, ride or calm it: unowned creatures, their owner and trusted friends. */
    public static boolean mayHandle(BondableBeast beast, Player player) {
        return beast.bondOwner() == null || CompanionRules.mayHandle(relation(beast, player));
    }

    // -- claiming and stages -----------------------------------------------------------------------------

    /** First bonded: it gets its personality and its first memory. Called when the bond layer sets an owner. */
    public static void onClaimed(BondableBeast beast, ServerPlayer owner) {
        CompanionProfile profile = profile(beast);
        if (profile == null) {
            return;
        }
        CompanionMemory memory = memory(beast);
        if (memory.traits().isEmpty()) {
            Random random = new Random(beast.bondMob().getUUID().getLeastSignificantBits());
            memory = memory.withTraits(CompanionRules.roll(profile.traitPool(), profile.traitsPerCreature(), random,
                    id -> CompanionData.trait(id) != null));
        }
        memory = memory.remember(day(beast), "companion.wizards_and_beasts.history.claimed", owner.getGameProfile().name());
        setMemory(beast, syncTrust(memory, owner));
        refreshRoster(beast);
    }

    /**
     * After the bond or training moved: notice a new stage. A rise is remembered, told to the owner and fed into
     * the bestiary (trusting → observed, bonded → studied) and the career (trained, mastered); a fall from a
     * betrayal just lowers the mark quietly.
     */
    public static void afterChange(BondableBeast beast) {
        CompanionProfile profile = profile(beast);
        PathfinderMob mob = beast.bondMob();
        if (profile == null || mob.level().isClientSide()) {
            return;
        }
        CompanionStage now = stage(beast);
        CompanionMemory memory = memory(beast);
        if (now == memory.lastStage()) {
            refreshRoster(beast);
            return;
        }
        boolean rose = now.ordinal() > memory.lastStage().ordinal();
        memory = memory.withStage(now);
        Player resolved = beast.resolveBondOwner();
        ServerPlayer owner = resolved instanceof ServerPlayer sp ? sp : null;
        if (rose) {
            memory = memory.remember(day(beast), "companion.wizards_and_beasts.history.stage", now.name());
            if (owner != null) {
                PlayerFeedback.toast(owner, NoticeKind.UNLOCK,
                        Component.translatable("companion.wizards_and_beasts.stage_reached", mob.getDisplayName()),
                        Component.translatable(now.key()));
                Identifier species = beast.bondSpecies();
                if (now == CompanionStage.TRUSTING) {
                    BestiaryDataHelper.setTier(owner, species, DiscoveryTier.OBSERVED);
                } else if (now == CompanionStage.BONDED) {
                    BestiaryDataHelper.setTier(owner, species, DiscoveryTier.STUDIED);
                } else if (now == CompanionStage.TRAINED) {
                    CareerProgression.record(owner, ActivityType.COMPANION_TRAINED, species);
                } else if (now == CompanionStage.MASTERED) {
                    CareerProgression.record(owner, ActivityType.COMPANION_MASTERED, species);
                }
            }
        }
        setMemory(beast, memory);
        refreshRoster(beast);
    }

    // -- tick --------------------------------------------------------------------------------------------

    /**
     * Once a second from the bond tick. Every few seconds the creature copies its owner's trust lists and
     * reports to the roster; while following, ground covered together is worth a little bond each day.
     */
    public static void tick(BondableBeast beast) {
        PathfinderMob mob = beast.bondMob();
        if (profile(beast) == null || beast.bondOwner() == null || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        int second = beast.bondState().nextCompanionSecond();
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(beast.bondOwner());
        if (owner == null) {
            return;
        }
        if (second % 20 == 0) {
            setMemory(beast, syncTrust(memory(beast), owner));
        }
        if (second % 5 == 0) {
            if (owner.level() == level && mob.distanceToSqr(owner) < 24 * 24
                    && memory(beast).modeOrFollow() == CompanionCommand.FOLLOW
                    && beast.bondState().visitChunk(mob.chunkPosition().toLong())) {
                interact(beast, Interaction.EXPLORE, owner);
            }
            refreshRoster(beast);
        }
    }

    // -- interactions ------------------------------------------------------------------------------------

    /**
     * Time spent well together: bond for {@code kind}, scaled by personality and held to the day's cap.
     *
     * @return the bond actually gained
     */
    public static int interact(BondableBeast beast, Interaction kind, ServerPlayer with) {
        CompanionProfile profile = profile(beast);
        if (profile == null || beast.bondOwner() == null || !ModuleManager.isEnabled(Module.CREATURES)) {
            return 0;
        }
        long day = day(beast);
        CompanionMemory memory = memory(beast);
        CompanionRules.Gain gain = CompanionRules.bondGain(kind, kind.baseGain(), traits(memory),
                memory.gainedToday(kind, day));
        if (gain.units() <= 0) {
            return 0;
        }
        setMemory(beast, memory.withGain(kind, gain.units(), day));
        if (gain.bond() > 0) {
            beast.increaseBond(with, gain.bond(), true);
        }
        return gain.bond();
    }

    /** The bond a feed is worth to this creature's personality; feeding keeps its own cooldown, not a cap. */
    public static int feedGain(BondableBeast beast, int base) {
        if (profile(beast) == null) {
            return base;
        }
        return CompanionRules.bondGain(Interaction.FEED, base, traits(memory(beast)), 0).bond();
    }

    /** An order carried out: training, and a little bond for the doing. */
    public static void trained(BondableBeast beast, ServerPlayer owner) {
        CompanionProfile profile = profile(beast);
        if (profile == null) {
            return;
        }
        long day = day(beast);
        CompanionMemory memory = memory(beast);
        int points = CompanionRules.trainingGain(traits(memory), memory.trainingToday(day));
        if (points > 0) {
            setMemory(beast, memory.withTraining(memory.training() + points, memory.trainingToday(day) + points, day));
        }
        interact(beast, Interaction.TRAINING, owner);
        afterChange(beast);
    }

    /** Copies the owner's lists onto the creature now rather than at its next periodic check. */
    public static void refreshTrust(BondableBeast beast) {
        if (beast.resolveBondOwner() instanceof ServerPlayer owner) {
            setMemory(beast, syncTrust(memory(beast), owner));
        }
    }

    /** Copies the owner's trusted and hostile lists onto the creature, which carries them while the owner is away. */
    static CompanionMemory syncTrust(CompanionMemory memory, ServerPlayer owner) {
        CompanionRoster roster = owner.getData(ModAttachments.COMPANION_ROSTER.get());
        if (memory.trusted().equals(roster.trusted()) && memory.hostile().equals(roster.hostile())) {
            return memory;
        }
        CompanionMemory next = memory;
        for (UUID id : new HashSet<>(memory.trusted())) {
            next = next.withTrust(id, false);
        }
        for (UUID id : new HashSet<>(memory.hostile())) {
            next = next.withHostile(id, false);
        }
        for (UUID id : roster.trusted()) {
            next = next.withTrust(id, true);
        }
        for (UUID id : roster.hostile()) {
            next = next.withHostile(id, true);
        }
        return next;
    }

    // -- commands ----------------------------------------------------------------------------------------

    /** The answer to an order, for the owner's action bar and for tests. */
    public enum Outcome { DONE, NOT_OWNER, TOO_FAR, NOT_A_COMPANION, REFUSED, NOTHING_FOUND, ON_COOLDOWN, NO_TARGET }

    /** Beastmaster capstone flag: companions obey one stage early and never refuse on temperament. */
    public static final String BEAST_MASTERS_VOICE = "beast_masters_voice";

    public static Refusal refusal(BondableBeast beast, CompanionCommand command) {
        CompanionProfile profile = profile(beast);
        if (profile == null) {
            return Refusal.UNKNOWN_TO_SPECIES;
        }
        // The owner's voice: the Beastmaster capstone is read from whoever the creature answers to, so a stranger
        // standing nearby never borrows it and the roster offers the owner exactly what the creature will take.
        boolean beastMaster = beast.resolveBondOwner() instanceof ServerPlayer owner
                && at.koopro.wizardsandbeasts.skill.SkillSystemAPI.hasAbility(owner, BEAST_MASTERS_VOICE);
        return CompanionRules.check(command, profile, stage(beast), traits(memory(beast)), ableToFight(beast),
                beastMaster);
    }

    /**
     * An order from {@code player}. Only the owner commands; the range, the species, the stage and the creature's
     * temperament all have their say. Modes stand until replaced; orders run now.
     */
    public static Outcome command(ServerPlayer player, BondableBeast beast, CompanionCommand command) {
        PathfinderMob mob = beast.bondMob();
        if (profile(beast) == null) {
            return Outcome.NOT_A_COMPANION;
        }
        if (relation(beast, player) != Relation.OWNER) {
            return Outcome.NOT_OWNER;
        }
        if (mob.level() != player.level()
                || (command != CompanionCommand.RETURN && mob.distanceToSqr(player) > COMMAND_RANGE * COMMAND_RANGE)) {
            return Outcome.TOO_FAR;
        }
        Refusal refusal = refusal(beast, command);
        if (refusal != Refusal.OK) {
            PlayerFeedback.actionBar(player, Component.translatable(
                    "companion.wizards_and_beasts.refused." + refusal.name().toLowerCase(java.util.Locale.ROOT),
                    mob.getDisplayName(), Component.translatable(command.key())));
            return Outcome.REFUSED;
        }
        Outcome outcome = command.isMode() ? setMode(player, beast, command) : order(player, beast, command);
        if (outcome == Outcome.DONE) {
            PlayerFeedback.actionBar(player, Component.translatable("companion.wizards_and_beasts.obeys",
                    mob.getDisplayName(), Component.translatable(command.key())).withStyle(ChatFormatting.GRAY));
        }
        refreshRoster(beast);
        return outcome;
    }

    private static Outcome setMode(ServerPlayer owner, BondableBeast beast, CompanionCommand mode) {
        PathfinderMob mob = beast.bondMob();
        BlockPos anchor = mode == CompanionCommand.FOLLOW ? null : mob.blockPosition();
        setMemory(beast, memory(beast).withMode(mode, anchor));
        mob.getNavigation().stop();
        if (mode == CompanionCommand.STAY || mode == CompanionCommand.REST || mode == CompanionCommand.FOLLOW) {
            mob.setTarget(null);
        }
        return Outcome.DONE;
    }

    private static Outcome order(ServerPlayer owner, BondableBeast beast, CompanionCommand order) {
        return switch (order) {
            case RETURN -> returnTo(owner, beast);
            case SEARCH -> search(owner, beast);
            case ASSIST -> assist(owner, beast);
            case ATTACK -> attack(owner, beast);
            default -> Outcome.REFUSED;
        };
    }

    private static Outcome returnTo(ServerPlayer owner, BondableBeast beast) {
        PathfinderMob mob = beast.bondMob();
        if (mob.distanceToSqr(owner) > RETURN_WALK_RANGE * RETURN_WALK_RANGE) {
            Vec3 spot = owner.position().add(owner.getLookAngle().multiply(-1.5, 0, -1.5));
            mob.teleportTo(spot.x, owner.getY(), spot.z);
            mob.getNavigation().stop();
        } else {
            mob.getNavigation().moveTo(owner, 1.2);
        }
        setMemory(beast, memory(beast).withMode(CompanionCommand.FOLLOW, null));
        trained(beast, owner);
        return Outcome.DONE;
    }

    private static Outcome attack(ServerPlayer owner, BondableBeast beast) {
        PathfinderMob mob = beast.bondMob();
        Vec3 eye = owner.getEyePosition();
        Vec3 end = eye.add(owner.getLookAngle().scale(24.0));
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(owner, eye, end,
                owner.getBoundingBox().expandTowards(owner.getLookAngle().scale(24.0)).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && e != mob && e != owner, 24.0 * 24.0);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target) || !mayTarget(beast, target)) {
            PlayerFeedback.actionBar(owner, Component.translatable("companion.wizards_and_beasts.no_target",
                    mob.getDisplayName()));
            return Outcome.NO_TARGET;
        }
        mob.setTarget(target);
        return Outcome.DONE;
    }

    /**
     * Whether a companion may be set on {@code target}: never its owner, a trusted friend or another of the
     * owner's creatures; a player only when the protection rules would let a player hurt them.
     */
    public static boolean mayTarget(BondableBeast beast, LivingEntity target) {
        UUID owner = beast.bondOwner();
        if (owner == null) {
            return false;
        }
        if (target instanceof Player player) {
            if (owner.equals(player.getUUID()) || memory(beast).trusts(player.getUUID())) {
                return false;
            }
            boolean pvp = player.level() instanceof ServerLevel level && level.isPvpAllowed();
            return memory(beast).hates(player.getUUID()) || pvp;
        }
        if (target instanceof BondableBeast other && owner.equals(other.bondOwner())) {
            return false;
        }
        return true;
    }

    // -- abilities ---------------------------------------------------------------------------------------

    private static boolean ready(BondableBeast beast, String key) {
        Long at = memory(beast).readyAt().get(key);
        return at == null || beast.bondMob().level().getGameTime() >= at;
    }

    private static void spend(BondableBeast beast, String key, int cooldownSeconds) {
        setMemory(beast, memory(beast).withReady(key, beast.bondMob().level().getGameTime() + cooldownSeconds * 20L));
    }

    private static Outcome search(ServerPlayer owner, BondableBeast beast) {
        CompanionProfile profile = profile(beast);
        if (profile == null) {
            return Outcome.NOT_A_COMPANION;
        }
        boolean anyReady = false;
        boolean found = false;
        for (CompanionAbility ability : profile.abilities()) {
            if (ability.command() != CompanionCommand.SEARCH || !ready(beast, ability.key())) {
                continue;
            }
            anyReady = true;
            int hits = switch (ability) {
                case Reveal reveal -> reveal(owner, beast, reveal);
                case CompanionAbility.RetrieveItems retrieve -> retrieve(owner, beast, retrieve);
                case CompanionAbility.Scout scout -> scout(owner, beast, scout);
                default -> 0;
            };
            spend(beast, ability.key(), cooldownOf(ability));
            found |= hits > 0;
            if (hits > 0) {
                PlayerFeedback.actionBar(owner, Component.translatable("companion.wizards_and_beasts.found",
                        beast.bondMob().getDisplayName(), hits, Component.translatable(ability.key())));
            }
        }
        if (!anyReady) {
            PlayerFeedback.actionBar(owner, Component.translatable("companion.wizards_and_beasts.tired",
                    beast.bondMob().getDisplayName()));
            return Outcome.ON_COOLDOWN;
        }
        if (!found) {
            PlayerFeedback.actionBar(owner, Component.translatable("companion.wizards_and_beasts.found_nothing",
                    beast.bondMob().getDisplayName()));
            return Outcome.NOTHING_FOUND;
        }
        interact(beast, Interaction.HELP, owner);
        trained(beast, owner);
        return Outcome.DONE;
    }

    private static int cooldownOf(CompanionAbility ability) {
        return switch (ability) {
            case CompanionAbility.HealOwner h -> h.cooldownSeconds();
            case CompanionAbility.CleanseOwner c -> c.cooldownSeconds();
            case Reveal r -> r.cooldownSeconds();
            case CompanionAbility.RetrieveItems r -> r.cooldownSeconds();
            case CompanionAbility.Scout s -> s.cooldownSeconds();
            case CompanionAbility.Innate ignored -> 0;
        };
    }

    /** Outlines what the creature senses, for the owner. Returns how many things it found. */
    static int reveal(ServerPlayer owner, BondableBeast beast, Reveal reveal) {
        PathfinderMob mob = beast.bondMob();
        ServerLevel level = (ServerLevel) mob.level();
        int duration = 200;
        switch (reveal.target()) {
            case VALUABLES, WANDWOOD -> {
                TagKey<Block> tag = reveal.target() == CompanionAbility.RevealTarget.VALUABLES ? SHINY : WANDWOOD;
                List<BlockPos> found = new ArrayList<>();
                BlockPos centre = mob.blockPosition();
                int r = reveal.radius();
                for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
                    if (level.getBlockState(pos).is(tag)) {
                        found.add(pos.immutable());
                        if (found.size() >= 64) {
                            break;
                        }
                    }
                }
                if (!found.isEmpty()) {
                    SpellOutlines.highlightBlocks(owner, found, new OutlineStyle(0xFFFFD24A, duration));
                }
                return found.size();
            }
            case THREATS, DARK -> {
                AABB box = mob.getBoundingBox().inflate(reveal.radius());
                List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != mob && e.isAlive()
                        && e != owner && (reveal.target() == CompanionAbility.RevealTarget.THREATS
                        ? e instanceof Enemy || e.isInvisible()
                        : isDark(e)));
                if (!found.isEmpty()) {
                    SpellOutlines.highlightEntities(found, new OutlineStyle(
                            reveal.target() == CompanionAbility.RevealTarget.DARK ? 0xFF7A3CFF : 0xFFFF4A4A, duration));
                }
                return found.size();
            }
        }
        return 0;
    }

    /** A dark creature, or a wizard the Dark Arts have marked deeply. */
    static boolean isDark(LivingEntity entity) {
        if (entity.getType().is(SilveredWeapons.DARK_CREATURES)) {
            return true;
        }
        return entity instanceof Player player && player.getData(ModAttachments.DARK_CORRUPTION.get()) >= 25.0f;
    }

    private static int retrieve(ServerPlayer owner, BondableBeast beast, CompanionAbility.RetrieveItems retrieve) {
        PathfinderMob mob = beast.bondMob();
        List<ItemEntity> items = mob.level().getEntitiesOfClass(ItemEntity.class,
                mob.getBoundingBox().inflate(retrieve.radius()), ItemEntity::isAlive);
        int moved = 0;
        for (ItemEntity item : items) {
            if (moved >= retrieve.maxStacks()) {
                break;
            }
            item.teleportTo(owner.getX(), owner.getY() + 0.5, owner.getZ());
            item.setNoPickUpDelay();
            moved++;
        }
        return moved;
    }

    private static int scout(ServerPlayer owner, BondableBeast beast, CompanionAbility.Scout scout) {
        PathfinderMob mob = beast.bondMob();
        mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, scout.seconds() * 20, 0, false, false));
        return reveal(owner, beast, new Reveal(CompanionAbility.RevealTarget.THREATS, scout.radius(), 0));
    }

    private static Outcome assist(ServerPlayer owner, BondableBeast beast) {
        CompanionProfile profile = profile(beast);
        PathfinderMob mob = beast.bondMob();
        if (profile == null) {
            return Outcome.NOT_A_COMPANION;
        }
        if (mob.distanceToSqr(owner) > ASSIST_RANGE * ASSIST_RANGE) {
            return Outcome.TOO_FAR;
        }
        boolean helped = false;
        boolean anyReady = false;
        for (CompanionAbility ability : profile.abilities()) {
            if (ability.command() != CompanionCommand.ASSIST || !ready(beast, ability.key())) {
                continue;
            }
            anyReady = true;
            boolean did = switch (ability) {
                case CompanionAbility.HealOwner heal -> {
                    if (owner.getHealth() >= owner.getMaxHealth()) {
                        yield false;
                    }
                    owner.heal(heal.amount());
                    yield true;
                }
                case CompanionAbility.CleanseOwner ignored -> {
                    List<MobEffectInstance> harmful = new ArrayList<>();
                    for (MobEffectInstance effect : owner.getActiveEffects()) {
                        if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                            harmful.add(effect);
                        }
                    }
                    harmful.forEach(effect -> owner.removeEffect(effect.getEffect()));
                    yield !harmful.isEmpty();
                }
                default -> false;
            };
            if (did) {
                spend(beast, ability.key(), cooldownOf(ability));
                helped = true;
                PlayerFeedback.actionBar(owner, Component.translatable("companion.wizards_and_beasts.assisted",
                        mob.getDisplayName(), Component.translatable(ability.key())));
            }
        }
        if (!anyReady) {
            return Outcome.ON_COOLDOWN;
        }
        if (!helped) {
            PlayerFeedback.actionBar(owner, Component.translatable("companion.wizards_and_beasts.nothing_to_do",
                    mob.getDisplayName()));
            return Outcome.NOTHING_FOUND;
        }
        if (mob.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, owner.getX(), owner.getY() + 1.8,
                    owner.getZ(), 5, 0.4, 0.3, 0.4, 0.0);
        }
        interact(beast, Interaction.HELP, owner);
        trained(beast, owner);
        return Outcome.DONE;
    }

    // -- release -----------------------------------------------------------------------------------------

    /** The owner lets it go: it forgets them and returns to the wild with its personality and story. */
    public static Outcome release(ServerPlayer owner, BondableBeast beast) {
        if (relation(beast, owner) != Relation.OWNER) {
            return Outcome.NOT_OWNER;
        }
        UUID id = beast.bondMob().getUUID();
        beast.bondState().setOwner(null);
        beast.setSyncedBondLevel(beast.bondState().setLevel(0, Integer.MAX_VALUE));
        setMemory(beast, memory(beast).released(day(beast)));
        owner.setData(ModAttachments.COMPANION_ROSTER.get(), owner.getData(ModAttachments.COMPANION_ROSTER.get()).without(id));
        markDirty(owner);
        return Outcome.DONE;
    }

    // -- roster ------------------------------------------------------------------------------------------

    /** Writes this companion's current state into its owner's roster, when the owner is online. */
    public static void refreshRoster(BondableBeast beast) {
        PathfinderMob mob = beast.bondMob();
        if (mob.level().isClientSide() || profile(beast) == null || beast.bondOwner() == null
                || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(beast.bondOwner());
        if (owner == null) {
            return;
        }
        CompanionRoster roster = owner.getData(ModAttachments.COMPANION_ROSTER.get());
        CompanionRoster.Entry entry = entryOf(beast);
        if (entry.equals(roster.entries().get(mob.getUUID()))) {
            return;
        }
        owner.setData(ModAttachments.COMPANION_ROSTER.get(), roster.with(mob.getUUID(), entry));
        markDirty(owner);
    }

    static CompanionRoster.Entry entryOf(BondableBeast beast) {
        PathfinderMob mob = beast.bondMob();
        CompanionProfile profile = profile(beast);
        BondProfile bond = beast.bondProfile();
        CompanionMemory memory = memory(beast);
        boolean fight = ableToFight(beast);
        List<CompanionCommand> commands = profile == null ? List.of() : CompanionRules.offered(profile, fight);
        List<CompanionCommand> usable = new ArrayList<>();
        for (CompanionCommand command : commands) {
            if (refusal(beast, command) == Refusal.OK) {
                usable.add(command);
            }
        }
        List<String> abilities = new ArrayList<>();
        if (profile != null) {
            profile.abilities().forEach(a -> abilities.add(a.key()));
        }
        List<CompanionMemory.HistoryEntry> history = memory.history();
        String name = mob.hasCustomName() && mob.getCustomName() != null ? mob.getCustomName().getString() : "";
        return new CompanionRoster.Entry(beast.bondSpecies(), name, beast.bondLevel(),
                bond == null ? 100 : bond.maxBond(), stage(beast), memory.traits(), memory.training(),
                profile == null ? 0 : profile.trainedAt(), profile == null ? 0 : profile.masteredAt(),
                (float) Math.ceil(mob.getHealth()), mob.getMaxHealth(), memory.modeOrFollow(),
                mob.level().dimension().identifier().toString(), mob.getBlockX(), mob.getBlockY(), mob.getBlockZ(),
                mob.isAlive(), day(beast), history, memory.generation(), memory.parents(), commands, usable, abilities);
    }

    /** The companion died for good: its entry becomes a memorial. */
    public static void onDied(BondableBeast beast) {
        PathfinderMob mob = beast.bondMob();
        if (profile(beast) == null || beast.bondOwner() == null || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(beast.bondOwner());
        if (owner == null) {
            return;
        }
        CompanionRoster roster = owner.getData(ModAttachments.COMPANION_ROSTER.get());
        CompanionRoster.Entry entry = roster.entries().getOrDefault(mob.getUUID(), entryOf(beast));
        owner.setData(ModAttachments.COMPANION_ROSTER.get(), roster.with(mob.getUUID(), entry.deceased(day(beast),
                new CompanionMemory.HistoryEntry(day(beast), "companion.wizards_and_beasts.history.died", ""))));
        markDirty(owner);
        PlayerFeedback.toast(owner, NoticeKind.FAIL,
                Component.translatable("companion.wizards_and_beasts.died", mob.getDisplayName()), null);
    }

    public static void markDirty(ServerPlayer owner) {
        synchronized (ROSTER_DIRTY) {
            ROSTER_DIRTY.add(owner.getUUID());
        }
    }

    /** Sends each changed roster once, at the end of the server tick: many changes, one packet. */
    public static void flushRosters(net.minecraft.server.MinecraftServer server) {
        List<UUID> dirty;
        synchronized (ROSTER_DIRTY) {
            if (ROSTER_DIRTY.isEmpty()) {
                return;
            }
            dirty = new ArrayList<>(ROSTER_DIRTY);
            ROSTER_DIRTY.clear();
        }
        for (UUID id : dirty) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                CompanionRosterS2CPayload.send(player);
            }
        }
    }

    /** The species' display name, for history lines and the roster. */
    public static Component speciesName(Identifier species) {
        return Component.translatable(BuiltInRegistries.ENTITY_TYPE.getValue(species).getDescriptionId());
    }

    /** Resolves a companion entity by id in the owner's level, or {@code null} when it is not loaded there. */
    public static @Nullable BondableBeast find(ServerPlayer owner, UUID id) {
        Entity entity = owner.level().getEntity(id);
        return entity instanceof BondableBeast beast && entity.isAlive() ? beast : null;
    }
}
