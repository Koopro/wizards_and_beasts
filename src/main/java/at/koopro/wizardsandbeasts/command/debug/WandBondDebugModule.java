package at.koopro.wizardsandbeasts.command.debug;

import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.util.WandHelper;
import at.koopro.wizardsandbeasts.wand.WandAttachments;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries;
import at.koopro.wizardsandbeasts.wand.resonance.WandResonanceConfig;
import at.koopro.wizardsandbeasts.wand.resonance.WandResonanceConfigLoader;
import at.koopro.wizardsandbeasts.wand.resonance.WandResonanceSystem;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

public final class WandBondDebugModule implements DebugModule {
    @Override
    public String name() {
        return "wand";
    }

    @Override
    public String summary() {
        return "Wand item bond, resonance, force/clear (sub: force, clear, clear_cache).";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal(name())
                .executes(ctx -> inspect(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("force")
                        .executes(ctx -> forceBond(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> forceBond(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))))
                .then(Commands.literal("clear")
                        .executes(ctx -> clearBond(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> clearBond(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))))
                .then(Commands.literal("clear_cache")
                        .executes(ctx -> clearResonanceCache(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> clearResonanceCache(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> inspect(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"))));
    }

    private static InteractionHand wandHand(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof WandItem) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().getItem() instanceof WandItem) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private static float effectiveRefuseFloor(RegistryAccess reg, Identifier woodKey, WandResonanceConfig cfg) {
        float refuseFloor = cfg.refuseThreshold();
        var woodOpt = reg.lookupOrThrow(WandDatapackRegistries.WAND_WOOD_REGISTRY).get(woodKey);
        if (woodOpt.isPresent()) {
            refuseFloor = Math.max(refuseFloor, woodOpt.get().value().refuseThreshold());
        }
        return refuseFloor;
    }

    private int inspect(CommandSourceStack source, ServerPlayer target) {
        DebugOutput out = new DebugOutput(source);
        InteractionHand hand = wandHand(target);
        if (hand == null) {
            out.warn(target.getName().getString() + " is not holding a wand.");
            return 0;
        }
        ItemStack stack = target.getItemInHand(hand);
        RegistryAccess reg = target.level().registryAccess();
        WandResonanceConfig cfg = WandResonanceConfigLoader.getConfig(reg);

        out.header("Wand bond debug :: " + target.getName().getString());
        out.kv("Hand", hand == InteractionHand.MAIN_HAND ? "main" : "off");

        Identifier wood = WandComponents.getWood(stack);
        Identifier core = WandComponents.getCore(stack);
        out.kv("Wood", wood == null ? "(none)" : wood.toString());
        out.kv("Core", core == null ? "(none)" : core.toString());
        out.kv("Flexibility", WandComponents.getFlexibility(stack) == null ? "(none)" : WandComponents.getFlexibility(stack).getSerializedName());
        Float len = WandComponents.getLength(stack);
        out.kv("Length (in)", len == null ? "(none)" : String.format("%.2f", len));

        Optional<UUID> master = WandComponents.getMaster(stack);
        out.kv("Bonded master", master.map(UUID::toString).orElse("(none)"));
        out.kv("Bonded to this player", String.valueOf(WandHelper.isWandBondedTo(target, stack)));
        out.kv("Allegiance score", String.format("%.3f", WandComponents.getAllegianceScore(stack)));
        out.kv("Corruption", String.format("%.3f", WandComponents.getCorruption(stack)));
        out.kv("Integrity", String.format("%.3f", WandComponents.getIntegrity(stack)));

        target.getData(WandAttachments.BONDED_WAND.get())
                .ifPresentOrElse(
                        r -> out.kv("Player bonded_wand record", r.woodKey() + " | " + r.coreKey()),
                        () -> out.kv("Player bonded_wand record", "(none)"));

        float score = WandResonanceSystem.computeResonance(target, stack, reg);
        float refuseFloor = wood == null ? cfg.refuseThreshold() : effectiveRefuseFloor(reg, wood, cfg);
        out.kv("Computed resonance", String.format("%.4f", score));
        out.kv("Config match >=", String.format("%.4f", cfg.matchThreshold()));
        out.kv("Effective refuse floor", String.format("%.4f", refuseFloor));

        String tier;
        if (score >= cfg.matchThreshold()) {
            tier = "would MATCH (bond on next use attempt)";
        } else if (score >= refuseFloor) {
            tier = "would MISMATCH (no bond)";
        } else {
            tier = "would REFUSE (damage + no bond)";
        }
        out.kv("Resonance tier (if unbonded)", tier);

        int cacheSize = target.getData(WandAttachments.WAND_RESONANCE_CACHE.get()).size();
        out.kv("Resonance cache entries", cacheSize);
        return 1;
    }

    private int forceBond(CommandSourceStack source, ServerPlayer target) {
        DebugOutput out = new DebugOutput(source);
        InteractionHand hand = wandHand(target);
        if (hand == null) {
            out.warn("Hold a wand in main or off hand.");
            return 0;
        }
        ItemStack cur = target.getItemInHand(hand);
        Identifier wood = WandComponents.getWood(cur);
        Identifier core = WandComponents.getCore(cur);
        var flex = WandComponents.getFlexibility(cur);
        ItemStack next = cur.copy();
        next.set(WandComponents.WAND_MASTER.get(), Optional.of(target.getUUID()));
        next.set(WandComponents.WAND_ALLEGIANCE_SCORE.get(), 1.0f);
        if (wood != null && core != null && flex != null) {
            target.setData(WandAttachments.BONDED_WAND.get(), Optional.of(new WandAttachments.BondedWandRecord(
                    wood, core, flex, 1.0f)));
        } else {
            target.setData(WandAttachments.BONDED_WAND.get(), Optional.empty());
        }
        target.setItemInHand(hand, next);
        out.ok("Force-bonded " + target.getName().getString() + "'s held wand.");
        return 1;
    }

    private int clearBond(CommandSourceStack source, ServerPlayer target) {
        DebugOutput out = new DebugOutput(source);
        InteractionHand hand = wandHand(target);
        if (hand == null) {
            out.warn("Hold a wand in main or off hand.");
            return 0;
        }
        ItemStack cur = target.getItemInHand(hand);
        ItemStack next = cur.copy();
        next.set(WandComponents.WAND_MASTER.get(), Optional.empty());
        target.setData(WandAttachments.BONDED_WAND.get(), Optional.empty());
        target.setItemInHand(hand, next);
        out.ok("Cleared wand master + player bonded_wand record for " + target.getName().getString() + ".");
        return 1;
    }

    private int clearResonanceCache(CommandSourceStack source, ServerPlayer target) {
        target.setData(WandAttachments.WAND_RESONANCE_CACHE.get(), new HashMap<>());
        new DebugOutput(source).ok("Cleared resonance score cache for " + target.getName().getString() + ".");
        return 1;
    }
}
