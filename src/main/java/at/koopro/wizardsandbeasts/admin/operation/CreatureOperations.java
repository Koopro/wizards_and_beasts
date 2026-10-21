package at.koopro.wizardsandbeasts.admin.operation;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.creature.variant.VariantHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Operations on one loaded creature of the mod, offered by the Creatures → Live report. All act on the world, so
 * they need {@code admin.world} (the capability the test spawns use), whatever section shows them.
 *
 * <ul>
 *   <li>{@code creature_scale <factor>}: an admin size modifier ({@value #SCALE_MIN}–{@value #SCALE_MAX}) on top of the
 *       species' own scale modifiers — ×1 removes it. Saved with the entity; moves hitbox and model together.</li>
 *   <li>{@code creature_variant <id>}: through the creature's own {@link VariantHolder#applyVariant}.</li>
 *   <li>{@code creature_freeze on|off}: vanilla no-AI.</li>
 *   <li>{@code creature_heal}: to full health.</li>
 *   <li>{@code creature_discard} (destructive): removes it; refused for a bonded companion, whose owner's roster
 *       would otherwise keep a creature that no longer exists.</li>
 * </ul>
 */
@NullMarked
public final class CreatureOperations {

    public static final double SCALE_MIN = 0.25;
    public static final double SCALE_MAX = 4.0;
    static final Identifier ADMIN_SCALE = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "admin_scale");

    private CreatureOperations() {}

    static void registerAll() {
        AdminOperations.register(op("creature_scale", false, request -> {
            Double factor = parseScale(request.argument());
            return factor == null ? "a factor from " + SCALE_MIN + " to " + SCALE_MAX : null;
        }, (mob, request) -> {
            double factor = parseScale(request.argument());
            AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
            if (scale == null) {
                return AdminOperation.Outcome.refused("not_eligible", "no scale attribute");
            }
            scale.removeModifier(ADMIN_SCALE);
            if (Math.abs(factor - 1.0) > 1.0e-6) {
                scale.addPermanentModifier(new AttributeModifier(ADMIN_SCALE, factor - 1.0,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            return AdminOperation.Outcome.ok(String.format(Locale.ROOT, "%s now ×%.2f", name(mob), mob.getScale()));
        }));
        AdminOperations.register(op("creature_variant", false,
                request -> request.argument().isEmpty() ? "variant id expected" : null,
                (mob, request) -> {
                    if (!(mob instanceof VariantHolder holder)) {
                        return AdminOperation.Outcome.refused("not_eligible", name(mob) + " has no variants");
                    }
                    String before = holder.variant().variantId();
                    return holder.applyVariant(request.argument())
                            ? AdminOperation.Outcome.ok(name(mob) + " " + before + " → " + request.argument())
                            : AdminOperation.Outcome.refused("invalid_argument", "unknown variant " + request.argument());
                }));
        AdminOperations.register(op("creature_freeze", false,
                request -> request.argument().equals("on") || request.argument().equals("off") ? null : "on or off",
                (mob, request) -> {
                    mob.setNoAi(request.argument().equals("on"));
                    return AdminOperation.Outcome.ok(name(mob) + (mob.isNoAi() ? " frozen" : " thawed"));
                }));
        AdminOperations.register(op("creature_heal", false, request -> null, (mob, request) -> {
            float before = mob.getHealth();
            mob.setHealth(mob.getMaxHealth());
            return AdminOperation.Outcome.ok(String.format(Locale.ROOT, "%s %.1f → %.1f", name(mob), before, mob.getHealth()));
        }));
        AdminOperations.register(op("creature_discard", true, request -> null, (mob, request) -> {
            if (mob instanceof BondableBeast beast && beast.bondOwner() != null) {
                return AdminOperation.Outcome.refused("not_eligible", name(mob) + " is bonded to " + beast.bondOwner());
            }
            String what = name(mob);
            mob.discard();
            return AdminOperation.Outcome.ok(what + " removed");
        }));
    }

    private static @Nullable Double parseScale(String text) {
        try {
            double value = Double.parseDouble(text);
            return value >= SCALE_MIN && value <= SCALE_MAX ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String name(Mob mob) {
        return mob.getName().getString() + " (" + mob.getStringUUID().substring(0, 8) + ")";
    }

    /** The loaded mod creature {@code subject} names, or null. */
    static @Nullable Mob creature(MinecraftServer server, String subject) {
        UUID id;
        try {
            id = UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Mob mob && entity.isAlive()
                    && WizardsAndBeastsMod.MODID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace())) {
                return mob;
            }
        }
        return null;
    }

    private static AdminOperation op(String id, boolean destructive,
                                     Function<AdminOperation.Request, @Nullable String> check,
                                     BiFunction<Mob, AdminOperation.Request, AdminOperation.Outcome> act) {
        return new AdminOperation() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public AdminCategory section() {
                return AdminCategory.CREATURES;
            }

            @Override
            public AdminCapability capability() {
                return AdminCapability.WORLD;
            }

            @Override
            public boolean destructive() {
                return destructive;
            }

            @Override
            public @Nullable String validate(Request request) {
                if (creature(request.server(), request.subject()) == null) {
                    return "no loaded creature " + request.subject();
                }
                return check.apply(request);
            }

            @Override
            public Outcome execute(Request request) {
                Mob mob = creature(request.server(), request.subject());
                return mob == null ? Outcome.refused("invalid_argument", "gone") : act.apply(mob, request);
            }
        };
    }
}
