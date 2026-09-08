package at.koopro.wizardsandbeasts.entity.debug;

import at.koopro.wizardsandbeasts.bestiary.data.PlayerBestiaryData;
import at.koopro.wizardsandbeasts.command.debug.inspect.DebugInspector;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.creature.AlphaRoster;
import at.koopro.wizardsandbeasts.creature.Trait;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * A beast's sheet: which creature it is, which traits it carries, and what the person looking at it
 * has recorded about it.
 *
 * <p>The last of those is the reason this is not just {@code GenericEntityInspector} with extra
 * rows. Bestiary tier is per-<em>viewer</em>, so the same unicorn reads SEEN to one wizard and
 * MASTERED to another, and a harvest that refuses is nearly always a tier the harvester has not
 * reached rather than anything wrong with the beast. Showing the viewer's own tier beside the
 * beast's identity puts both halves of that answer in one place.
 */
@NullMarked
public final class CreatureDebugInspector implements DebugInspector.OfEntity {

    @Override
    public String id() {
        return "beast";
    }

    @Override
    public String summary() {
        return "Beast: creature id, traits, alpha-roster status, your bestiary tier for it.";
    }

    @Override
    public boolean matches(Entity entity) {
        return entity instanceof GenericBeastEntity;
    }

    @Override
    public DebugReport inspect(Entity entity, ServerPlayer viewer) {
        GenericBeastEntity beast = (GenericBeastEntity) entity;
        Identifier creatureId = beast.creatureId();
        DebugReport report = DebugReport.of("Beast · " + creatureId);

        report.row("creature id", creatureId.toString());
        report.state("alpha roster", AlphaRoster.isAlpha(creatureId) ? "shipped" : "not in the slice",
                AlphaRoster.isAlpha(creatureId) ? ChatPalette.OK : ChatPalette.MUTED);
        report.row("health", String.format("%.1f / %.1f", beast.getHealth(), beast.getMaxHealth()));

        report.section("appearance");
        report.row("  render scale", String.format("%.2f", beast.getRenderScale()));
        report.row("  tint", String.format("#%08X", beast.getTint()));
        report.flag("  disguised", beast.isDisguised());

        List<String> traits = new ArrayList<>();
        for (Trait trait : Trait.values()) {
            if (beast.has(trait)) {
                traits.add(trait.name());
            }
        }
        report.section("traits (" + traits.size() + ")");
        if (traits.isEmpty()) {
            report.row("  —", "none");
        } else {
            report.row("  carried", String.join(", ", traits));
        }

        report.section("timers");
        report.row("  fire dry", beast.getFireDryTicks());
        report.row("  water dry", beast.getWaterDryTicks());

        report.section("ai");
        report.flag("  no ai", beast.isNoAi());
        report.flag("  persistent", beast.isPersistenceRequired());
        report.row("  target", targetName(beast));

        // The viewer's own record, not the beast's. See the class note.
        report.section("your bestiary");
        PlayerBestiaryData bestiary = viewer.getData(ModAttachments.BESTIARY_DATA.get());
        var tier = bestiary.tiers().get(creatureId);
        report.row("  tier", tier == null ? "UNDISCOVERED" : tier.name());
        Long lastHarvest = bestiary.lastHarvests().get(creatureId);
        report.row("  last harvest", lastHarvest == null ? "never"
                : (viewer.level().getGameTime() - lastHarvest) + "t ago");
        return report;
    }

    private static String targetName(LivingEntity beast) {
        if (!(beast instanceof Mob mob) || mob.getTarget() == null) {
            return "none";
        }
        return mob.getTarget().getName().getString();
    }
}
