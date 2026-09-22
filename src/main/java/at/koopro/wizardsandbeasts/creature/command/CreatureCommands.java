package at.koopro.wizardsandbeasts.creature.command;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.AlphaRoster;
import at.koopro.wizardsandbeasts.creature.CreatureDefinition;
import at.koopro.wizardsandbeasts.creature.CreatureDefinitionRegistry;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModCreatures;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@code /wandb beast creature …} (gamemaster). Summons generic creatures by id and lists the build status.
 * Access is gated by {@link Module#CREATURES}; registration is always present.
 */
public final class CreatureCommands {

    private CreatureCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("creature")
                .then(Commands.literal("summon")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        ModCreatures.MANIFEST.stream().map(ModCreatures.Spec::id), b))
                                .executes(ctx -> summon(ctx.getSource(), StringArgumentType.getString(ctx, "id"), 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 32))
                                        .executes(ctx -> summon(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())))
                // A parade ground for every mob the mod registers: one of each, AI off, labelled, in a grid.
                // What it is for is looking at them — rig checks, scale comparisons, screenshots — so it says
                // nothing about spawning rules and deliberately ignores the module gate: registration is
                // always present, and a disabled module should not stop an operator inspecting the art.
                .then(Commands.literal("lineup")
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearLineup(ctx.getSource())))
                        .executes(ctx -> lineup(ctx.getSource(), DEFAULT_SPACING))
                        .then(Commands.argument("spacing", IntegerArgumentType.integer(1, 16))
                                .executes(ctx -> lineup(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "spacing")))));
    }

    /** Blocks between neighbours in the lineup. Three clears a thestral's shoulders without spreading a mile. */
    private static final int DEFAULT_SPACING = 3;

    /** Entity tag every lineup member carries, so {@code lineup clear} can find exactly them again. */
    private static final String LINEUP_TAG = "wandb_lineup";

    /**
     * One of every mod mob, stood in a grid in front of the caster with its AI switched off.
     *
     * <p>Membership is decided by what the entity <em>is</em> rather than by a hand-kept list: every type in
     * the mod's namespace that instantiates to a {@link Mob}. That takes in the 107 generic beasts, the ten
     * bespoke ones, the dementor and the goblin teller, and leaves out brooms, beams, projectiles and shields
     * without anybody having to remember to exclude them.
     *
     * <p>Each one is tagged, named with its id, made persistent and frozen ({@code NoAI}), so the row stays
     * where it was put and can be cleared in one command afterwards.
     */
    private static int lineup(CommandSourceStack src, int spacing)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();

        List<EntityType<?>> mobs = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (!WizardsAndBeastsMod.MODID.equals(id.getNamespace())) {
                continue;
            }
            mobs.add(type);
        }
        mobs.sort(Comparator.comparing(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath()));

        // A square-ish grid laid out ahead of the caster, so a hundred creatures stay in one eyeful.
        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(mobs.size())));
        double originX = player.getX();
        double originZ = player.getZ() + spacing * 2.0;
        int placed = 0;
        int skipped = 0;
        for (EntityType<?> type : mobs) {
            Entity entity = type.create(level, EntitySpawnReason.COMMAND);
            if (!(entity instanceof Mob mob)) {
                // Not a mob: a broom, a bolt, a ward. Nothing to line up.
                skipped++;
                continue;
            }
            int index = placed;
            double x = originX + (index % columns - columns / 2.0) * spacing;
            double z = originZ + (index / columns) * spacing;
            mob.snapTo(x, player.getY(), z, 180.0f, 0.0f);
            mob.setNoAi(true);
            mob.setPersistenceRequired();
            mob.addTag(LINEUP_TAG);
            mob.setCustomName(Component.literal(
                    BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath()));
            mob.setCustomNameVisible(true);
            if (level.addFreshEntity(mob)) {
                placed++;
            } else {
                skipped++;
            }
        }

        int finalPlaced = placed;
        int finalSkipped = skipped;
        src.sendSuccess(() -> Component.literal("Lined up " + finalPlaced + " mod mobs with AI off"
                        + (finalSkipped > 0 ? " (" + finalSkipped + " non-mob types skipped)" : "")
                        + ". Clear them with /wandb beast creature lineup clear.")
                .withStyle(ChatFormatting.GREEN), true);
        return placed;
    }

    /** Removes everything {@link #lineup} placed, and nothing else. */
    private static int clearLineup(CommandSourceStack src)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();
        List<Entity> tagged = new ArrayList<>();
        level.getEntities((EntityType<?>) null, entity -> entity.getTags().contains(LINEUP_TAG), tagged);
        for (Entity entity : tagged) {
            entity.discard();
        }
        int removed = tagged.size();
        src.sendSuccess(() -> Component.literal("Cleared " + removed + " lined-up mobs")
                .withStyle(ChatFormatting.GREEN), true);
        return removed;
    }

    private static int summon(CommandSourceStack src, String id, int count)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!ModuleManager.isEnabled(Module.CREATURES)) {
            src.sendFailure(Component.literal("The CREATURES module is disabled."));
            return 0;
        }
        var holder = ModCreatures.ENTITIES.get(id);
        if (holder == null) {
            src.sendFailure(Component.literal("Unknown creature id: " + id));
            return 0;
        }
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();
        EntityType<?> type = holder.get();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Entity entity = type.create(level, EntitySpawnReason.COMMAND);
            if (entity == null) {
                continue;
            }
            entity.setPos(player.getX(), player.getY(), player.getZ());
            if (level.addFreshEntity(entity)) {
                spawned++;
            }
        }
        int finalSpawned = spawned;
        src.sendSuccess(() -> Component.literal("Summoned " + finalSpawned + "x " + id)
                .withStyle(ChatFormatting.GREEN), true);
        return spawned;
    }

    /**
     * The build report. Each line now says whether the creature is on the alpha roster or still wears
     * a placeholder rig, because "registered" and "finished" were the same colour before and the list
     * read as 100 working creatures.
     */
    private static int list(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("Generic creatures: " + ModCreatures.MANIFEST.size()
                + " registered, " + CreatureDefinitionRegistry.size() + " definitions loaded, "
                + AlphaRoster.SHIPPED.size() + " on the alpha roster")
                .withStyle(ChatFormatting.GOLD), false);
        for (ModCreatures.Spec spec : ModCreatures.MANIFEST) {
            CreatureDefinition def = CreatureDefinitionRegistry.get(
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, spec.id()));
            boolean ok = def != null;
            boolean alpha = AlphaRoster.isAlpha(spec.id());
            ChatFormatting colour = !ok ? ChatFormatting.RED
                    : alpha ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY;
            src.sendSuccess(() -> Component.literal((ok ? "  [ok] " : "  [--] ") + spec.id()
                    + " [" + spec.loco() + "]" + (alpha ? " (alpha)" : " (placeholder rig)"))
                    .withStyle(colour), false);
        }
        src.sendSuccess(() -> Component.literal("Bespoke entity classes: "
                + String.join(", ", ModCreatures.BESPOKE_IDS.stream().sorted().toList()))
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }
}
