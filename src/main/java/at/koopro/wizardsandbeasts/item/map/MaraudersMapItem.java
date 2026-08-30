package at.koopro.wizardsandbeasts.item.map;

import at.koopro.wizardsandbeasts.item.GeoItemRenderers;
import at.koopro.wizardsandbeasts.item.GeoItemBase;
import at.koopro.wizardsandbeasts.map.MapAtlas;
import at.koopro.wizardsandbeasts.map.MapSession;
import at.koopro.wizardsandbeasts.map.MapSessions;
import at.koopro.wizardsandbeasts.map.MaraudersMapAtlasStore;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.map.MapOpenS2CPayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The artefact itself.
 *
 * <p>The stack carries almost nothing: a {@code MapId}, the people the map will draw for, where it
 * was made, and which insult it is up to. Everything the map <em>knows</em> — every tile it has been
 * carried across and every place it has learned the name of — lives in
 * {@link MaraudersMapAtlasStore} under that id. That split is what lets the map remember a
 * continent without re-serialising a continent every time the holder picks up a cobblestone.
 *
 * <p>It also makes the map a genuinely shared object, which is the canon reading: hand it to
 * someone on the trusted list and they open the same parchment, already charted.
 */
public class MaraudersMapItem extends GeoItemBase {

    private static final String TAG_MAP_ID = "MapId";
    private static final String TAG_BOUND_X = "BoundX";
    private static final String TAG_BOUND_Z = "BoundZ";
    private static final String TAG_BOUND_DIMENSION = "BoundDimension";
    private static final String TAG_TRUSTED = "TrustedPlayers";
    private static final String TAG_INSULT = "InsultIndex";

    /** The controller both the open and the close path trigger on. */
    public static final String ANIM_CONTROLLER = "map_controller";
    public static final String TRIGGER_UNFOLD = "unfold";
    public static final String TRIGGER_FOLD = "fold";

    private static final RawAnimation FOLDED_IDLE =
            RawAnimation.begin().thenLoop("animation.marauders_map.folded_idle");

    /**
     * Opening chains straight into the open idle, and that chain is the whole fix.
     *
     * <p>The trigger used to name the unfold clip alone. A triggered animation hands control
     * back to the controller's default handler when it finishes, and the default is
     * {@link #FOLDED_IDLE} -- so the map completed its half-second unfold and then snapped
     * shut in the same breath. Every held state has to name the loop it settles into.
     */
    private static final RawAnimation UNFOLDING = RawAnimation.begin()
            .thenPlay("animation.marauders_map.unfolding")
            .thenLoop("animation.marauders_map.open_idle");

    private static final RawAnimation FOLDING = RawAnimation.begin()
            .thenPlay("animation.marauders_map.folding")
            .thenLoop("animation.marauders_map.folded_idle");

    private static final List<String> INSULTS = List.of(
            "Mr. Moony presents his compliments to %s, and begs them to keep their abnormally large nose out of other people's business.",
            "Mr. Prongs agrees with Mr. Moony, and would like to add that %s is an ugly git.",
            "Mr. Padfoot would like to register his astonishment that an idiot like %s ever became a player of note.",
            "Mr. Wormtail bids %s good day, and advises them to wash their hair, the slimeball."
    );

    public MaraudersMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            if (!ModuleManager.isEnabled(Module.ARTEFACTS)) {
                return InteractionResult.PASS;
            }
            initializeIfNeeded(stack, player, serverLevel);

            if (!isTrusted(stack, player.getUUID())) {
                sendInsult(stack, player);
                return InteractionResult.SUCCESS;
            }

            UUID mapId = mapId(stack);
            if (mapId == null) {
                return InteractionResult.SUCCESS; // initialise failed; nothing sensible to open
            }

            MapAtlas atlas = MaraudersMapAtlasStore.get(serverLevel).atlas(mapId);
            MapSession session = MapSessions.open(serverPlayer, mapId);
            // Everything already charted in this dimension is queued at open. The client draws
            // what has arrived and fills in as the rest streams, which is why the screen does not
            // wait on a single large packet before it will show anything.
            session.queueRegions(atlas.regionKeys(session.dimension()));

            PacketDistributor.sendToPlayer(serverPlayer, new MapOpenS2CPayload(
                    mapId,
                    session.dimension(),
                    serverPlayer.getBlockX(),
                    serverPlayer.getBlockZ(),
                    MapSession.SENSE_RADIUS,
                    atlas.palette()));

