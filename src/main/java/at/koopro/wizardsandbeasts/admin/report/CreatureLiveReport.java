package at.koopro.wizardsandbeasts.admin.report;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.report.ReportView.Tone;
import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.creature.companion.Companions;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyCapability;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyProfileRegistry;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyState;
import at.koopro.wizardsandbeasts.creature.variant.CreatureVariant;
import at.koopro.wizardsandbeasts.creature.variant.CreatureVariants;
import at.koopro.wizardsandbeasts.creature.variant.VariantHolder;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Creatures → Live: one living creature of the mod, read off the entity now — identity, variant, size, bond, ecology
 * intent and the AI goals running this tick — with buttons for the {@link at.koopro.wizardsandbeasts.admin.operation.CreatureOperations}.
 * Without a subject it lists the loaded creatures (nearest first when an administrator in the world asks) and how many
 * of each species are loaded.
 *
 * <p>Reading needs the Creatures section's capability; positions only show to those who may act on the world, the
 * same rule the Players section uses.
 */
@NullMarked
final class CreatureLiveReport implements AdminReport {

    static final String ID = "creature_live";
    private static final int NEARBY = 96;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public AdminCategory section() {
        return AdminCategory.CREATURES;
    }

    /** A loaded creature of this mod (a {@link Mob} whose type is registered under the mod's namespace). */
    static boolean isModCreature(Entity entity) {
        if (!(entity instanceof Mob)) {
            return false;
        }
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return WizardsAndBeastsMod.MODID.equals(key.getNamespace());
    }

