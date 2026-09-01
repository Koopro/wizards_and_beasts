package at.koopro.wizardsandbeasts.item.trinket;

import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.sneakoscope.SneakoscopeDetector;
import at.koopro.wizardsandbeasts.sneakoscope.SneakoscopeFocus;
import at.koopro.wizardsandbeasts.sneakoscope.SneakoscopeReading;
import at.koopro.wizardsandbeasts.sneakoscope.SneakoscopeTier;
import at.koopro.wizardsandbeasts.sneakoscope.SneakoscopeTuning;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A Dark Detector: a glass top that will not sit still in the company of people who are lying.
 *
 * <p><b>What it is not.</b> It is not a wearable Potion of Night Vision for invisible players. It
 * never reveals anybody, never names anybody, and outside focused mode it will not even tell you
 * which way to look — it tells you <em>how bad it is</em>, by spinning, and leaves the looking to
 * you. What trips it is deceit rather than concealment: see {@link SneakoscopeDetector} for the
 * list, which includes people standing in plain sight holding a wand that is not theirs.
 *
 * <h2>How it runs</h2>
 * The scan is a server-side {@link #inventoryTick} on the held stack only, once every
 * {@link SneakoscopeTuning#BASE_SCAN_INTERVAL_TICKS} ticks, and it writes its result to two data
 * components on the stack. Those components are the <em>only</em> channel to the client: because a
 * held stack already replicates, the spinning model, the orbiting motes, the alarm tint and the
 * tooltip all come for free with no payload, no protocol change and nothing to keep in sync. The
 * write is skipped whenever the value has not changed, so a quiet Sneakoscope costs one AABB sweep
 * every eight ticks and zero packets.
 *
 * <h2>Focused mode</h2>
 * Right-click halves the reach and doubles the scan rate, and starts publishing a bearing to the
 * nearest suspect, rounded to one of sixteen sectors — the arrow is an approximation on purpose.
 * There is no mana cost; it is a passive artefact, and the price is paid on the way out, as a
 * {@link SneakoscopeTuning#FOCUS_COOLDOWN_TICKS}-tick cooldown once focus is released. That is what
 * stops focused mode being simply strictly better than idle: you cannot flick it on for one reading
 * and off again without losing the item for two seconds.
 */
@NullMarked
public class SneakoscopeItem extends Item {

    public SneakoscopeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (!(entity instanceof ServerPlayer holder)) {
            return;
        }
        // Held in a hand, or nothing at all: MAINHAND arrives from Inventory#tick for the selected
        // slot and OFFHAND from EntityEquipment#tick; every other carried slot ticks with null.
        boolean held = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
        if (!held || !ModuleManager.isEnabled(Module.ARTEFACTS)) {
            goQuiet(stack);
            return;
        }

        SneakoscopeFocus focus = focusOf(stack);
        int interval = SneakoscopeTuning.scanIntervalTicks(focus.active());
        if (level.getGameTime() % interval != 0L) {
            return;
        }

        SneakoscopeReading reading = SneakoscopeDetector.scan(holder, SneakoscopeTuning.scanRadius(focus.active()));
        publish(stack, reading, focus);
        whirr(level, holder, reading.tier());
    }

    /**
     * Right-click toggles focused mode.
     *
     * <p>Client-side this returns {@code SUCCESS} without touching anything: the toggle is a server
     * decision — it may be refused by the cooldown — and letting the client predict it would show a
     * focus ring that is about to be taken away again.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.ARTEFACTS)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        SneakoscopeFocus focus = focusOf(stack);

        if (!focus.active() && player.getCooldowns().isOnCooldown(stack)) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("item.wizards_and_beasts.sneakoscope.settling")
                            .withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }

        boolean nowFocused = !focus.active();
        stack.set(ModDataComponents.SNEAKOSCOPE_FOCUS.get(),
                new SneakoscopeFocus(nowFocused, SneakoscopeTuning.NO_BEARING));

        if (!nowFocused) {
            // The whole cost of the item, charged on release rather than on use so that focusing is
            // a commitment instead of a free peek.
            player.getCooldowns().addCooldown(stack, SneakoscopeTuning.FOCUS_COOLDOWN_TICKS);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.SNEAKOSCOPE_FOCUS.get(), SoundSource.PLAYERS, 0.5f, nowFocused ? 1.4f : 0.8f);
        PlayerFeedback.actionBar(player, Component
                .translatable(nowFocused
                        ? "item.wizards_and_beasts.sneakoscope.focus_on"
                        : "item.wizards_and_beasts.sneakoscope.focus_off")
                .withStyle(nowFocused ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        int threats = threatCount(stack);
        SneakoscopeTier tier = SneakoscopeTuning.tier(threats);
        tooltipAdder.accept(Component
                .translatable("item.wizards_and_beasts.sneakoscope.reading", threats)
                .withStyle(tierColour(tier)));
        if (focusOf(stack).active()) {
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.sneakoscope.focused")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    /** A spinning Sneakoscope glitters; a resting one is just glass. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return threatCount(stack) >= SneakoscopeTuning.LOW_THRESHOLD;
    }

    /** Suspicious entities seen by the last scan, {@code 0} for a stack that has never scanned. */
    public static int threatCount(ItemStack stack) {
        Integer stored = stack.get(ModDataComponents.LAST_THREAT_COUNT.get());
        return stored == null ? 0 : Math.max(0, stored);
    }

    public static SneakoscopeFocus focusOf(ItemStack stack) {
        SneakoscopeFocus stored = stack.get(ModDataComponents.SNEAKOSCOPE_FOCUS.get());
        return stored == null ? SneakoscopeFocus.IDLE : stored;
    }

    /**
     * Writes the reading onto the stack, and only when something actually changed.
     *
     * <p>A component write on a held stack is a slot resync to the holder and an equipment resync to
     * everyone tracking them. Doing that unconditionally would put a packet on the wire eight times
     * a second for an item whose normal state is "nothing is happening".
     */
    private static void publish(ItemStack stack, SneakoscopeReading reading, SneakoscopeFocus focus) {
        if (threatCount(stack) != reading.threats()) {
            if (reading.threats() == 0) {
                stack.remove(ModDataComponents.LAST_THREAT_COUNT.get());
            } else {
                stack.set(ModDataComponents.LAST_THREAT_COUNT.get(), reading.threats());
            }
        }
        // The bearing is only meaningful — and only worth sending — while focused.
        int bearing = focus.active() ? reading.bearing() : SneakoscopeTuning.NO_BEARING;
        if (focus.bearing() != bearing) {
            stack.set(ModDataComponents.SNEAKOSCOPE_FOCUS.get(), focus.withBearing(bearing));
        }
    }

    /** Drops a stashed or module-disabled Sneakoscope back to rest without a needless write. */
    private static void goQuiet(ItemStack stack) {
        if (stack.has(ModDataComponents.LAST_THREAT_COUNT.get())) {
            stack.remove(ModDataComponents.LAST_THREAT_COUNT.get());
        }
        if (stack.has(ModDataComponents.SNEAKOSCOPE_FOCUS.get())) {
            stack.remove(ModDataComponents.SNEAKOSCOPE_FOCUS.get());
        }
    }

    /**
     * The whirr, at the cadence and pitch the tier calls for.
     *
     * <p>Played into the world rather than to the holder: a Sneakoscope going off in your pocket is
     * audible to the people it is going off about, which is a cost worth keeping — it is why you
     * hide one, and why Moody's was worth silencing.
     */
    private static void whirr(ServerLevel level, ServerPlayer holder, SneakoscopeTier tier) {
        int interval = SneakoscopeTuning.whirrIntervalTicks(tier);
        if (interval <= 0 || level.getGameTime() % interval != 0L) {
            return;
        }
        level.playSound(null, holder.getX(), holder.getY(), holder.getZ(),
                whirrSound(tier), SoundSource.PLAYERS,
                SneakoscopeTuning.whirrVolume(tier), SneakoscopeTuning.whirrPitch(tier));
    }

    private static net.minecraft.sounds.SoundEvent whirrSound(SneakoscopeTier tier) {
        return switch (tier) {
            case HIGH -> ModSounds.SNEAKOSCOPE_SHRIEK.get();
            case MEDIUM -> ModSounds.SNEAKOSCOPE_WHIRR.get();
            default -> ModSounds.SNEAKOSCOPE_SPIN.get();
        };
    }

    private static ChatFormatting tierColour(SneakoscopeTier tier) {
        return switch (tier) {
            case HIGH -> ChatFormatting.RED;
            case MEDIUM -> ChatFormatting.GOLD;
            case LOW -> ChatFormatting.YELLOW;
            case CALM -> ChatFormatting.DARK_GRAY;
        };
    }
}