            unfold(serverPlayer, stack, serverLevel);
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * Plays the map open in the holder's hand, and makes the sound of it.
     *
     * <p>Server-side: {@code triggerAnim} is GeckoLib's synced trigger, so calling it here is
     * what makes other players see the map open in someone else's hand rather than only the
     * holder seeing it in their own. It is an instance method on {@code GeoItem}, hence the
     * cast -- which doubles as the guard for a caller that handed us the wrong stack.
     */
    public static void unfold(ServerPlayer player, ItemStack stack, ServerLevel level) {
        if (!(stack.getItem() instanceof MaraudersMapItem map)) {
            return;
        }
        map.triggerAnim(player, GeoItem.getOrAssignId(stack, level), ANIM_CONTROLLER, TRIGGER_UNFOLD);
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS, 0.7F, 0.8F);
    }

    /**
     * Folds it away again. Called when the screen closes, which is the only moment the fold
     * is actually watched -- while the map is open the screen is covering the hand holding it.
     */
    public static void fold(ServerPlayer player, ItemStack stack, ServerLevel level) {
        if (!(stack.getItem() instanceof MaraudersMapItem map)) {
            return;
        }
        map.triggerAnim(player, GeoItem.getOrAssignId(stack, level), ANIM_CONTROLLER, TRIGGER_FOLD);
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS, 0.6F, 0.6F);
    }

    /**
     * Stamps a fresh map with an id, its birthplace and its first trusted name.
     *
     * <p>Keyed on {@code MapId} rather than on {@code BoundX} so that a map saved by an older build
     * — which has the bound position but no id — is adopted rather than left permanently blank.
     */
    private void initializeIfNeeded(ItemStack stack, Player player, ServerLevel level) {
        CompoundTag tag = getCustomTag(stack);
        if (tag.read(TAG_MAP_ID, UUIDUtil.CODEC).isPresent()) {
            return;
        }
        tag.store(TAG_MAP_ID, UUIDUtil.CODEC, UUID.randomUUID());

        if (tag.getInt(TAG_BOUND_X).isEmpty()) {
            tag.putInt(TAG_BOUND_X, player.blockPosition().getX());
            tag.putInt(TAG_BOUND_Z, player.blockPosition().getZ());
            tag.putString(TAG_BOUND_DIMENSION, level.dimension().identifier().toString());
        }
        if (tag.getList(TAG_TRUSTED).isEmpty()) {
            ListTag trusted = new ListTag();
            trusted.add(StringTag.valueOf(player.getUUID().toString()));
            tag.put(TAG_TRUSTED, trusted);
            tag.putInt(TAG_INSULT, 0);
        }
        setCustomTag(stack, tag);
    }

    /** The map's id, or {@code null} if it has never been unfolded. */
    public static @Nullable UUID mapId(ItemStack stack) {
        return getCustomTag(stack).read(TAG_MAP_ID, UUIDUtil.CODEC).orElse(null);
    }

    /**
     * The first Marauder's Map in the player's inventory, or {@link ItemStack#EMPTY}.
     *
     * <p>Anywhere in the pack, not only in hand. Surveying only while the map is held would mean
     * the map charts a corridor exactly as wide as the moments a player happened to be looking at
     * it, which is not how carrying a map works.
     */
    public static ItemStack findCarried(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof MaraudersMapItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Ids of every activated map the player is carrying — a death marks all of them. */
    public static List<UUID> carriedMapIds(Player player) {
        List<UUID> ids = new ArrayList<>(1);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof MaraudersMapItem)) {
                continue;
            }
            UUID id = mapId(stack);
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    public static boolean isTrusted(ItemStack stack, UUID playerUuid) {
        CompoundTag tag = getCustomTag(stack);
        ListTag trusted = tag.getList(TAG_TRUSTED).orElse(new ListTag());
        String uuidStr = playerUuid.toString();
        for (int i = 0; i < trusted.size(); i++) {
            if (trusted.getString(i).orElse("").equals(uuidStr)) {
                return true;
            }
        }
        return false;
    }

    private void sendInsult(ItemStack stack, Player player) {
        CompoundTag tag = getCustomTag(stack);
        int index = tag.getInt(TAG_INSULT).orElse(0);
        String insult = String.format(
                INSULTS.get(index % INSULTS.size()),
                player.getName().getString());
        at.koopro.wizardsandbeasts.feedback.PlayerFeedback.toast(player,
                at.koopro.wizardsandbeasts.feedback.NoticeKind.FAIL,
                Component.translatable("item.wizards_and_beasts.marauders_map.speaks"),
                Component.literal(insult));
        tag.putInt(TAG_INSULT, (index + 1) % INSULTS.size());
        setCustomTag(stack, tag);
    }

    private static CompoundTag getCustomTag(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.copyTag();
    }

    private static void setCustomTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // -- GeckoLib --

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(GeoItemRenderers.lazy("at.koopro.wizardsandbeasts.client.map.MaraudersMapRenderer"));
    }

    /**
     * One controller, four clips, and a default of "closed".
     *
     * <p>The transition length is 3 rather than 5 ticks. Blending is what stops a triggered
     * clip from popping, but the fold clips are short and deliberate, and a quarter-second
     * cross-fade over a 0.6-second fold smears the two ends of it into each other.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<MaraudersMapItem>(
                ANIM_CONTROLLER, 3,
                state -> state.setAndContinue(FOLDED_IDLE))
                .triggerableAnim(TRIGGER_UNFOLD, UNFOLDING)
                .triggerableAnim(TRIGGER_FOLD, FOLDING));
    }

}
