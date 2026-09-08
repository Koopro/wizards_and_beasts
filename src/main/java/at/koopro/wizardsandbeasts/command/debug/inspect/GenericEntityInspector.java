package at.koopro.wizardsandbeasts.command.debug.inspect;

import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleContentIndex;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.NullMarked;

/**
 * The entity fallback, for the same reason {@link GenericBlockInspector} is the block one.
 *
 * <p>Answers with identity, health, effects and the AI facts that explain most "why is it not doing
 * anything" reports: whether it has a target, whether it is persistent, whether it is no-AI.
 */
@NullMarked
public final class GenericEntityInspector implements DebugInspector.OfEntity {

    /** Effects listed before the panel gives up and prints a count. */
    private static final int MAX_EFFECTS = 6;

    @Override
    public String id() {
        return "entity";
    }

    @Override
    public String summary() {
        return "Any entity: type, health, effects, AI target, owning module.";
    }

    @Override
    public boolean matches(Entity entity) {
        return true;
    }

    @Override
    public DebugReport inspect(Entity entity, ServerPlayer viewer) {
        DebugReport report = DebugReport.of(entity.getName().getString());
        report.row("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        report.row("id / uuid", entity.getId() + " / " + entity.getUUID());
        report.row("pos", String.format("%.2f %.2f %.2f",
                entity.getX(), entity.getY(), entity.getZ()));

        Module owner = ModuleContentIndex.moduleOf(entity.getType());
        report.row("module", owner == null ? "(untagged)" : owner.name());
        if (owner != null) {
            report.flag("module accessible", ModuleContentIndex.isAccessible(entity.getType()));
        }

        if (entity instanceof LivingEntity living) {
            report.section("living");
            report.bar("  health", living.getMaxHealth() <= 0f
                    ? 0f : living.getHealth() / living.getMaxHealth());
            report.row("  hp", String.format("%.1f / %.1f", living.getHealth(), living.getMaxHealth()));
            report.flag("  on fire", living.isOnFire());
            report.row("  vehicle", living.getVehicle() == null
                    ? "none" : living.getVehicle().getName().getString());

            int shown = 0;
            for (MobEffectInstance effect : living.getActiveEffects()) {
                if (shown++ == 0) {
                    report.section("effects");
                }
                if (shown > MAX_EFFECTS) {
                    report.note("… " + (living.getActiveEffects().size() - MAX_EFFECTS) + " more");
                    break;
                }
                report.row("  " + effect.getEffect().value().getDisplayName().getString(),
                        "amp " + effect.getAmplifier() + ", " + effect.getDuration() + "t");
            }
        }

        if (entity instanceof Mob mob) {
            report.section("ai");
            report.flag("  no ai", mob.isNoAi());
            report.flag("  persistent", mob.isPersistenceRequired());
            report.row("  target", mob.getTarget() == null
                    ? "none" : mob.getTarget().getName().getString());
            report.flag("  leashed", mob.isLeashed());
        }
        return report;
    }
}