    /** The loaded entity with this UUID in any dimension, or null. */
    static @Nullable Entity find(MinecraftServer server, String subject) {
        UUID id;
        try {
            id = UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    @Override
    public void build(Request request, ReportBuilder out) {
        boolean positions = request.actor().canModify(AdminCapability.WORLD);
        ServerPlayer viewer = request.actor().actorId() == null ? null
                : request.server().getPlayerList().getPlayer(request.actor().actorId());
        subjects(request.server(), viewer, positions, out);
        Entity entity = request.subject().isEmpty() ? null : find(request.server(), request.subject());
        if (entity == null || !isModCreature(entity)) {
            out.section("loaded");
            if (!request.subject().isEmpty()) {
                out.literalNote(request.subject() + " — not loaded", Tone.WARN);
            }
            census(request.server(), out);
            out.note("pick_hint");
            return;
        }
        Mob mob = (Mob) entity;
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
        out.literalSection(mob.getName().getString() + " · " + typeId);
        out.row("uuid", mob.getStringUUID());
        out.row("health", String.format(Locale.ROOT, "%.1f / %.1f", mob.getHealth(), mob.getMaxHealth()),
                mob.getHealth() < mob.getMaxHealth() / 3 ? Tone.WARN : Tone.NORMAL);
        out.row("where", positions ? mob.level().dimension().identifier() + " " + mob.blockPosition().toShortString()
                : "—");
        out.flag("persistent", mob.isPersistenceRequired(), true);
        out.flag("no_ai", mob.isNoAi(), false);
        out.row("age_ticks", mob.tickCount);

        out.section("body");
        out.row("scale", String.format(Locale.ROOT, "×%.2f", mob.getScale()));
        var scale = mob.getAttribute(Attributes.SCALE);
        if (scale != null) {
            out.row("scale_base", String.format(Locale.ROOT, "%.2f", scale.getBaseValue()));
            scale.getModifiers().forEach(modifier -> out.literal(modifier.id().getPath(),
                    String.format(Locale.ROOT, "%+.2f (%s)", modifier.amount(),
                            modifier.operation().getSerializedName()), Tone.MUTED));
        }
        if (mob instanceof VariantHolder holder) {
            out.row("variant", holder.variant().variantId());
        } else {
            out.rowKey("variant", ReportBuilder.KEY_ROOT + "none", Tone.MUTED);
        }

        out.section("bond");
        if (mob instanceof BondableBeast beast) {
            UUID owner = beast.bondOwner();
            ServerPlayer ownerPlayer = owner == null ? null : request.server().getPlayerList().getPlayer(owner);
            out.row("owner", owner == null ? "—" : ownerPlayer != null ? ownerPlayer.getName().getString() : owner.toString());
            out.row("bond_level", beast.bondLevel());
            out.row("stage", Companions.stage(beast).name().toLowerCase(Locale.ROOT));
            out.flag("juvenile", beast.isJuvenileBeast(), false);
            out.row("growth_ticks", beast.bondState().growthTicks());
            out.row("species", beast.bondSpecies().toString());
        } else {
            out.note("not_bondable");
        }

        out.section("ecology");
        // Read only when the creature already has one: getData would attach a fresh state just by looking.
        if (mob.hasData(ModAttachments.ECOLOGY_STATE.get())) {
            EcologyState ecology = mob.getData(ModAttachments.ECOLOGY_STATE.get());
            out.row("intent", ecology.intent().name().toLowerCase(Locale.ROOT));
            EcologyCapability cause = ecology.decision().cause();
            out.row("cause", cause == null ? "—" : cause.getSerializedName());
            out.flag("awake", ecology.awake(), true);
            Vec3 home = ecology.home();
            out.row("home", home == null ? "—" : positions
                    ? String.format(Locale.ROOT, "%.0f %.0f %.0f", home.x, home.y, home.z) : "set");
        } else {
            out.note("no_ecology_state");
        }
        List<String> capabilities = new ArrayList<>();
        EcologyProfileRegistry.of(mob.getType()).capabilities().forEach(c -> capabilities.add(c.getSerializedName()));
        capabilities.sort(String::compareTo);
        out.row("capabilities", capabilities.isEmpty() ? "—" : String.join(", ", capabilities));

        out.section("goals");
        goals(mob.goalSelector.getAvailableGoals(), out, "");
        goals(mob.targetSelector.getAvailableGoals(), out, "target ");
        out.row("target", mob.getTarget() == null ? "—" : mob.getTarget().getName().getString());
        out.row("navigating", mob.getNavigation().isInProgress() ? "yes" : "no");

        // The buttons. Operations re-check everything on the server; these only offer them.
        out.action("creature_scale", "act_scale_half", "0.5", false);
        out.action("creature_scale", "act_scale_one", "1", false);
        out.action("creature_scale", "act_scale_double", "2", false);
        if (mob instanceof VariantHolder) {
            for (CreatureVariant variant : CreatureVariants.of(typeId)) {
                out.action("creature_variant", "act_variant", variant.variantId(), false);
            }
        }
        out.action("creature_freeze", mob.isNoAi() ? "act_unfreeze" : "act_freeze", mob.isNoAi() ? "off" : "on", false);
        out.action("creature_heal", "act_heal", "", false);
        out.action("creature_discard", "act_discard", "", true);
    }

    private static void goals(java.util.Set<WrappedGoal> goals, ReportBuilder out, String prefix) {
        goals.stream().sorted(Comparator.comparingInt(WrappedGoal::getPriority)).forEach(goal ->
                out.literal(prefix + goal.getPriority() + " " + goal.getGoal().getClass().getSimpleName(),
                        goal.isRunning() ? "running" : "idle", goal.isRunning() ? Tone.GOOD : Tone.MUTED));
    }

    private static void census(MinecraftServer server, ReportBuilder out) {
        Map<String, Integer> counts = new TreeMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (isModCreature(entity)) {
                    counts.merge(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath(), 1, Integer::sum);
                }
            }
        }
        out.row("total", counts.values().stream().mapToInt(Integer::intValue).sum());
        counts.forEach((type, count) -> out.literal(type, count));
    }

    private static void subjects(MinecraftServer server, @Nullable ServerPlayer viewer, boolean positions,
                                 ReportBuilder out) {
        record Found(Entity entity, double distance) {}
        List<Found> found = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!isModCreature(entity)) {
                    continue;
                }
                double distance = viewer != null && viewer.level() == level ? viewer.distanceTo(entity) : Double.MAX_VALUE;
                if (viewer == null || distance <= NEARBY || !positions) {
                    found.add(new Found(entity, distance));
                }
            }
        }
        found.sort(Comparator.comparingDouble(Found::distance));
        for (Found f : found) {
            Entity entity = f.entity();
            String note = f.distance() == Double.MAX_VALUE ? entity.level().dimension().identifier().getPath()
                    : String.format(Locale.ROOT, "%.0fm", f.distance());
            out.subjectOption(entity.getStringUUID(), entity.getName().getString(), positions ? note : "");
        }
    }
}
