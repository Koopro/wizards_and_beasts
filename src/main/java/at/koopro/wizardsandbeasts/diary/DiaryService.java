package at.koopro.wizardsandbeasts.diary;

import at.koopro.wizardsandbeasts.item.darkartefact.RiddlesDiaryItem;
import at.koopro.wizardsandbeasts.network.trinket.DiaryReplyS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-side logic for Tom Riddle's Diary: writing into it draws a reply, deepens the writer's dark
 * corruption, and — once corruption runs high enough — lets the soul fragment seize control.
 */
public final class DiaryService {

    /** Dark corruption gained per line written. */
    private static final float WRITE_GAIN = 9.0f;
    /** Corruption at/above which the next reply becomes a possession takeover. */
    private static final float POSSESS_THRESHOLD = 85.0f;
    /** How long (ticks) a possession holds the writer. */
    public static final int POSSESSION_TICKS = 160;

    private static final String[][] LINES = {
            { // tier 0 — curious
                "Hello. My name is Tom Riddle. How did you come by my diary?",
                "It is a little frightening, isn't it, a book that writes back?",
                "I can show you things no one else will. Keep writing." },
            { // tier 1 — probing
                "You can trust me. Tell me what troubles you.",
                "No one understands you as I do. Go on.",
                "We could be great friends, you and I." },
            { // tier 2 — manipulative
                "We are more alike than you know. Let me show you.",
                "Give me a little more of yourself. It is nothing.",
                "Your hand trembles. Mine is steady. Lean on me." },
            { // tier 3 — menacing
                "Yes... I can feel you now. Let me out.",
                "Give me your hand. It will be over soon.",
                "You opened the door. You cannot close it." },
    };

    private DiaryService() {}

    public static boolean isSoulIntact(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SOUL_FRAGMENT_INTACT.get(), true);
    }

    /** Right-click open: bind the diary to the writer if unbound. Returns false if the soul is destroyed. */
    public static boolean tryOpen(ServerPlayer player, ItemStack stack) {
        if (!isSoulIntact(stack)) {
            player.displayClientMessage(Component.literal("The diary is ruined — no voice answers.")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return false;
        }
        Optional<UUID> bound = stack.getOrDefault(ModDataComponents.DIARY_POSSESSING.get(), Optional.empty());
        if (bound.isEmpty()) {
            stack.set(ModDataComponents.DIARY_POSSESSING.get(), Optional.of(player.getUUID()));
        }
        return true;
    }

    /** A line was written. Picks a reply, deepens corruption, and may trigger possession. */
    public static void onWrite(ServerPlayer player, String text) {
        ItemStack diary = findDiary(player);
        if (diary.isEmpty() || !isSoulIntact(diary)) {
            return;
        }
        diary.set(ModDataComponents.DIARY_POSSESSING.get(), Optional.of(player.getUUID()));

        float corruption = Math.min(100f, player.getData(ModAttachments.DARK_CORRUPTION.get()) + WRITE_GAIN);
        player.setData(ModAttachments.DARK_CORRUPTION.get(), corruption);

        int tier = tierFor(corruption);
        boolean possess = corruption >= POSSESS_THRESHOLD;
        String line = LINES[tier][player.getRandom().nextInt(LINES[tier].length)];

        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new DiaryReplyS2CPayload(line, tier, possess));

        if (possess) {
            beginPossession(player);
        }
    }

    private static void beginPossession(ServerPlayer player) {
        int d = POSSESSION_TICKS;
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, d, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, d, 1, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, d, 2, false, false, true));
        player.hurt(player.damageSources().magic(), 2.0f);
    }

    private static int tierFor(float corruption) {
        if (corruption >= POSSESS_THRESHOLD) return 3;
        if (corruption >= 60f) return 2;
        if (corruption >= 30f) return 1;
        return 0;
    }

    private static ItemStack findDiary(ServerPlayer player) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (main.getItem() instanceof RiddlesDiaryItem) {
            return main;
        }
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        if (off.getItem() instanceof RiddlesDiaryItem) {
            return off;
        }
        return ItemStack.EMPTY;
    }
}
